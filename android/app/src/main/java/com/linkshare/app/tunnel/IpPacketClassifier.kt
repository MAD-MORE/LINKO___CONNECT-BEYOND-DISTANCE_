package com.linkshare.app.tunnel

/** Small, allocation-light classifier used before handing packets to the provider stack. */
object IpPacketClassifier {
    enum class Version { IPV4, IPV6, UNKNOWN }
    enum class Protocol { TCP, UDP, ICMP, OTHER }

    data class PacketInfo(val version: Version, val protocol: Protocol, val headerLength: Int)

    fun inspect(packet: ByteArray, length: Int = packet.size): PacketInfo {
        if (length < 1) return PacketInfo(Version.UNKNOWN, Protocol.OTHER, 0)
        return when (packet[0].toInt().ushr(4)) {
            4 -> inspectIpv4(packet, length)
            6 -> inspectIpv6(packet, length)
            else -> PacketInfo(Version.UNKNOWN, Protocol.OTHER, 0)
        }
    }

    private fun inspectIpv4(packet: ByteArray, length: Int): PacketInfo {
        if (length < 20) return PacketInfo(Version.IPV4, Protocol.OTHER, 0)
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || ihl > length) return PacketInfo(Version.IPV4, Protocol.OTHER, 0)
        return PacketInfo(Version.IPV4, protocol(packet[9].toInt() and 0xff), ihl)
    }

    private fun inspectIpv6(packet: ByteArray, length: Int): PacketInfo {
        if (length < 40) return PacketInfo(Version.IPV6, Protocol.OTHER, 0)
        return PacketInfo(Version.IPV6, protocol(packet[6].toInt() and 0xff), 40)
    }

    private fun protocol(value: Int): Protocol = when (value) {
        6 -> Protocol.TCP
        17 -> Protocol.UDP
        1, 58 -> Protocol.ICMP
        else -> Protocol.OTHER
    }
}
