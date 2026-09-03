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

    suspend fun discoverMappedAddresses(socket: DatagramSocket, deadline: Long): List<InetSocketAddress> = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, InetSocketAddress>()
        for ((host, port) in DEFAULT_STUN_SERVERS) {
            if (System.currentTimeMillis() >= deadline) break
            runCatching { discover(socket, host, port, 1_200) }
                .getOrNull()
                ?.endpoint
                ?.let { results.putIfAbsent("${it.address.hostAddress}:${it.port}", it) }
        }
        results.values.toList()
    }

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
            val txId = ByteArray(12).also(random::nextBytes)
            val reqBuffer = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
            reqBuffer.putShort(BINDING_REQUEST.toShort())
            reqBuffer.putShort(0.toShort())
            reqBuffer.putInt(STUN_MAGIC_COOKIE)
            reqBuffer.put(txId)
            val reqBytes = reqBuffer.array()
            ds.send(DatagramPacket(reqBytes, reqBytes.size, serverAddress, stunPort))
            val respBytes = ByteArray(512)
            val respPacket = DatagramPacket(respBytes, respBytes.size)
            ds.receive(respPacket)
            parseStunResponse(respPacket.data, respPacket.length, txId)
        } catch (_: Exception) {
            null
        } finally {
            if (ownsSocket) runCatching { ds.close() }
        }
    }

    fun parseStunResponse(data: ByteArray, length: Int, expectedTxId: ByteArray): InetSocketAddress? {
        if (length < 20) return null
        val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
        val msgType = buf.short.toInt() and 0xffff
        if (msgType != BINDING_RESPONSE) return null
        val msgLength = buf.short.toInt() and 0xffff
        if (buf.int != STUN_MAGIC_COOKIE) return null
        val txId = ByteArray(12)
        buf.get(txId)
        if (!txId.contentEquals(expectedTxId)) return null
        val end = minOf(20 + msgLength, length)
        var mappedAddress: InetSocketAddress? = null
        while (buf.position() + 4 <= end) {
            val attrType = buf.short.toInt() and 0xffff
            val attrLen = buf.short.toInt() and 0xffff
            if (buf.position() + attrLen > end) break
            when (attrType) {
                ATTR_XOR_MAPPED_ADDRESS -> {
                    val addr = parseXorMappedAddress(buf, attrLen, txId)
                    if (addr != null) return addr
                }
                ATTR_MAPPED_ADDRESS -> {
                    if (mappedAddress == null) mappedAddress = parseMappedAddress(buf, attrLen)
                    else buf.position(buf.position() + attrLen)
                }
                else -> buf.position(buf.position() + attrLen)
            }
            val padding = (4 - (attrLen % 4)) % 4
            if (buf.position() + padding <= end) buf.position(buf.position() + padding)
        }
        return mappedAddress
    }

    private fun parseXorMappedAddress(buf: ByteBuffer, attrLen: Int, txId: ByteArray): InetSocketAddress? {
        if (attrLen < 4) return null
        buf.get()
        val family = buf.get().toInt() and 0xff
        val xorPort = buf.short.toInt() and 0xffff
        val port = xorPort xor (STUN_MAGIC_COOKIE ushr 16)
        return when (family) {
            FAMILY_IPV4 -> {
                if (attrLen < 8) return null
                val xorIp = buf.int
                val ipBytes = ByteBuffer.allocate(4).putInt(xorIp xor STUN_MAGIC_COOKIE).array()
                InetSocketAddress(InetAddress.getByAddress(ipBytes), port)
            }
            FAMILY_IPV6 -> {
                if (attrLen < 20) return null
                val mask = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).putInt(STUN_MAGIC_COOKIE).put(txId).array()
                val rawIp = ByteArray(16)
                buf.get(rawIp)
                for (i in 0 until 16) rawIp[i] = (rawIp[i].toInt() xor mask[i].toInt()).toByte()
                InetSocketAddress(InetAddress.getByAddress(rawIp), port)
            }
            else -> null
        }
    }

    private fun parseMappedAddress(buf: ByteBuffer, attrLen: Int): InetSocketAddress? {
        if (attrLen < 4) return null
        buf.get()
        val family = buf.get().toInt() and 0xff
        val port = buf.short.toInt() and 0xffff
        return when (family) {
            FAMILY_IPV4 -> {
                if (attrLen < 8) return null
                val bytes = ByteArray(4)
                buf.get(bytes)
                InetSocketAddress(InetAddress.getByAddress(bytes), port)
            }
            FAMILY_IPV6 -> {
                if (attrLen < 20) return null
                val bytes = ByteArray(16)
                buf.get(bytes)
                InetSocketAddress(InetAddress.getByAddress(bytes), port)
            }
            else -> null
        }
    }
}
