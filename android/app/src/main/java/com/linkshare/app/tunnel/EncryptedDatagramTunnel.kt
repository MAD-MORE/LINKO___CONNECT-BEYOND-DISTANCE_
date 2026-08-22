package com.linkshare.app.tunnel

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encrypted datagram transport for LINKO's data plane.
 * The session key must come from the authenticated session establishment flow;
 * this class never derives keys from UI data or bearer tokens.
 */
class EncryptedDatagramTunnel(
    private val socket: DatagramSocket,
    private val peer: InetSocketAddress,
    sessionKey: ByteArray
) : AutoCloseable {
    private val key = SecretKeySpec(sessionKey.copyOf().also {
        require(it.size == 32) { "LINKO session key must be 32 bytes" }
    }, "AES")
    private val random = SecureRandom()

    fun send(plaintext: ByteArray) {
        require(plaintext.size <= MAX_PAYLOAD) { "LINKO payload too large" }
        val nonce = ByteArray(NONCE_SIZE)
        random.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        val frame = ByteArray(MAGIC.size + 1 + NONCE_SIZE + ciphertext.size)
        MAGIC.copyInto(frame, 0)
        frame[MAGIC.size] = VERSION
        nonce.copyInto(frame, MAGIC.size + 1)
        ciphertext.copyInto(frame, MAGIC.size + 1 + NONCE_SIZE)
        socket.send(DatagramPacket(frame, frame.size, peer))
    }

    fun receive(timeoutMs: Int = 1000): ByteArray? {
        socket.soTimeout = timeoutMs
        val buffer = ByteArray(MAX_FRAME)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        if (packet.length < HEADER_SIZE + TAG_SIZE) return null
        if (!buffer.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        if (buffer[MAGIC.size] != VERSION) return null
        val nonceStart = MAGIC.size + 1
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

    override fun close() = socket.close()

    companion object {
        private val MAGIC = byteArrayOf(0x4C, 0x4B, 0x4F, 0x31) // LKO1
        private const val VERSION: Byte = 1
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val MAX_PAYLOAD = 16 * 1024
        private const val MAX_FRAME = MAGIC.size + 1 + NONCE_SIZE + MAX_PAYLOAD + TAG_SIZE
        private const val HEADER_SIZE = MAGIC.size + 1 + NONCE_SIZE
    }
}
