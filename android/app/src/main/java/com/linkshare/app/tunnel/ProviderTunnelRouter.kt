package com.linkshare.app.tunnel

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * Provider-side packet router boundary. It accepts decrypted IP packets from
 * the relay and forwards them through the provider's normal network stack.
 * Reverse packets are returned to the encrypted tunnel through [sendBack].
 *
 * The router deliberately owns no long-lived credentials; the session key
 * remains in the tunnel client and is never persisted.
 */
class ProviderTunnelRouter(private val bindPort: Int = 0) : AutoCloseable {
    private val socket = DatagramChannel.open().apply {
        bind(InetSocketAddress("127.0.0.1", bindPort))
        configureBlocking(false)
    }

    fun forward(packet: ByteArray, destination: InetSocketAddress): Boolean = runCatching {
        socket.send(ByteBuffer.wrap(packet), destination)
        true
    }.getOrDefault(false)

    fun receive(buffer: ByteArray): Pair<Int, InetSocketAddress?>? = runCatching {
        val target = socket.receive(ByteBuffer.wrap(buffer))
        if (target is InetSocketAddress) buffer.size to target else null
    }.getOrNull()

    override fun close() {
        socket.close()
    }
}
