package com.linkshare.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import com.linkshare.app.tunnel.IpPacketRouter
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LinkShareVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null
    private var transport: EncryptedDatagramTunnel? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newFixedThreadPool(3)
    private var scheduler: ScheduledExecutorService? = null
    private val router = IpPacketRouter()

    private val bytesUp = AtomicLong(0)
    private val bytesDown = AtomicLong(0)
    private val lastPongReceivedAt = AtomicLong(0)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra(EXTRA_PEER_HOST)
        val port = intent?.getIntExtra(EXTRA_PEER_PORT, -1) ?: -1
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val role = intent?.getStringExtra(EXTRA_ROLE)
        val sessionKey = intent?.getByteArrayExtra(EXTRA_SESSION_KEY)

        if (host.isNullOrBlank() || port !in 1..65535 || sessionId.isNullOrBlank() || role != ROLE_RECEIVER || sessionKey?.size != 32) {
            Log.e(TAG, "Invalid VPN startup arguments")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (VpnService.prepare(this) != null) {
            Log.e(TAG, "VPN permission not granted by user")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        stopTunnel()
        acquireLocks()

        tunnelInterface = Builder()
            .setSession("LINKO Tunnel")
            .setMtu(TUN_MTU)
            .addAddress("10.48.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setBlocking(true)
            .establish()
            ?: run {
                Log.e(TAG, "Failed to establish Android VPN interface")
                releaseLocks()
                return START_NOT_STICKY
            }

        val socket = DatagramSocket()
        if (!protect(socket)) {
            Log.e(TAG, "Failed to protect tunnel socket from VPN routing loop")
            socket.close()
            stopTunnel()
            return START_NOT_STICKY
        }

        transport = EncryptedDatagramTunnel(
            socket = socket,
            peer = InetSocketAddress(host, port),
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.RECEIVER,
            sessionKey = sessionKey
        )

        running.set(true)
        lastPongReceivedAt.set(System.currentTimeMillis())

        executor.execute { outboundLoop() }
        executor.execute { inboundLoop() }

        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleWithFixedDelay({
            if (running.get()) {
                transport?.sendPing()
                val silentMs = System.currentTimeMillis() - lastPongReceivedAt.get()
                if (silentMs > 20_000L) {
                    Log.w(TAG, "Tunnel peer silent for ${silentMs}ms, sending high-priority keepalive")
                    transport?.sendPing()
                }
            }
        }, 3, 5, TimeUnit.SECONDS)

        Log.i(TAG, "LINKO VPN service started successfully for session=$sessionId to $host:$port")
        return START_STICKY
    }

    private fun acquireLocks() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LINKO:VpnWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L)
            }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LINKO:VpnWifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        }
    }

    private fun outboundLoop() {
        val descriptor = tunnelInterface ?: return
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val packet = ByteArray(MAX_IP_PACKET)
                while (running.get()) {
                    val count = input.read(packet)
                    if (count <= 0) break

                    val raw = packet.copyOf(count)
                    if (router.parse(raw) == null || raw.size > MAX_TUN_PAYLOAD) continue

                    transport?.send(raw, EncryptedDatagramTunnel.PacketType.DATA)
                    bytesUp.addAndGet(raw.size.toLong())
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                Log.w(TAG, "VPN outbound loop terminated: ${e.message}")
                stopTunnel()
            }
        }
    }

    private fun inboundLoop() {
        val descriptor = tunnelInterface ?: return
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                while (running.get()) {
                    try {
                        val rx = transport?.receive(RECEIVE_TIMEOUT_MS) ?: continue
                        when (rx.type) {
                            EncryptedDatagramTunnel.PacketType.DATA -> {
                                val packet = rx.payload
                                if (packet.isNotEmpty() && packet.size <= MAX_IP_PACKET && router.parse(packet) != null) {
                                    output.write(packet)
                                    output.flush()
                                    bytesDown.addAndGet(packet.size.toLong())
                                }
                            }
                            EncryptedDatagramTunnel.PacketType.PONG -> {
                                val sentAt = if (rx.payload.size >= 8) {
                                    ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long
                                } else 0L
                                val rtt = System.currentTimeMillis() - sentAt
                                lastPongReceivedAt.set(System.currentTimeMillis())
                                Log.d(TAG, "Tunnel keepalive RTT: ${rtt}ms")
                            }
                            EncryptedDatagramTunnel.PacketType.PING -> {
                                val sentAt = if (rx.payload.size >= 8) {
                                    ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long
                                } else System.currentTimeMillis()
                                transport?.sendPong(sentAt)
                            }
                            EncryptedDatagramTunnel.PacketType.CLOSE -> {
                                Log.i(TAG, "Received remote CLOSE from provider")
                                stopTunnel()
                                break
                            }
                            else -> Unit
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Poll again
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                Log.w(TAG, "VPN inbound loop terminated: ${e.message}")
                stopTunnel()
            }
        }
    }

    private fun stopTunnel() {
        if (!running.getAndSet(false)) return
        releaseLocks()
        scheduler?.shutdownNow()
        scheduler = null
        transport?.sendClose()
        transport?.close()
        transport = null
        runCatching { tunnelInterface?.close() }
        tunnelInterface = null
        Log.i(TAG, "LINKO VPN tunnel stopped. Total uploaded: ${bytesUp.get()} bytes, downloaded: ${bytesDown.get()} bytes")
    }

    override fun onDestroy() {
        stopTunnel()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LINKO_VPN_SERVICE"
        const val EXTRA_PEER_HOST = "linko.peer.host"
        const val EXTRA_PEER_PORT = "linko.peer.port"
        const val EXTRA_SESSION_ID = "linko.session.id"
        const val EXTRA_ROLE = "linko.role"
        const val EXTRA_SESSION_KEY = "linko.session.key"
        const val ROLE_RECEIVER = "receiver"
        private const val RECEIVE_TIMEOUT_MS = 500
        private const val MAX_IP_PACKET = 64 * 1024
        private const val MAX_TUN_PAYLOAD = 32 * 1024
        private const val TUN_MTU = 1500
    }
}
