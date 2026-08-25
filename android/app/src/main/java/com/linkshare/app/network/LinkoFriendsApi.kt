package com.linkshare.app.network

import com.linkshare.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Real friend discovery backed by the deployed Supabase Edge Function and Realtime-enabled database. */
class LinkoFriendsApi(private val accessTokenProvider: () -> String?) {
    private val baseUrl = "${BuildConfig.LINKO_SUPABASE_URL}/functions/v1/linko-friends"

    suspend fun ensureProfile(displayName: String?, username: String? = null): JSONObject = withContext(Dispatchers.IO) {
        post("/profile", JSONObject().apply {
            put("displayName", displayName ?: "LINKO User")
            username?.trim()?.takeIf { it.isNotBlank() }?.let { put("username", it) }
        })
    }

    suspend fun profile(): JSONObject = withContext(Dispatchers.IO) { get("/profile") }

    suspend fun search(query: String): List<FriendSearchResult> = withContext(Dispatchers.IO) {
        val json = get("/search?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8"))
        val array = json.optJSONArray("results") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(parseFriend(item))
            }
        }
    }

    suspend fun friends(): JSONObject = withContext(Dispatchers.IO) {
        val friendsJson = get("/friends")
        val friends = friendsJson.optJSONArray("friends") ?: JSONArray()
        val requestsJson = get("/requests")
        val requests = requestsJson.optJSONArray("requests") ?: JSONArray()
        val existingIds = mutableSetOf<String>()
        for (i in 0 until friends.length()) {
            friends.optJSONObject(i)?.optString("user_id")?.takeIf { it.isNotBlank() }?.let(existingIds::add)
        }
        for (i in 0 until requests.length()) {
            val request = requests.optJSONObject(i) ?: continue
            if (request.optString("status") != "accepted") continue
            val profile = request.optJSONObject("profile") ?: continue
            val userId = profile.optString("user_id")
            if (userId.isBlank() || !existingIds.add(userId)) continue
            friends.put(JSONObject(profile.toString()).apply { put("relationship_status", "friend") })
        }
        JSONObject(friendsJson.toString()).put("friends", friends)
    }

    suspend fun sendRequest(receiverUserId: String): JSONObject = withContext(Dispatchers.IO) {
        post("/requests", JSONObject().put("receiverUserId", receiverUserId))
    }

    suspend fun requests(): JSONObject = withContext(Dispatchers.IO) { get("/requests") }

    suspend fun respond(requestId: String, accepted: Boolean): JSONObject = withContext(Dispatchers.IO) {
        post("/requests/respond", JSONObject().put("requestId", requestId).put("status", if (accepted) "accepted" else "declined"))
    }

    suspend fun removeFriend(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val current = requests()
            val array = current.optJSONArray("requests") ?: JSONArray()
            var requestId: String? = null
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("status") != "accepted") continue
                val profile = item.optJSONObject("profile") ?: continue
                if (profile.optString("user_id") == userId) {
                    requestId = item.optString("id").takeIf { it.isNotBlank() }
                    break
                }
            }
            val id = requestId
            if (id.isNullOrBlank()) {
                false
            } else {
                deleteFriendRequestRow(id)
                true
            }
        }
    }

    private fun parseFriend(item: JSONObject): FriendSearchResult = FriendSearchResult(
        userId = item.optString("user_id"),
        linkoId = item.optString("linko_id"),
        displayName = item.optString("display_name"),
        deviceId = item.optString("device_id").takeIf { it.isNotBlank() },
        deviceName = item.optString("device_name").takeIf { it.isNotBlank() },
        isSharing = item.optBoolean("is_sharing", false),
        relationshipStatus = item.optString("relationship_status", "none"),
        requestId = item.optString("request_id").takeIf { it.isNotBlank() && it != "null" },
        username = item.optString("username").trim().removePrefix("@").takeIf { it.isNotBlank() },
    )

    private fun get(path: String): JSONObject = request("GET", path, null)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private fun deleteFriendRequestRow(requestId: String) {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val url = "${BuildConfig.LINKO_SUPABASE_URL}/rest/v1/friend_requests?id=eq.${java.net.URLEncoder.encode(requestId, "UTF-8")}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw LinkoNetworkException(JSONObject(text.ifBlank { "{}" }).optString("message", "http_$status"), status)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw LinkoNetworkException(JSONObject(text.ifBlank { "{}" }).optString("message", "http_$status"), status)
            }
            return JSONObject(text.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }
}
