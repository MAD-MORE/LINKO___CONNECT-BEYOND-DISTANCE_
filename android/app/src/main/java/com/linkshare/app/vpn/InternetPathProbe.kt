package com.linkshare.app.vpn

import java.net.InetAddress
import java.security.SecureRandom

/** Builds and validates a small real Internet TCP probe carried inside LINKO's encrypted DATA path. */
object InternetPathProbe {
    private const val SOURCE_IP = "10.48.0.2"
    private const val TARGET_IP = "1.1.1.1"
    private const val TARGET_PORT = 443
    private const val TCP_SYN_LEN = 44

    data class Expectation(
        val sourcePort: Int,
        val sequence: Long,
        val startedAt: Long,
    )

    data class Request(
        val expectation: Expectation,
        val packet: ByteArray,
    )

    private val random = SecureRandom()

    fun create(): Request {
        val sourcePort = 40_000 + random.nextInt(20_000)
        val sequence = random.nextInt().toLong() and 0xffffffffL
        val startedAt = System.currentTimeMillis()
        return Request(Expectation(sourcePort, sequence, startedAt), buildTcpSyn(sourcePort, sequence))
    }

    fun isSuccessfulResponse(packet: ByteArray, expectation: Expectation): Boolean {
        if (packet.size < 40) return false
        if ((packet[0].toInt() ushr 4) != 4 || (packet[9].toInt() and 0xff) != 6) return false
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || packet.size < ihl + 20) return false
        val total = u16(packet, 2)
        if (total < ihl + 20 || total > packet.size) return false
        if (!ipEquals(packet, 12, TARGET_IP) || !ipEquals(packet, 16, SOURCE_IP)) return false

        val tcp = ihl
        if (u16(packet, tcp) != TARGET_PORT || u16(packet, tcp + 2) != expectation.sourcePort) return false
        val flags = u16(packet, tcp + 12) and 0x01ff
        if (flags and 0x0002 == 0 || flags and 0x0010 == 0) return false
        return u32(packet, tcp + 8) == ((expectation.sequence + 1L) and 0xffffffffL)
    }

    private fun buildTcpSyn(sourcePort: Int, sequence: Long): ByteArray {
        val packet = ByteArray(TCP_SYN_LEN)
        packet[0] = 0x45
        packet[8] = 64
        packet[9] = 6
        write16(packet, 2, TCP_SYN_LEN)
        write16(packet, 20, sourcePort)
        write16(packet, 22, TARGET_PORT)
        write32(packet, 24, sequence)
        write32(packet, 28, 0L)
        write16(packet, 32, 0x6002)
        write16(packet, 34, 65535)
        packet[40] = 2
        packet[41] = 4
        packet[42] = 0x05
        packet[43] = 0x28
        System.arraycopy(InetAddress.getByName(SOURCE_IP).address, 0, packet, 12, 4)
        System.arraycopy(InetAddress.getByName(TARGET_IP).address, 0, packet, 16, 4)

        write16(packet, 10, checksum(packet, 0, 20))
        val tcpLength = 24
        val pseudo = ByteArray(12 + tcpLength)
        System.arraycopy(packet, 12, pseudo, 0, 8)
        pseudo[9] = 6
        write16(pseudo, 10, tcpLength)
        System.arraycopy(packet, 20, pseudo, 12, tcpLength)
        write16(packet, 36, checksum(pseudo, 0, pseudo.size))
        return packet
    }

    private fun ipEquals(packet: ByteArray, offset: Int, expected: String): Boolean =
        packet.copyOfRange(offset, offset + 4).contentEquals(InetAddress.getByName(expected).address)

    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 255) shl 24) or
            ((b[o + 1].toLong() and 255) shl 16) or
            ((b[o + 2].toLong() and 255) shl 8) or
            (b[o + 3].toLong() and 255)

    private fun write16(b: ByteArray, o: Int, v: Int) { b[o] = (v ushr 8).toByte(); b[o + 1] = v.toByte() }
    private fun write32(b: ByteArray, o: Int, v: Long) { write16(b, o, (v ushr 16).toInt()); write16(b, o + 2, v.toInt()) }
    private fun checksum(b: ByteArray, o: Int, n: Int): Int {
        var sum = 0L
        var i = o
        val end = o + n
        while (i + 1 < end) { sum += u16(b, i).toLong(); i += 2 }
        if (i < end) sum += ((b[i].toInt() and 255) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }
}