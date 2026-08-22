package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.DatagramSocket

/**
 * Provider data-plane boundary. Implementations receive complete IP packets and return
 * complete IP packets. The default implementation supports the currently implemented
 * IPv4/UDP adapter and rejects protocols it cannot safely forward.
 */
interface ProviderTransportAdapter : Closeable {
    fun forward(packet: ByteArray, timeoutMs: Int = 2_000): ByteArray?
}

class UdpOnlyProviderTransportAdapter : ProviderTransportAdapter {
    private val delegate = ProviderUdpPacketForwarder()

    override fun forward(packet: ByteArray, timeoutMs: Int): ByteArray? =
        delegate.forward(packet, timeoutMs)

    override fun close() = delegate.close()
}

/** Creates protected provider sockets; the caller must protect the socket with VpnService. */
object ProviderSocketFactory {
    fun openDatagramSocket(): DatagramSocket = DatagramSocket()
}
