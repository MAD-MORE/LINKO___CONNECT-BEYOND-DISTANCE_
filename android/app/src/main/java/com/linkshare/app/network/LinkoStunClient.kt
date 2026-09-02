package com.linkshare.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Lightweight RFC 5389 / RFC 8489 compliant STUN client.
 * Performs STUN Binding Requests to discover this device's public reflexive IP and port
 * for direct P2P NAT traversal without external dependencies.
 */
object LinkoStunClient {

    private const val STUN_MAGIC_COOKIE = 0x2112A442
    private const val BINDING_REQUEST = 0x0001
    private const val BINDING_RESPONSE = 0x0101
    private const val ATTR_MAPPED_ADDRESS = 0x0001
    private const val ATTR_XOR_MAPPED_ADDRESS = 0x0020
    private const val FAMILY_IPV4 = 0x01
    private const val FAMILY_IPV6 = 0x02

    private val random = SecureRandom()

    data class Candidate(val address: InetAddress, val port: Int, val type: String = "srflx") {
        val endpoint: InetSocketAddress get() = InetSocketAddress(address, port)
    }

    fun discover(socket: DatagramSocket, serverHost: String = "stun.l.google.com", serverPort: Int = 19302, timeoutMs: Int = 2500): Candidate? {
        val transaction = ByteArray(12).also(random::nextBytes)
        val request = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
            .putShort(BINDING_REQUEST.toShort()).putShort(0).putInt(STUN_MAGIC_COOKIE).put(transaction).array()
        return runCatching {
            socket.soTimeout = timeoutMs
            socket.send(DatagramPacket(request, request.size, InetAddress.getByName(serverHost), serverPort))
            val response = DatagramPacket(ByteArray(2048), 2048)
            socket.receive(response)
            val parsed = parseStunResponse(response.data, response.length, transaction)
            if (parsed != null) Candidate(parsed.address, parsed.port, "srflx") else null
        }.getOrNull()
    }

    val DEFAULT_STUN_SERVERS = listOf(
        "stun.l.google.com" to 19302,
        "stun1.l.google.com" to 19302,
        "stun.cloudflare.com" to 3478,
        "stun.framasoft.org" to 3478
    )

    /**
     * Queries a STUN server to discover the public mapped address of [socket] (or a temporary socket).
     */
    suspend fun discoverPublicEndpoint(
        socket: DatagramSocket? = null,
        stunHost: String = "stun.l.google.com",
        stunPort: Int = 19302,
        timeoutMs: Int = 3000
    ): InetSocketAddress? = withContext(Dispatchers.IO) {
        val ownsSocket = socket == null
        val ds = socket ?: DatagramSocket()
        try {
            ds.soTimeout = timeoutMs
            val serverAddress = InetAddress.getByName(stunHost)

            // Build STUN Binding Request Header (20 bytes)
            // 0..1: Message Type (0x0001)
            // 2..3: Message Length (0x0000 - no attributes)
            // 4..7: Magic Cookie (0x2112A442)
            // 8..19: Transaction ID (96 bits)
            val txId = ByteArray(12).also(random::nextBytes)
            val reqBuffer = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
            reqBuffer.putShort(BINDING_REQUEST.toShort())
            reqBuffer.putShort(0.toShort())
            reqBuffer.putInt(STUN_MAGIC_COOKIE)
            reqBuffer.put(txId)

            val reqBytes = reqBuffer.array()
            val reqPacket = DatagramPacket(reqBytes, reqBytes.size, serverAddress, stunPort)
            ds.send(reqPacket)

            // Receive Response
            val respBytes = ByteArray(512)
            val respPacket = DatagramPacket(respBytes, respBytes.size)
            ds.receive(respPacket)

            parseStunResponse(respPacket.data, respPacket.length, txId)
        } catch (_: Exception) {
            null
        } finally {
            if (ownsSocket) {
                runCatching { ds.close() }
            }
        }
    }

