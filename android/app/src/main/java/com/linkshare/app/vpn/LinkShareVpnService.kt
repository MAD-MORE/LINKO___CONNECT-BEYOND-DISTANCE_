package com.linkshare.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import com.linkshare.app.MainActivity
import com.linkshare.app.R
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.DirectP2pNegotiator
import com.linkshare.app.network.LinkoDeviceControlApi
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoNetworkException
import com.linkshare.app.network.LinkoSignalingClient
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import com.linkshare.app.tunnel.IpPacketRouter
import kotlinx.coroutines.runBlocking
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Receiver VPN. Supabase is signaling only; the data path is direct authenticated UDP. */
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
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForeground(NOTIFICATION_ID, serviceNotification("LINKO", "Preparing direct encrypted tunnel"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val role = intent?.getStringExtra(EXTRA_ROLE)
        val sessionKey = intent?.getByteArrayExtra(EXTRA_SESSION_KEY)
        if (sessionId.isNullOrBlank() || role != ROLE_RECEIVER || sessionKey?.size != 32) {
            Log.e(TAG, "Invalid direct VPN startup arguments")
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
        val allowedPackages = intent?.getStringArrayListExtra(EXTRA_ALLOWED_PACKAGES) ?: emptyList<String>()
        val builder = Builder()
            .setSession("LINKO Direct Tunnel")
            .setMtu(TUN_MTU)
            .addAddress("10.48.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setBlocking(true)
        for (pkg in allowedPackages) runCatching { builder.addAllowedApplication(pkg) }
        tunnelInterface = builder.establish() ?: run {
            Log.e(TAG, "Failed to establish Android VPN interface")
            releaseLocks(); stopSelf(startId); return START_NOT_STICKY
        }

        val socket = runCatching { DatagramSocket(0) }.getOrElse {
            Log.e(TAG, "Could not create direct UDP socket", it)
            stopTunnel(); stopSelf(startId); return START_NOT_STICKY
        }
        if (!protect(socket)) {
            Log.e(TAG, "Failed to protect direct UDP socket from VPN routing loop")
            socket.close(); stopTunnel(); stopSelf(startId); return START_NOT_STICKY
        }

        running.set(false)
        LinkoEngineBridge.reportTunnelState("direct_connecting", "Finding a direct peer path")
        executor.execute { establishDirectTransport(sessionId, sessionKey, socket) }
        return START_STICKY
    }

    private fun establishDirectTransport(sessionId: String, sessionKey: ByteArray, socket: DatagramSocket) {
        try {
            val auth = LinkoAuth(applicationContext)
            val token = auth.currentAccessToken()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_auth_required")
            val result = runBlocking {
                DirectP2pNegotiator.establish(
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                    role = EncryptedDatagramTunnel.Role.RECEIVER,
                    signaling = LinkoSignalingClient(accessToken = token),
                    socket = socket,
                )
            }
            transport = EncryptedDatagramTunnel(socket = result.socket, peer = result.peer, sessionId = sessionId, role = EncryptedDatagramTunnel.Role.RECEIVER, sessionKey = sessionKey)
            running.set(true)
            lastPongReceivedAt.set(System.currentTimeMillis())
            runCatching {
                runBlocking {
                    LinkoDeviceControlApi(applicationContext).transition(sessionId, "connected")
                }
            }.onFailure { Log.w(TAG, "Connected tunnel established but session-state update failed: ${it.message}") }
            updateForegroundNotification("Connected", "Direct encrypted LINKO tunnel is active")
            LinkoEngineBridge.reportTunnelState("direct_established", "Secure direct connection established")
            executor.execute { outboundLoop() }
            executor.execute { inboundLoop() }
            registerNetworkHandoverCallback(result.socket)
            scheduler = Executors.newSingleThreadScheduledExecutor()
            scheduler?.scheduleWithFixedDelay({
                if (running.get()) {
                    runCatching { transport?.sendPing() }
                    val silentMs = System.currentTimeMillis() - lastPongReceivedAt.get()
                    if (silentMs > 45_000L) {
                        Log.w(TAG, "Direct peer heartbeat expired after ${silentMs}ms")
                        LinkoEngineBridge.reportTunnelState("failed", "Direct peer became unreachable (NAT/network timeout)")
                        stopTunnel()
                    }
                }
            }, 3, 15, TimeUnit.SECONDS)
            LinkoEngineBridge.reportTunnelState("connected", "Direct tunnel established; verifying Internet")
            Log.i(TAG, "LINKO direct P2P tunnel established for session=$sessionId to ${result.peer.hostString}:${result.peer.port}")
        } catch (e: Exception) {
            Log.e(TAG, "Direct P2P connection failed: ${e.message}", e)
            runCatching { socket.close() }
            LinkoEngineBridge.reportTunnelState("failed", e.message ?: "direct_connection_failed")
            stopTunnel()
        }
    }

    private fun registerNetworkHandoverCallback(socket: DatagramSocket) {
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { protect(socket); runCatching { transport?.sendPing() } }
                override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) { protect(socket) }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun acquireLocks() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LINKO:VpnWakeLock")?.apply { setReferenceCounted(false); acquire(24 * 60 * 60 * 1000L) }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LINKO:VpnWifiLock")?.apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null; if (wifiLock?.isHeld == true) wifiLock?.release(); wifiLock = null }
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
                    LinkoEngineBridge.updateTrafficStats(bytesDown.get(), bytesUp.get())
                }
            }
        } catch (e: Exception) { if (running.get()) { Log.w(TAG, "VPN outbound loop terminated: ${e.message}"); stopTunnel() } }
    }

    private fun inboundLoop() {
        val descriptor = tunnelInterface ?: return
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                while (running.get()) {
                    try {
                        when (val rx = transport?.receive(RECEIVE_TIMEOUT_MS)) {
                            null -> Unit
                            else -> when (rx.type) {
                                EncryptedDatagramTunnel.PacketType.DATA -> {
                                    val packet = rx.payload
                                    if (packet.isNotEmpty() && packet.size <= MAX_IP_PACKET && router.parse(packet) != null) {
                                        output.write(packet); output.flush(); bytesDown.addAndGet(packet.size.toLong()); LinkoEngineBridge.updateTrafficStats(bytesDown.get(), bytesUp.get())
                                    }
                                }
                                EncryptedDatagramTunnel.PacketType.PONG -> {
                                    val sentAt = if (rx.payload.size >= 8) ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long else 0L
                                    val rtt = System.currentTimeMillis() - sentAt
                                    lastPongReceivedAt.set(System.currentTimeMillis())
                                    LinkoEngineBridge.updateTrafficStats(bytesDown.get(), bytesUp.get(), rtt.toInt().coerceAtLeast(1))
                                }
                                EncryptedDatagramTunnel.PacketType.PING -> {
                                    val sentAt = if (rx.payload.size >= 8) ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long else System.currentTimeMillis()
                                    transport?.sendPong(sentAt)
                                }
                                EncryptedDatagramTunnel.PacketType.CLOSE -> { stopTunnel(); return }
                                else -> Unit
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) { }
                }
            }
        } catch (e: Exception) { if (running.get()) { Log.w(TAG, "VPN inbound loop terminated: ${e.message}"); stopTunnel() } }
    }

    private fun stopTunnel() {
        val wasRunning = running.getAndSet(false)
        releaseLocks()
        runCatching {
            networkCallback?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager)?.unregisterNetworkCallback(it) }
        }
        networkCallback = null
        scheduler?.shutdownNow(); scheduler = null
        runCatching { transport?.sendClose() }; runCatching { transport?.close() }; transport = null
        runCatching { tunnelInterface?.close() }; tunnelInterface = null
        updateForegroundNotification("LINKO", if (wasRunning) "Direct tunnel closed" else "Direct connection cancelled")
        LinkoEngineBridge.reportTunnelState("stopped", if (wasRunning) "Direct tunnel closed" else "Direct connection cancelled")
        Log.i(TAG, "LINKO VPN tunnel stopped. Uploaded=${bytesUp.get()} bytes, downloaded=${bytesDown.get()} bytes")
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL_ID, "LINKO VPN", NotificationManager.IMPORTANCE_LOW))
    }

    private fun serviceNotification(title: String, text: String): Notification {
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 9001, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).build()
    }

    private fun updateForegroundNotification(title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) runCatching { startForeground(NOTIFICATION_ID, serviceNotification(title, text)) }
    }

    override fun onRevoke() { stopTunnel(); super.onRevoke() }
    override fun onDestroy() { stopTunnel(); executor.shutdownNow(); super.onDestroy() }

    companion object {
        private const val TAG = "LINKO_VPN_SERVICE"
        private const val CHANNEL_ID = "linko_vpn"
        private const val NOTIFICATION_ID = 7002
        const val EXTRA_PEER_HOST = "linko.peer.host"
        const val EXTRA_PEER_PORT = "linko.peer.port"
        const val EXTRA_SESSION_ID = "linko.session.id"
        const val EXTRA_ROLE = "linko.role"
        const val EXTRA_SESSION_KEY = "linko.session.key"
        const val EXTRA_ALLOWED_PACKAGES = "linko.allowed.packages"
        const val ROLE_RECEIVER = "receiver"
        private const val RECEIVE_TIMEOUT_MS = 500
        private const val MAX_IP_PACKET = 64 * 1024
        private const val MAX_TUN_PAYLOAD = 32 * 1024
        private const val TUN_MTU = 1280
    }
}
