package com.linkshare.app.tunnel

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedDatagramTunnel(
    private val socket: DatagramSocket,
    private val peer: InetSocketAddress,
    private val sessionId: String,
    private val role: Role,
    sessionKey: ByteArray
) : AutoCloseable {
    enum class Role { RECEIVER, PROVIDER }

    private val key = SecretKeySpec(sessionKey.copyOf().also {
        require(it.size == 32) { "LINKO session key must be 32 bytes" }
    }, "AES")
    private val random = SecureRandom()
    private val sessionHeader = sessionId.toByteArray(Charsets.UTF_8)

    fun send(plaintext: ByteArray) {
        require(plaintext.size <= MAX_PAYLOAD) { "LINKO payload too large" }
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        val frame = ByteArray(MAGIC.size + 1 + NONCE_SIZE + ciphertext.size)
        MAGIC.copyInto(frame)
        frame[MAGIC.size] = VERSION
        nonce.copyInto(frame, MAGIC.size + 1)
        ciphertext.copyInto(frame, MAGIC.size + 1 + NONCE_SIZE)
        val header = sessionHeader + byteArrayOf(0, if (role == Role.RECEIVER) 0 else 1)
        val wire = header + frame
        socket.send(DatagramPacket(wire, wire.size, peer))
    }

    fun receive(timeoutMs: Int = 1000): ByteArray? {
        socket.soTimeout = timeoutMs
        val buffer = ByteArray(MAX_FRAME + sessionHeader.size + 2)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val separator = findSeparator(buffer, sessionHeader.size, packet.length)
        if (separator != sessionHeader.size || !buffer.copyOfRange(0, separator).contentEquals(sessionHeader)) return null
        val targetRole = buffer[separator + 1].toInt()
        if (targetRole != if (role == Role.RECEIVER) 0 else 1) return null
        val frameStart = separator + 2
        if (packet.length < frameStart + HEADER_SIZE + TAG_SIZE) return null
        if (!buffer.copyOfRange(frameStart, frameStart + MAGIC.size).contentEquals(MAGIC)) return null
        if (buffer[frameStart + MAGIC.size] != VERSION) return null
        val nonceStart = frameStart + MAGIC.size + 1
        val nonce = buffer.copyOfRange(nonceStart, nonceStart + NONCE_SIZE)
        val cipherText = buffer.copyOfRange(nonceStart + NONCE_SIZE, packet.length)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.doFinal(cipherText)
        } catch (_: Exception) {
            null
        }
    }

    private fun findSeparator(buffer: ByteArray, expected: Int, length: Int): Int {
        if (expected >= length) return -1
        return if (buffer[expected].toInt() == 0) expected else -1
    }

    override fun close() = socket.close()

    companion object {
        private val MAGIC = byteArrayOf(0x4C, 0x4B, 0x4F, 0x31)
        private const val VERSION: Byte = 1
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val MAX_PAYLOAD = 16 * 1024
        private const val HEADER_SIZE = MAGIC.size + 1 + NONCE_SIZE
        private const val MAX_FRAME = HEADER_SIZE + MAX_PAYLOAD + TAG_SIZE
    }
}
