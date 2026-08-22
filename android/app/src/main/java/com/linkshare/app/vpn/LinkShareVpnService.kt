package com.linkshare.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LinkShareVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null
    private var transport: EncryptedDatagramTunnel? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra(EXTRA_PEER_HOST)
        val port = intent?.getIntExtra(EXTRA_PEER_PORT, -1) ?: -1
        val sessionKey = intent?.getByteArrayExtra(EXTRA_SESSION_KEY)
        if (host.isNullOrBlank() || port !in 1..65535 || sessionKey?.size != 32) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        stopTunnel()
        tunnelInterface = Builder()
            .setSession("LINKO tunnel")
            .addAddress("10.48.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .establish()
            ?: return START_NOT_STICKY

        val socket = DatagramSocket()
        if (!protect(socket)) {
            socket.close()
            stopTunnel()
            return START_NOT_STICKY
        }

        transport = EncryptedDatagramTunnel(socket, InetSocketAddress(host, port), sessionKey)
        running.set(true)
        executor.execute { packetLoop() }
        return START_STICKY
    }

    private fun packetLoop() {
        val descriptor = tunnelInterface ?: return
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val packet = ByteArray(MAX_IP_PACKET)
                while (running.get()) {
                    val count = input.read(packet)
                    if (count <= 0) break
                    transport?.send(packet.copyOf(count))
                }
            }
        } catch (_: Exception) {
            // Teardown is handled by stopTunnel().
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
        const val EXTRA_SESSION_KEY = "linko.session.key"
        private const val MAX_IP_PACKET = 64 * 1024
    }
}
