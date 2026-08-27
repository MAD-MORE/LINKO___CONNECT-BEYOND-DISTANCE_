package com.linkshare.app.network

import android.content.Context
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.model.Friend
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private persistence for the last known LINKO account/profile/friend context.
 * This uses SharedPreferences and never requests external storage permission.
 */
class LinkoLocalCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasUsableCache(userId: String?): Boolean {
        val cachedUser = prefs.getString(KEY_USER_ID, null)
        return !cachedUser.isNullOrBlank() && cachedUser == userId && prefs.contains(KEY_FRIENDS)
    }

    fun restoreProfile(auth: LinkoAuth, userId: String?): Boolean {
        val cachedUser = prefs.getString(KEY_USER_ID, null)
        if (cachedUser.isNullOrBlank() || cachedUser != userId) return false
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)
        val linkoId = prefs.getString(KEY_LINKO_ID, null)
        val username = prefs.getString(KEY_USERNAME, null)
        if (displayName.isNullOrBlank() && linkoId.isNullOrBlank() && username.isNullOrBlank()) return false
        auth.saveProfile(displayName, linkoId, username)
        return true
    }

    fun saveProfile(profile: ProfileRecord) {
        prefs.edit()
            .putString(KEY_USER_ID, profile.userId)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_LINKO_ID, profile.linkoId)
            .apply {
                profile.username?.let { putString(KEY_USERNAME, it) } ?: remove(KEY_USERNAME)
                putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }
            .apply()
    }

    fun saveFriends(userId: String, friends: List<Friend>) {
        val array = JSONArray()
        friends.forEach { friend ->
            array.put(JSONObject().apply {
                put("id", friend.id)
                put("name", friend.name)
                put("initials", friend.initials)
                put("cityHint", friend.cityHint)
                put("trustNote", friend.trustNote)
                put("isSharing", friend.isSharing)
                put("accentHex", friend.accentHex)
            })
        }
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_FRIENDS, array.toString())
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun readFriends(userId: String?): List<Friend> {
        val cachedUser = prefs.getString(KEY_USER_ID, null)
        if (cachedUser.isNullOrBlank() || cachedUser != userId) return emptyList()
        val array = runCatching { JSONArray(prefs.getString(KEY_FRIENDS, "[]") ?: "[]") }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name", "LINKO User")
                add(
                    Friend(
                        id = id,
                        name = name,
                        initials = item.optString("initials", initials(name)),
                        cityHint = item.optString("cityHint", ""),
                        trustNote = item.optString("trustNote", "REAL LINKO USER"),
                        isSharing = item.optBoolean("isSharing", false),
                        accentHex = item.optLong("accentHex", 0xFF4C8DFF),
                    ),
                )
            }
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun initials(name: String): String = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "L" }

    companion object {
        private const val PREFS = "linko_local_cache"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_LINKO_ID = "linko_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_FRIENDS = "friends"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
