package com.linkshare.app.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** Runs the provider side of a LINKO relay session. UDP is supported; TCP is intentionally rejected. */
class ProviderTunnelRunner(
    private val socket: DatagramSocket,
    private val endpoint: InetSocketAddress,
    private val sessionId: String,
    sessionKey: ByteArray,
    private val scope: CoroutineScope
) {
    private val tunnel = EncryptedDatagramTunnel(
        socket = socket,
        peer = endpoint,
        sessionId = sessionId,
        role = EncryptedDatagramTunnel.Role.PROVIDER,
        sessionKey = sessionKey
    )
    private val udpForwarder = ProviderUdpPacketForwarder()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val packet = tunnel.receive(1_000) ?: continue
                    val response = udpForwarder.forward(packet)
                    if (response != null) tunnel.send(response)
                } catch (_: java.net.SocketTimeoutException) {
                    // Keep the session alive.
                } catch (_: Exception) {
                    // Invalid frames and unsupported protocols are dropped.
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        udpForwarder.close()
        tunnel.close()
    }
}
