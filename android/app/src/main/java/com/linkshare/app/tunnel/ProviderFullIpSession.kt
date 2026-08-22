package com.linkshare.app.tunnel

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Bridges one authorized LINKO provider session to a full-IP userspace engine.
 * A loopback SOCKS5 server is created and destroyed with the session so the
 * tun2socks engine always has a concrete provider-network egress path.
 */
class ProviderFullIpSession(
    private val socket: DatagramSocket,
    endpoint: InetSocketAddress,
    private val sessionId: String,
    sessionKey: ByteArray,
    private val tun: ParcelFileDescriptor,
    private val scope: CoroutineScope,
    private val engine: FullIpTunnelEngine = HevFullIpTunnelEngine()
) : AutoCloseable {
    private val tunnel = EncryptedDatagramTunnel(
        socket = socket,
        peer = endpoint,
        sessionId = sessionId,
        role = EncryptedDatagramTunnel.Role.PROVIDER,
        sessionKey = sessionKey
    )
    private val socks = ProviderSocks5Server()
    private var inboundJob: Job? = null
    private var outboundJob: Job? = null

    fun start() {
        check(inboundJob == null) { "provider_full_ip_session_already_started" }
        val socksPort = socks.start()
        try {
            engine.start(tun.fileDescriptor, LOOPBACK_HOST, socksPort)
        } catch (error: Throwable) {
            socks.close()
            throw error
        }

        inboundJob = scope.launch(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseOutputStream(tun.fileDescriptor).use { output ->
                while (isActive) {
                    try {
                        val packet = tunnel.receive(1_000) ?: continue
                        if (packet.size <= MAX_IP_PACKET) {
                            output.write(packet)
                            output.flush()
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue polling with bounded work.
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        outboundJob = scope.launch(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseInputStream(tun.fileDescriptor).use { input ->
                val buffer = ByteArray(MAX_IP_PACKET)
                while (isActive) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    tunnel.send(buffer.copyOf(count))
                }
            }
        }
    }

    override fun close() {
        inboundJob?.cancel()
        outboundJob?.cancel()
        inboundJob = null
        outboundJob = null
        runCatching { engine.close() }
        socks.close()
        tunnel.close()
        tun.close()
    }

    companion object {
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val MAX_IP_PACKET = 64 * 1024
    }
}
