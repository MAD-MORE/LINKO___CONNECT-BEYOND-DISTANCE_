package com.linkshare.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import com.linkshare.app.tunnel.IpPacketRouter
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LinkShareVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null
    private var transport: EncryptedDatagramTunnel? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newFixedThreadPool(2)
    private val router = IpPacketRouter()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra(EXTRA_PEER_HOST)
        val port = intent?.getIntExtra(EXTRA_PEER_PORT, -1) ?: -1
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val role = intent?.getStringExtra(EXTRA_ROLE)
        val sessionKey = intent?.getByteArrayExtra(EXTRA_SESSION_KEY)
        if (host.isNullOrBlank() || port !in 1..65535 || sessionId.isNullOrBlank() || role != ROLE_RECEIVER || sessionKey?.size != 32) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (VpnService.prepare(this) != null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        stopTunnel()
        tunnelInterface = Builder()
            .setSession("LINKO tunnel")
            .setMtu(TUN_MTU)
            .addAddress("10.48.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .establish()
            ?: return START_NOT_STICKY

        val socket = DatagramSocket()
        if (!protect(socket)) {
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
        executor.execute { outboundLoop() }
        executor.execute { inboundLoop() }
        return START_STICKY
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
                    transport?.send(raw)
                }
            }
        } catch (_: Exception) {
            stopTunnel()
        }
    }

    private fun inboundLoop() {
        val descriptor = tunnelInterface ?: return
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                while (running.get()) {
                    try {
                        val packet = transport?.receive(RECEIVE_TIMEOUT_MS) ?: continue
                        if (packet.size <= MAX_IP_PACKET && router.parse(packet) != null) {
                            output.write(packet)
                            output.flush()
                        }
                    } catch (_: SocketTimeoutException) {
                        // Keep polling while the VPN remains active.
                    }
                }
            }
        } catch (_: Exception) {
            stopTunnel()
        }
    }

    private fun stopTunnel() {
        running.set(false)
        transport?.close()
        transport = null
        tunnelInterface?.close()
        tunnelInterface = null
    }

    override fun onDestroy() {
        stopTunnel()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PEER_HOST = "linko.peer.host"
        const val EXTRA_PEER_PORT = "linko.peer.port"
        const val EXTRA_SESSION_ID = "linko.session.id"
        const val EXTRA_ROLE = "linko.role"
        const val EXTRA_SESSION_KEY = "linko.session.key"
        const val ROLE_RECEIVER = "receiver"
        private const val RECEIVE_TIMEOUT_MS = 1000
        private const val MAX_IP_PACKET = 64 * 1024
        private const val MAX_TUN_PAYLOAD = 16 * 1024
        private const val TUN_MTU = 1500
    }
}
