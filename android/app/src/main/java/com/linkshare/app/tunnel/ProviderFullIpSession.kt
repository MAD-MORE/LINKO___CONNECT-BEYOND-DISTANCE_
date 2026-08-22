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
 * Bridges one authorized LINKO provider session to a userspace full-IP engine.
 * The engine owns TCP/UDP/IPv4/IPv6 state; LINKO owns authenticated transport.
 */
class ProviderFullIpSession(
    private val socket: DatagramSocket,
    endpoint: InetSocketAddress,
    private val sessionId: String,
    sessionKey: ByteArray,
    private val tun: ParcelFileDescriptor,
    private val socksHost: String,
    private val socksPort: Int,
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
    private var inboundJob: Job? = null
    private var outboundJob: Job? = null

    fun start() {
        check(inboundJob == null) { "provider_full_ip_session_already_started" }
        engine.start(tun.fileDescriptor, socksHost, socksPort)

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
                        // Continue polling without creating unbounded work.
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
        engine.close()
        tunnel.close()
        tun.close()
    }

    companion object {
        private const val MAX_IP_PACKET = 64 * 1024
    }
}
