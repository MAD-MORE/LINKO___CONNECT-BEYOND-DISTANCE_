package com.linkshare.app.tunnel

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Application traffic is encrypted before it leaves the Android device.
 * The relay treats the resulting bytes as opaque ciphertext.
 */
object EncryptedFrame {
    private const val VERSION: Byte = 1
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32) { "LINKO session key must be 256 bits" }
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + NONCE_SIZE + ciphertext.size)
            .put(VERSION).put(nonce).put(ciphertext).array()
    }

    fun decrypt(frame: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32) { "LINKO session key must be 256 bits" }
        require(frame.size > 1 + NONCE_SIZE) { "invalid encrypted frame" }
        require(frame[0] == VERSION) { "unsupported frame version" }
        val nonce = frame.copyOfRange(1, 1 + NONCE_SIZE)
        val ciphertext = frame.copyOfRange(1 + NONCE_SIZE, frame.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    fun keyFromBase64(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP).also { require(it.size == 32) }
}
