package com.linkshare.app.tunnel

import java.io.Closeable

/** Dispatches provider IPv4 packets to the appropriate transport adapter. */
class ProviderIpPacketForwarder : Closeable {
    private val udp = ProviderUdpPacketForwarder()
    private val tcp = ProviderTcpPacketForwarder()

    fun forward(packet: ByteArray): List<ByteArray> {
        if (packet.size < 20 || (packet[0].toInt() ushr 4) != 4) return emptyList()
        return when (packet[9].toInt() and 0xff) {
            17 -> listOfNotNull(udp.forward(packet))
            6 -> tcp.forward(packet)
            else -> emptyList()
        }
    }

    fun drainTcpResponses(maxPackets: Int = 32): List<ByteArray> = tcp.drainResponses(maxPackets)

    override fun close() {
        udp.close()
        tcp.close()
    }
}
