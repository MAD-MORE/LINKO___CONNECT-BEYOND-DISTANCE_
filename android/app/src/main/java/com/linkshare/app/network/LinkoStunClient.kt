package com.linkshare.app.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/** Minimal RFC 5389 STUN Binding client for NAT-mapped endpoint discovery. */
object LinkoStunClient {
    private const val MAGIC_COOKIE = 0x2112A442
    private const val BINDING_REQUEST = 0x0001
    private const val BINDING_SUCCESS = 0x0101
    private const val XOR_MAPPED_ADDRESS = 0x0020
    private const val MAPPED_ADDRESS = 0x0001

    data class Candidate(val address: InetAddress, val port: Int, val type: String = "srflx") {
        val endpoint: InetSocketAddress get() = InetSocketAddress(address, port)
    }

    fun discover(socket: DatagramSocket, serverHost: String = "stun.l.google.com", serverPort: Int = 19302, timeoutMs: Int = 2500): Candidate? {
        val transaction = ByteArray(12).also(SecureRandom()::nextBytes)
        val request = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
            .putShort(BINDING_REQUEST.toShort()).putShort(0).putInt(MAGIC_COOKIE).put(transaction).array()
        socket.soTimeout = timeoutMs
        socket.send(DatagramPacket(request, request.size, InetAddress.getByName(serverHost), serverPort))
        val response = DatagramPacket(ByteArray(2048), 2048)
        socket.receive(response)
        return parse(response.data, response.length, transaction)
    }

    private fun parse(data: ByteArray, length: Int, transaction: ByteArray): Candidate? {
        if (length < 20) return null
        val b = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
        if ((b.short.toInt() and 0xffff) != BINDING_SUCCESS) return null
        b.short
        if (b.int != MAGIC_COOKIE) return null
        val echoed = ByteArray(12); b.get(echoed)
        if (!echoed.contentEquals(transaction)) return null
        var offset = 20
        while (offset + 4 <= length) {
            val type = ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
            val size = ((data[offset + 2].toInt() and 0xff) shl 8) or (data[offset + 3].toInt() and 0xff)
            val valueStart = offset + 4
            if (valueStart + size > length) return null
            if (type == XOR_MAPPED_ADDRESS || type == MAPPED_ADDRESS) {
                val family = data[valueStart + 1].toInt() and 0xff
                if (family == 0x01 && size >= 8) {
                    val rawPort = ((data[valueStart + 2].toInt() and 0xff) shl 8) or (data[valueStart + 3].toInt() and 0xff)
                    val port = if (type == XOR_MAPPED_ADDRESS) rawPort xor (MAGIC_COOKIE ushr 16) else rawPort
                    val address = ByteArray(4)
                    System.arraycopy(data, valueStart + 4, address, 0, 4)
                    if (type == XOR_MAPPED_ADDRESS) {
                        val cookie = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(MAGIC_COOKIE).array()
                        for (i in 0..3) address[i] = (address[i].toInt() xor cookie[i].toInt()).toByte()
                    }
                    return Candidate(InetAddress.getByAddress(address), port)
                }
            }
            offset += 4 + ((size + 3) and 3)
        }
        return null
    }
}