    /**
     * Parses STUN response and extracts mapped public IP and port.
     */
    fun parseStunResponse(data: ByteArray, length: Int, expectedTxId: ByteArray): InetSocketAddress? {
        if (length < 20) return null
        val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)

        val msgType = buf.short.toInt() and 0xffff
        if (msgType != BINDING_RESPONSE) return null

        val msgLength = buf.short.toInt() and 0xffff
        val magicCookie = buf.int
        if (magicCookie != STUN_MAGIC_COOKIE) return null

        val txId = ByteArray(12)
        buf.get(txId)
        if (!txId.contentEquals(expectedTxId)) return null

        val end = minOf(20 + msgLength, length)
        var mappedAddress: InetSocketAddress? = null

        // Parse attributes
        while (buf.position() + 4 <= end) {
            val attrType = buf.short.toInt() and 0xffff
            val attrLen = buf.short.toInt() and 0xffff
            if (buf.position() + attrLen > end) break

            when (attrType) {
                ATTR_XOR_MAPPED_ADDRESS -> {
                    val addr = parseXorMappedAddress(buf, attrLen, txId)
                    if (addr != null) return addr // XOR mapped address preferred
                }
                ATTR_MAPPED_ADDRESS -> {
                    if (mappedAddress == null) {
                        mappedAddress = parseMappedAddress(buf, attrLen)
                    } else {
                        buf.position(buf.position() + attrLen)
                    }
                }
                else -> {
                    // Skip unknown attribute with 4-byte padding alignment
                    buf.position(buf.position() + attrLen)
                }
            }

            // Align to 4-byte boundary
            val padding = (4 - (attrLen % 4)) % 4
            if (buf.position() + padding <= end) {
                buf.position(buf.position() + padding)
            }
        }

        return mappedAddress
    }

    private fun parseXorMappedAddress(buf: ByteBuffer, attrLen: Int, txId: ByteArray): InetSocketAddress? {
        if (attrLen < 4) return null
        buf.get() // Reserved 0x00
        val family = buf.get().toInt() and 0xff
        val xorPort = buf.short.toInt() and 0xffff
        val port = xorPort xor (STUN_MAGIC_COOKIE ushr 16)

        return when (family) {
            FAMILY_IPV4 -> {
                if (attrLen < 8) return null
                val xorIp = buf.int
                val ip = xorIp xor STUN_MAGIC_COOKIE
                val ipBytes = ByteBuffer.allocate(4).putInt(ip).array()
                val inetAddr = InetAddress.getByAddress(ipBytes)
                InetSocketAddress(inetAddr, port)
            }
            FAMILY_IPV6 -> {
                if (attrLen < 20) return null
                val xorCookieAndTxId = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                    .putInt(STUN_MAGIC_COOKIE)
                    .put(txId)
                    .array()
                val rawIp = ByteArray(16)
                buf.get(rawIp)
                for (i in 0 until 16) {
                    rawIp[i] = (rawIp[i].toInt() xor xorCookieAndTxId[i].toInt()).toByte()
                }
                val inetAddr = InetAddress.getByAddress(rawIp)
                InetSocketAddress(inetAddr, port)
            }
            else -> null
        }
    }

    private fun parseMappedAddress(buf: ByteBuffer, attrLen: Int): InetSocketAddress? {
        if (attrLen < 4) return null
        buf.get() // Reserved
        val family = buf.get().toInt() and 0xff
        val port = buf.short.toInt() and 0xffff

        return when (family) {
            FAMILY_IPV4 -> {
                if (attrLen < 8) return null
                val ipBytes = ByteArray(4)
                buf.get(ipBytes)
                val inetAddr = InetAddress.getByAddress(ipBytes)
                InetSocketAddress(inetAddr, port)
            }
            FAMILY_IPV6 -> {
                if (attrLen < 20) return null
                val ipBytes = ByteArray(16)
                buf.get(ipBytes)
                val inetAddr = InetAddress.getByAddress(ipBytes)
                InetSocketAddress(inetAddr, port)
            }
            else -> null
        }
    }
}
