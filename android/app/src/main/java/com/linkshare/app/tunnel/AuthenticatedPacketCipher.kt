package com.linkshare.app.tunnel

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AEAD packet protection. Nonces are generated per packet and are included in the frame. */
class AuthenticatedPacketCipher(key: ByteArray) {
    private val key = SecretKeySpec(requireKey(key), "AES")
    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    fun decrypt(frame: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(frame.size >= 12 + 16) { "Invalid LINKO packet" }
        val nonce = frame.copyOfRange(0, 12)
        val ciphertext = frame.copyOfRange(12, frame.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    private fun requireKey(value: ByteArray): ByteArray {
        require(value.size == 16 || value.size == 24 || value.size == 32) { "AES key must be 128/192/256-bit" }
        return value.copyOf()
    }
}
