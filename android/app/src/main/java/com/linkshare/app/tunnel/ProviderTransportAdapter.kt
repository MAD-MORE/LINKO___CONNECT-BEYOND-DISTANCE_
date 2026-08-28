package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.DatagramSocket

/**
 * Provider data-plane boundary interface.
 * Receives complete raw IP packets from client over the encrypted tunnel,
 * forwards them via provider's active network connection, and returns response packets.
 */
interface ProviderTransportAdapter : Closeable {
    fun forward(packet: ByteArray): List<ByteArray>
    fun drainPending(maxCount: Int = 32): List<ByteArray>
}

/** Full IP implementation handling UDP, TCP, and ICMP. */
class FullIpProviderTransportAdapter : ProviderTransportAdapter {
    private val delegate = ProviderIpPacketForwarder()

    override fun forward(packet: ByteArray): List<ByteArray> =
        delegate.forward(packet)

    override fun drainPending(maxCount: Int): List<ByteArray> =
        delegate.drainTcpResponses(maxCount)

    override fun close() = delegate.close()
}

/** Legacy UDP-only implementation. */
class UdpOnlyProviderTransportAdapter : ProviderTransportAdapter {
    private val delegate = ProviderUdpPacketForwarder()

    override fun forward(packet: ByteArray): List<ByteArray> =
        listOfNotNull(delegate.forward(packet))

    override fun drainPending(maxCount: Int): List<ByteArray> = emptyList()

    override fun close() = delegate.close()
}

object ProviderSocketFactory {
    fun openDatagramSocket(): DatagramSocket = DatagramSocket()
}
