package com.linkshare.app.auth

import android.content.Context

/** Persistent startup cache: resilience/performance only, never a replacement for server truth. */
class LinkoStartupCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("linko_startup_cache", Context.MODE_PRIVATE)

    fun hasUsableIdentity(): Boolean = !userId().isNullOrBlank() && !linkoId().isNullOrBlank()
    fun userId(): String? = prefs.getString(KEY_USER_ID, null)
    fun linkoId(): String? = prefs.getString(KEY_LINKO_ID, null)
    fun username(): String? = prefs.getString(KEY_USERNAME, null)
    fun displayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)
    fun deviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun lastSuccessfulSync(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun saveIdentity(userId: String?, linkoId: String?, username: String?, displayName: String?) {
        prefs.edit().apply {
            userId?.takeIf { it.isNotBlank() }?.let { putString(KEY_USER_ID, it) }
            linkoId?.takeIf { it.isNotBlank() }?.let { putString(KEY_LINKO_ID, it) }
            username?.removePrefix("@")?.takeIf { it.isNotBlank() }?.let { putString(KEY_USERNAME, it) }
            displayName?.trim()?.takeIf { it.isNotBlank() }?.let { putString(KEY_DISPLAY_NAME, it) }
            putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            apply()
        }
    }

    fun saveDeviceId(deviceId: String?) {
        if (!deviceId.isNullOrBlank()) prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LINKO_ID = "linko_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_successful_sync"
    }
}
