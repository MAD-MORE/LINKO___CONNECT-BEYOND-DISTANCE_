package com.linkshare.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Real friend discovery backed by the deployed Supabase Edge Function. */
class LinkoFriendsApi(private val accessTokenProvider: () -> String?) {
    private val baseUrl = "https://pbnvssbtshvesqwhckfa.supabase.co/functions/v1/linko-friends"

    suspend fun ensureProfile(displayName: String?): JSONObject = withContext(Dispatchers.IO) {
        post("/profile", JSONObject().put("displayName", displayName ?: "LINKO User"))
    }

    suspend fun search(query: String): List<FriendSearchResult> = withContext(Dispatchers.IO) {
        val json = get("/search?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8"))
        val array = json.optJSONArray("results") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(FriendSearchResult(
                    userId = item.optString("user_id"),
                    linkoId = item.optString("linko_id"),
                    displayName = item.optString("display_name"),
                    deviceId = item.optString("device_id").takeIf { it.isNotBlank() },
                    deviceName = item.optString("device_name").takeIf { it.isNotBlank() },
                    isSharing = item.optBoolean("is_sharing", false),
                    relationshipStatus = item.optString("relationship_status", "none"),
                    requestId = item.optString("request_id").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }
    }

    /**
     * Returns the current user's friends from the service. As a compatibility
     * guard, accepted requests are merged into the friend list as well. This
     * makes friendship symmetric for both the sender and accepter even if an
     * older deployed Edge Function only materializes the accepted friendship
     * for one side in its /friends response.
     */
    suspend fun friends(): JSONObject = withContext(Dispatchers.IO) {
        val friendsJson = get("/friends")
        val friends = friendsJson.optJSONArray("friends") ?: JSONArray()
        val requestsJson = get("/requests")
        val requests = requestsJson.optJSONArray("requests") ?: JSONArray()

        val existingIds = mutableSetOf<String>()
        for (i in 0 until friends.length()) {
            friends.optJSONObject(i)?.let { item ->
                item.optString("user_id").takeIf { it.isNotBlank() }?.let(existingIds::add)
            }
        }

        for (i in 0 until requests.length()) {
            val request = requests.optJSONObject(i) ?: continue
            if (request.optString("status") != "accepted") continue

            val profile = request.optJSONObject("profile") ?: continue
            val userId = profile.optString("user_id")
            if (userId.isBlank() || !existingIds.add(userId)) continue

            friends.put(JSONObject(profile.toString()).apply {
                put("relationship_status", "friend")
            })
        }

        return@withContext JSONObject(friendsJson.toString()).put("friends", friends)
    }

    /** Idempotent friend request operation. */
    suspend fun sendRequest(receiverUserId: String): JSONObject = withContext(Dispatchers.IO) {
        post("/requests", JSONObject().put("receiverUserId", receiverUserId))
    }

    suspend fun requests(): JSONObject = withContext(Dispatchers.IO) { get("/requests") }
    suspend fun respond(requestId: String, accepted: Boolean): JSONObject = withContext(Dispatchers.IO) {
        post("/requests/respond", JSONObject().put("requestId", requestId).put("status", if (accepted) "accepted" else "declined"))
    }

    private fun get(path: String): JSONObject = request("GET", path, null)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { payload -> connection.outputStream.use { stream -> stream.write(payload.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (status !in 200..299) throw LinkoNetworkException(json.optString("error", "http_$status"), status)
            return json
        } finally { connection.disconnect() }
    }
}

data class FriendSearchResult(
    val userId: String,
    val linkoId: String,
    val displayName: String,
    val deviceId: String?,
    val deviceName: String?,
    val isSharing: Boolean,
    val relationshipStatus: String = "none",
    val requestId: String? = null,
)
