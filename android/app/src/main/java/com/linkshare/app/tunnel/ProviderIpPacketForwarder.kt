package com.linkshare.app.tunnel

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Provider-side IPv4 packet forwarder.
 * Dispatches:
 * - UDP (Protocol 17): DNS queries and general UDP forwarding.
 * - TCP (Protocol 6): Stateful userspace TCP flow proxy.
 * - ICMP (Protocol 1): Echo Request -> Echo Reply synthesis for reachability tests.
 */
class ProviderIpPacketForwarder : Closeable {
    private val udp = ProviderUdpPacketForwarder()
    private val tcp = ProviderTcpPacketForwarder()

    fun forward(packet: ByteArray): List<ByteArray> {
        if (packet.size < 20 || (packet[0].toInt() ushr 4) != 4) return emptyList()
        val protocol = packet[9].toInt() and 0xff
        return when (protocol) {
            17 -> listOfNotNull(udp.forward(packet))
            6 -> tcp.forward(packet)
            1 -> listOfNotNull(handleIcmp(packet))
            else -> emptyList()
        }
    }

    fun drainTcpResponses(maxPackets: Int = 32): List<ByteArray> = tcp.drainResponses(maxPackets)

    private fun handleIcmp(packet: ByteArray): ByteArray? {
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (packet.size < ihl + 8) return null
        val type = packet[ihl].toInt() and 0xff
        val code = packet[ihl + 1].toInt() and 0xff
        if (type != 8 || code != 0) return null // Only handle ICMP Echo Request

        // Build Echo Reply (Type 0, Code 0) swapping src and dst IPs
        val reply = packet.copyOf()
        // Swap IP addresses (src at 12..15, dst at 16..19)
        System.arraycopy(packet, 16, reply, 12, 4)
        System.arraycopy(packet, 12, reply, 16, 4)

        // Set ICMP Type to 0 (Echo Reply)
        reply[ihl] = 0
        reply[ihl + 1] = 0
        reply[ihl + 2] = 0 // Clear ICMP checksum for recalculation
        reply[ihl + 3] = 0

        // Recalculate ICMP checksum
        val icmpLen = packet.size - ihl
        val icmpChecksum = checksum(reply, ihl, icmpLen)
        reply[ihl + 2] = (icmpChecksum ushr 8).toByte()
        reply[ihl + 3] = icmpChecksum.toByte()

        // Recalculate IP header checksum
        reply[10] = 0
        reply[11] = 0
        val ipChecksum = checksum(reply, 0, ihl)
        reply[10] = (ipChecksum ushr 8).toByte()
        reply[11] = ipChecksum.toByte()

        return reply
    }

    override fun close() {
        udp.close()
        tcp.close()
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += (((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < end) sum += ((bytes[i].toInt() and 0xFF) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
