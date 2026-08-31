package com.linkshare.app.auth

import android.content.Context
import java.security.SecureRandom

/** Short-lived request credential, deliberately unrelated to the permanent device ID. */
class LinkoRequestKey(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun current(nowMillis: Long = System.currentTimeMillis()): String {
        val existing = prefs.getString(KEY_VALUE, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (!existing.isNullOrBlank() && expiresAt > nowMillis) return existing
        return rotate(nowMillis)
    }

    fun rotate(nowMillis: Long = System.currentTimeMillis()): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val value = buildString(KEY_LENGTH) {
            repeat(KEY_LENGTH) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        val key = "LNK-$value"
        prefs.edit()
            .putString(KEY_VALUE, key)
            .putLong(KEY_EXPIRES_AT, nowMillis + TTL_MILLIS)
            .apply()
        return key
    }

    fun expiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    companion object {
        private const val PREFS = "linko_request_key"
        private const val KEY_VALUE = "request_key"
        private const val KEY_EXPIRES_AT = "request_key_expires_at"
        private const val KEY_LENGTH = 12
        private const val TTL_MILLIS = 10 * 60 * 1000L
    }
}
