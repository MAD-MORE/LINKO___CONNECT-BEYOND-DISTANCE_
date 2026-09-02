package com.linkshare.app.tunnel

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated encrypted LINKO datagram transport for the direct P2P data plane. */
class EncryptedDatagramTunnel(
    private val socket: DatagramSocket,
    private val peer: InetSocketAddress,
    val sessionId: String,
    val role: Role,
    sessionKey: ByteArray
) : AutoCloseable {
    enum class Role(val code: Byte) { PROVIDER(1), RECEIVER(2) }
    enum class PacketType(val code: Byte) { DATA(1), PING(2), PONG(3), HANDSHAKE(4), CLOSE(5) }
    data class ReceivedPacket(val type: PacketType, val sequenceNumber: Long, val payload: ByteArray)

    private val key = SecretKeySpec(sessionKey.copyOf().also { require(it.size == 32) { "LINKO session key must be exactly 32 bytes" } }, "AES")
    private val keyHash = MessageDigest.getInstance("SHA-256").digest(sessionKey)
    private val sessionBytes = sessionId.toByteArray(Charsets.US_ASCII).also { require(it.size == SESSION_ID_LEN) { "Session ID must be 36 ASCII characters" } }
    private val random = SecureRandom()
    private val sendSequence = AtomicLong(1)

    // Replay state is updated only after successful AES-GCM authentication.
    // A bounded set permits authenticated out-of-order packets inside the window.
    private val receivedSequences = LinkedHashSet<Long>(REPLAY_WINDOW)
    private var maxReceivedSequence = 0L

    @Synchronized
    private fun acceptAuthenticatedSequence(sequence: Long): Boolean {
        if (sequence <= 0L) return false
        if (maxReceivedSequence > REPLAY_WINDOW && sequence < maxReceivedSequence - REPLAY_WINDOW) return false
        if (!receivedSequences.add(sequence)) return false
        if (sequence > maxReceivedSequence) maxReceivedSequence = sequence
        val floor = (maxReceivedSequence - REPLAY_WINDOW).coerceAtLeast(0L)
        val iterator = receivedSequences.iterator()
        while (iterator.hasNext()) if (iterator.next() < floor) iterator.remove()
        return true
    }

    fun send(plaintext: ByteArray, type: PacketType = PacketType.DATA) {
        require(plaintext.size <= MAX_PAYLOAD) { "Payload exceeds maximum allowable size (${plaintext.size} > $MAX_PAYLOAD)" }
        val seq = sendSequence.getAndIncrement()
        val nonce = ByteArray(NONCE_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, nonce))
        val aad = ByteBuffer.allocate(HEADER_NO_NONCE_LEN).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC); put(VERSION); put(sessionBytes); put(keyHash); put(role.code); put(type.code); putLong(seq)
        }.array()
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        val wire = ByteBuffer.allocate(HEADER_LEN + ciphertext.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(aad); put(nonce); put(ciphertext)
        }.array()
        socket.send(DatagramPacket(wire, wire.size, peer))
    }

    fun receive(timeoutMs: Int = 1000): ReceivedPacket? {
        socket.soTimeout = timeoutMs
        val rawBuffer = ByteArray(MAX_FRAME_LEN)
        val packet = DatagramPacket(rawBuffer, rawBuffer.size)
        try { socket.receive(packet) } catch (_: java.net.SocketTimeoutException) { return null }
        if (packet.length < HEADER_LEN + TAG_LEN_BYTES) return null

        val buffer = ByteBuffer.wrap(rawBuffer, 0, packet.length).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC_LEN); buffer.get(magic)
        if (!magic.contentEquals(MAGIC)) return null
        if (buffer.get() != VERSION) return null
        val rxSession = ByteArray(SESSION_ID_LEN); buffer.get(rxSession)
        if (!rxSession.contentEquals(sessionBytes)) return null
        val rxKeyHash = ByteArray(KEY_HASH_LEN); buffer.get(rxKeyHash)
        if (!rxKeyHash.contentEquals(keyHash)) return null
        val senderRoleCode = buffer.get()
        val expectedPeerRole = if (role == Role.RECEIVER) Role.PROVIDER else Role.RECEIVER
        if (senderRoleCode != expectedPeerRole.code) return null
        val typeCode = buffer.get()
        val type = PacketType.values().firstOrNull { it.code == typeCode } ?: return null
        val seq = buffer.getLong()
        if (seq <= 0L) return null

        val nonce = ByteArray(NONCE_LEN); buffer.get(nonce)
        val ciphertextLen = packet.length - HEADER_LEN
        if (ciphertextLen < TAG_LEN_BYTES) return null
        val ciphertext = ByteArray(ciphertextLen); buffer.get(ciphertext)

        val plaintext = try {
            Cipher.getInstance(CIPHER_ALGO).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, nonce))
                updateAAD(rawBuffer.copyOfRange(0, HEADER_NO_NONCE_LEN))
                doFinal(ciphertext)
            }
        } catch (_: Exception) { return null }

        if (!acceptAuthenticatedSequence(seq)) return null
        return ReceivedPacket(type, seq, plaintext)
    }

    fun sendPing(timestampMs: Long = System.currentTimeMillis()) {
        send(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(timestampMs).array(), PacketType.PING)
    }

    fun sendPong(echoTimestamp: Long) {
        send(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(echoTimestamp).array(), PacketType.PONG)
    }

    fun sendClose() { runCatching { send(ByteArray(0), PacketType.CLOSE) } }

    override fun close() {
        runCatching { sendClose() }
        if (!socket.isClosed) socket.close()
    }

    companion object {
        private val MAGIC = byteArrayOf(0x4C, 0x4B, 0x4F, 0x32)
        private const val MAGIC_LEN = 4
        private const val VERSION: Byte = 2
        private const val SESSION_ID_LEN = 36
        private const val KEY_HASH_LEN = 32
        private const val NONCE_LEN = 12
        private const val TAG_LEN_BYTES = 16
        private const val TAG_LEN_BITS = 128
        private const val CIPHER_ALGO = "AES/GCM/NoPadding"
        const val HEADER_NO_NONCE_LEN = MAGIC_LEN + 1 + SESSION_ID_LEN + KEY_HASH_LEN + 1 + 1 + 8
        const val HEADER_LEN = HEADER_NO_NONCE_LEN + NONCE_LEN
        const val MAX_PAYLOAD = 32 * 1024
        const val MAX_FRAME_LEN = HEADER_LEN + MAX_PAYLOAD + TAG_LEN_BYTES
        private const val REPLAY_WINDOW = 2048
    }
}
