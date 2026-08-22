package com.linkshare.app.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** Runs the provider side of a LINKO relay session through an explicit IP transport boundary. */
class ProviderTunnelRunner(
    private val socket: DatagramSocket,
    private val endpoint: InetSocketAddress,
    private val sessionId: String,
    sessionKey: ByteArray,
    private val scope: CoroutineScope,
    private val adapter: ProviderTransportAdapter = UdpOnlyProviderTransportAdapter()
) {
    private val tunnel = EncryptedDatagramTunnel(
        socket = socket,
        peer = endpoint,
        sessionId = sessionId,
        role = EncryptedDatagramTunnel.Role.PROVIDER,
        sessionKey = sessionKey
    )
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val packet = tunnel.receive(1_000) ?: continue
                    val response = adapter.forward(packet)
                    if (response != null) tunnel.send(response)
                } catch (_: java.net.SocketTimeoutException) {
                    // Keep the authorized session alive while waiting for traffic.
                } catch (_: Exception) {
                    // Malformed, unsupported, or failed packets are isolated to this frame.
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        adapter.close()
        tunnel.close()
    }
}
