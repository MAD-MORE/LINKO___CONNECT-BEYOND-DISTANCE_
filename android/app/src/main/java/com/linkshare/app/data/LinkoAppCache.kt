package com.linkshare.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small durable cache for successful LINKO reads.
 *
 * Rules:
 * - Cache is scoped by authenticated user id.
 * - Cached data is never used as proof of ONLINE status.
 * - Network remains the source of truth and refreshes the cache.
 * - A user switch invalidates the previous user's cached snapshot.
 */
class LinkoAppCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveProfile(userId: String, displayName: String?, linkoId: String?, username: String?) {
        if (userId.isBlank()) return
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_LINKO_ID, linkoId)
            .putString(KEY_USERNAME, username)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun profileFor(userId: String): CachedProfile? {
        if (userId.isBlank() || prefs.getString(KEY_USER_ID, null) != userId) return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)
        val linkoId = prefs.getString(KEY_LINKO_ID, null)
        if (displayName.isNullOrBlank() && linkoId.isNullOrBlank()) return null
        return CachedProfile(
            userId = userId,
            displayName = displayName,
            linkoId = linkoId,
            username = prefs.getString(KEY_USERNAME, null),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    fun saveFriends(userId: String, friends: JSONArray) {
        if (userId.isBlank()) return
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_FRIENDS_JSON, friends.toString())
            .putLong(KEY_FRIENDS_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun friendsFor(userId: String): JSONArray? {
        if (userId.isBlank() || prefs.getString(KEY_USER_ID, null) != userId) return null
        val raw = prefs.getString(KEY_FRIENDS_JSON, null) ?: return null
        return runCatching { JSONArray(raw) }.getOrNull()
    }

    fun clearForUser(userId: String?) {
        if (userId == null || prefs.getString(KEY_USER_ID, null) == userId) {
            prefs.edit().clear().apply()
        }
    }

    data class CachedProfile(
        val userId: String,
        val displayName: String?,
        val linkoId: String?,
        val username: String?,
        val updatedAt: Long,
    )

    companion object {
        private const val PREFS_NAME = "linko_app_cache_v1"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_LINKO_ID = "linko_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_UPDATED_AT = "profile_updated_at"
        private const val KEY_FRIENDS_JSON = "friends_json"
        private const val KEY_FRIENDS_UPDATED_AT = "friends_updated_at"
    }
}
