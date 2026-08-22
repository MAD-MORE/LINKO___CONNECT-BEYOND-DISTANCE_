package com.linkshare.app.tunnel

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Small, deterministic HKDF-SHA256 implementation used to derive per-session
 * transport keys from an authenticated session secret. The signaling service
 * must never receive the resulting key.
 */
object SessionKeyDerivation {
    fun derive(sharedSecret: ByteArray, sessionId: String, length: Int = 32): ByteArray {
        require(sharedSecret.isNotEmpty())
        require(length in 1..255 * 32)
        val salt = MessageDigest.getInstance("SHA-256").digest(sessionId.toByteArray())
        val prk = hmac(salt, sharedSecret)
        val info = "LINKO-v1-session".toByteArray()
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val count = minOf(previous.size, length - offset)
            previous.copyInto(output, offset, 0, count)
            offset += count
            counter++
        }
        return output
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
}
