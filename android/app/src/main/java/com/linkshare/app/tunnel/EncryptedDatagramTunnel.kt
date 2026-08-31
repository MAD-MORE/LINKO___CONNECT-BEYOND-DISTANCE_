package com.linkshare.app.tunnel

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LINKO Authenticated Encrypted Datagram Tunnel.
 *
 * Wire Framing Format (V2):
 * ┌──────────┬─────────┬──────────────┬──────────┬──────┬──────┬───────────┬─────────┬───────────────────────┐
 * │ Magic    │ Version │ Session ID   │ Key Hash │ Role │ Type │ Sequence  │ Nonce   │ Ciphertext + Auth Tag │
 * │ (4B)     │ (1B)    │ (36B UUID)   │ (32B)    │ (1B) │ (1B) │ (8B)      │ (12B)   │ (Variable + 16B tag)  │
 * └──────────┴─────────┴──────────────┴──────────┴──────┴──────┴───────────┴─────────┴───────────────────────┘
 * Total Header: 4 + 1 + 36 + 32 + 1 + 1 + 8 + 12 = 95 bytes.
 */
class EncryptedDatagramTunnel(
    private val socket: DatagramSocket,
    private val peer: InetSocketAddress,
    val sessionId: String,
    val role: Role,
    sessionKey: ByteArray
) : AutoCloseable {

    enum class Role(val code: Byte) {
        PROVIDER(1),
        RECEIVER(2)
    }

    enum class PacketType(val code: Byte) {
        DATA(1),
        PING(2),
        PONG(3),
        HANDSHAKE(4),
        CLOSE(5)
    }

    data class ReceivedPacket(
        val type: PacketType,
        val sequenceNumber: Long,
        val payload: ByteArray
    )

    private val key = SecretKeySpec(sessionKey.copyOf().also {
        require(it.size == 32) { "LINKO session key must be exactly 32 bytes" }
    }, "AES")

    private val keyHash: ByteArray = MessageDigest.getInstance("SHA-256").digest(sessionKey)
    private val sessionBytes: ByteArray = sessionId.toByteArray(Charsets.US_ASCII).also {
        require(it.size == SESSION_ID_LEN) { "Session ID must be 36 ASCII characters" }
    }

    private val random = SecureRandom()
    private val sendSequence = AtomicLong(1)
    private var maxReceivedSequence = 0L

    /** Encrypts and transmits a packet of the specified type to the peer. */
    fun send(plaintext: ByteArray, type: PacketType = PacketType.DATA) {
        require(plaintext.size <= MAX_PAYLOAD) { "Payload exceeds maximum allowable size (${plaintext.size} > $MAX_PAYLOAD)" }

        val seq = sendSequence.getAndIncrement()
        val nonce = ByteArray(NONCE_LEN).also(random::nextBytes)

        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, nonce))

        // Authenticate the header fields via AAD (Additional Authenticated Data)
        val aad = ByteBuffer.allocate(HEADER_NO_NONCE_LEN).order(ByteOrder.BIG_ENDIAN)
        aad.put(MAGIC)
        aad.put(VERSION)
        aad.put(sessionBytes)
        aad.put(keyHash)
        aad.put(role.code)
        aad.put(type.code)
        aad.putLong(seq)
        cipher.updateAAD(aad.array())

        val ciphertext = cipher.doFinal(plaintext)

        val packetBuffer = ByteBuffer.allocate(HEADER_LEN + ciphertext.size).order(ByteOrder.BIG_ENDIAN)
        packetBuffer.put(aad.array())
        packetBuffer.put(nonce)
        packetBuffer.put(ciphertext)

        val wire = packetBuffer.array()
        val datagram = DatagramPacket(wire, wire.size, peer)
        socket.send(datagram)
    }

    /**
     * Receives and decrypts a packet from the tunnel socket.
     * Returns null on timeout or if packet is malformed / fails MAC verification.
     */
    fun receive(timeoutMs: Int = 1000): ReceivedPacket? {
        socket.soTimeout = timeoutMs
        val rawBuffer = ByteArray(MAX_FRAME_LEN)
        val packet = DatagramPacket(rawBuffer, rawBuffer.size)

        try {
            socket.receive(packet)
        } catch (_: java.net.SocketTimeoutException) {
            return null
        }

        if (packet.length < HEADER_LEN + TAG_LEN_BYTES) return null

        val buffer = ByteBuffer.wrap(rawBuffer, 0, packet.length).order(ByteOrder.BIG_ENDIAN)

        // 1. Verify Magic & Version
        val magic = ByteArray(MAGIC_LEN)
        buffer.get(magic)
        if (!magic.contentEquals(MAGIC)) return null

        val version = buffer.get()
        if (version != VERSION) return null

        // 2. Verify Session ID & Key Hash
        val rxSession = ByteArray(SESSION_ID_LEN)
        buffer.get(rxSession)
        if (!rxSession.contentEquals(sessionBytes)) return null

        val rxKeyHash = ByteArray(KEY_HASH_LEN)
        buffer.get(rxKeyHash)
        if (!rxKeyHash.contentEquals(keyHash)) return null

        // 3. Verify Sender Role (peer must be the opposite role)
        val senderRoleCode = buffer.get()
        val expectedPeerRole = if (role == Role.RECEIVER) Role.PROVIDER else Role.RECEIVER
        if (senderRoleCode != expectedPeerRole.code) return null

        // 4. Read Packet Type & Sequence
        val typeCode = buffer.get()
        val type = PacketType.values().firstOrNull { it.code == typeCode } ?: return null
        val seq = buffer.getLong()

        // 5. Anti-Replay Protection: check sequence monotonicity (with window tolerance)
        if (seq <= maxReceivedSequence && maxReceivedSequence - seq > REPLAY_WINDOW) {
            return null
        }
        if (seq > maxReceivedSequence) {
            maxReceivedSequence = seq
        }

        // 6. Read Nonce & Ciphertext
        val nonce = ByteArray(NONCE_LEN)
        buffer.get(nonce)

        val ciphertextLen = packet.length - HEADER_LEN
        val ciphertext = ByteArray(ciphertextLen)
        buffer.get(ciphertext)

        // 7. Decrypt and verify with AAD
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, nonce))

            val aad = rawBuffer.copyOfRange(0, HEADER_NO_NONCE_LEN)
            cipher.updateAAD(aad)

            val plaintext = cipher.doFinal(ciphertext)
            ReceivedPacket(type, seq, plaintext)
        } catch (_: Exception) {
            null
        }
    }

    /** Sends a keepalive ping packet. */
    fun sendPing(timestampMs: Long = System.currentTimeMillis()) {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(timestampMs).array()
        send(payload, PacketType.PING)
    }

    /** Sends a keepalive pong response echoing the received timestamp. */
    fun sendPong(echoTimestamp: Long) {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(echoTimestamp).array()
        send(payload, PacketType.PONG)
    }

    /** Sends a graceful termination packet to the peer. */
    fun sendClose() {
        runCatching { send(ByteArray(0), PacketType.CLOSE) }
    }

    override fun close() {
        sendClose()
        if (!socket.isClosed) {
            socket.close()
        }
    }

    companion object {
        private val MAGIC = byteArrayOf(0x4C, 0x4B, 0x4F, 0x32) // "LKO2"
        private const val MAGIC_LEN = 4
        private const val VERSION: Byte = 2
        private const val SESSION_ID_LEN = 36
        private const val KEY_HASH_LEN = 32
        private const val NONCE_LEN = 12
        private const val TAG_LEN_BYTES = 16
        private const val TAG_LEN_BITS = 128
        private const val CIPHER_ALGO = "AES/GCM/NoPadding"

        const val HEADER_NO_NONCE_LEN = MAGIC_LEN + 1 + SESSION_ID_LEN + KEY_HASH_LEN + 1 + 1 + 8 // 83 bytes
        const val HEADER_LEN = HEADER_NO_NONCE_LEN + NONCE_LEN // 95 bytes
        const val MAX_PAYLOAD = 32 * 1024 // 32 KB MTU
        const val MAX_FRAME_LEN = HEADER_LEN + MAX_PAYLOAD + TAG_LEN_BYTES
        private const val REPLAY_WINDOW = 2048
    }
}
