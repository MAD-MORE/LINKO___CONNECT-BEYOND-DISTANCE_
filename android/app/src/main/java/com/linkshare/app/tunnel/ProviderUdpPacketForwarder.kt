package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Provider-side IPv4/UDP adapter. It intentionally handles UDP only; arbitrary TCP/IP
 * forwarding requires a user-space TCP/IP stack and is not approximated with raw sockets.
 */
class ProviderUdpPacketForwarder : Closeable {
    fun forward(packet: ByteArray, timeoutMs: Int = 2_000): ByteArray? {
        val parsed = parseIpv4Udp(packet) ?: return null
        val socket = DatagramSocket()
        socket.soTimeout = timeoutMs
        return try {
            val outbound = DatagramPacket(parsed.payload, parsed.payload.size, parsed.destination, parsed.destinationPort)
            socket.send(outbound)
            val buffer = ByteArray(16 * 1024)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buildIpv4UdpResponse(parsed, response.address, response.port, response.data.copyOf(response.length))
        } finally {
            socket.close()
        }
    }

    override fun close() = Unit

    private data class UdpDatagram(
        val source: InetAddress,
        val sourcePort: Int,
        val destination: InetAddress,
        val destinationPort: Int,
        val payload: ByteArray
    )

    private fun parseIpv4Udp(packet: ByteArray): UdpDatagram? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (version != 4 || ihl < 20 || packet.size < ihl + 8) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null
        val totalLength = u16(packet, 2)
        if (totalLength < ihl + 8 || totalLength > packet.size) return null
        val src = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val dst = InetAddress.getByAddress(packet.copyOfRange(16, 20))
        val sourcePort = u16(packet, ihl)
        val destinationPort = u16(packet, ihl + 2)
        val udpLength = u16(packet, ihl + 4)
        if (udpLength < 8 || ihl + udpLength > totalLength) return null
        val payload = packet.copyOfRange(ihl + 8, ihl + udpLength)
        return UdpDatagram(src, sourcePort, dst, destinationPort, payload)
    }

    private fun buildIpv4UdpResponse(
        original: UdpDatagram,
        responseSource: InetAddress,
        responseSourcePort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipLength = 20
        val udpLength = 8 + payload.size
        val total = ipLength + udpLength
        val out = ByteArray(total)
        val buffer = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x45.toByte())
        buffer.put(0)
        buffer.putShort(total.toShort())
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0)
        buffer.put(responseSource.address)
        buffer.put(original.source.address)
        buffer.putShort(responseSourcePort.toShort())
        buffer.putShort(original.sourcePort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0)
        buffer.put(payload)

        val ipChecksum = checksum(out, 0, ipLength)
        out[10] = (ipChecksum ushr 8).toByte()
        out[11] = ipChecksum.toByte()

        val pseudo = ByteArray(12 + udpLength)
        System.arraycopy(responseSource.address, 0, pseudo, 0, 4)
        System.arraycopy(original.source.address, 0, pseudo, 4, 4)
        pseudo[9] = 17
        writeU16(pseudo, 10, udpLength)
        System.arraycopy(out, ipLength, pseudo, 12, udpLength)
        val udpChecksum = checksum(pseudo, 0, pseudo.size).let { if (it == 0) 0xFFFF else it }
        out[ipLength + 6] = (udpChecksum ushr 8).toByte()
        out[ipLength + 7] = udpChecksum.toByte()
        return out
    }

    private fun u16(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(bytes, i).toLong()
            i += 2
        }
        if (i < end) sum += ((bytes[i].toInt() and 0xFF) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
