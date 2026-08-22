package com.linkshare.app.tunnel

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Validates and classifies packets coming from Android's TUN interface.
 * This layer deliberately does not implement TCP itself; it provides a safe
 * IP framing boundary for the encrypted LINKO transport.
 */
class IpPacketRouter {
    enum class Protocol(val number: Int) { TCP(6), UDP(17), ICMP(1), OTHER(-1) }

    data class Packet(
        val version: Int,
        val protocol: Protocol,
        val payload: ByteArray
    )

    fun parse(packet: ByteArray): Packet? {
        if (packet.size < 20) return null
        val version = (packet[0].toInt() ushr 4) and 0x0f
        return when (version) {
            4 -> parseIpv4(packet)
            6 -> parseIpv6(packet)
            else -> null
        }
    }

    private fun parseIpv4(packet: ByteArray): Packet? {
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || ihl > packet.size) return null
        val total = ByteBuffer.wrap(packet, 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff
        if (total < ihl || total > packet.size) return null
        val protocol = when (packet[9].toInt() and 0xff) {
            6 -> Protocol.TCP
            17 -> Protocol.UDP
            1 -> Protocol.ICMP
            else -> Protocol.OTHER
        }
        return Packet(4, protocol, packet.copyOf(total))
    }

    private fun parseIpv6(packet: ByteArray): Packet? {
        if (packet.size < 40) return null
        val payloadLength = ByteBuffer.wrap(packet, 4, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff
        val total = 40 + payloadLength
        if (total > packet.size) return null
        val protocol = when (packet[6].toInt() and 0xff) {
            6 -> Protocol.TCP
            17 -> Protocol.UDP
            58 -> Protocol.ICMP
            else -> Protocol.OTHER
        }
        return Packet(6, protocol, packet.copyOf(total))
    }
}
