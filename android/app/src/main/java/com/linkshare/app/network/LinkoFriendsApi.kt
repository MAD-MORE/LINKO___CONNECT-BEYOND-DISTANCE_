package com.linkshare.app.network

import com.linkshare.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real friend discovery and request management backed by Supabase RPCs
 * with Edge Function path contract compatibility.
 */
class LinkoFriendsApi(private val accessTokenProvider: () -> String?) {
    private val edgeBaseUrl = "${BuildConfig.LINKO_SUPABASE_URL}/functions/v1/linko-friends"
    private val rpcBaseUrl = "${BuildConfig.LINKO_SUPABASE_URL}/rest/v1/rpc"

    suspend fun ensureProfile(displayName: String?, username: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val name = displayName ?: username ?: "LINKO User"
        runCatching {
            callRpc("linko_get_or_create_profile", JSONObject().put("p_display_name", name))
        }.getOrElse {
            post("/profile", JSONObject().apply {
                put("displayName", name)
                username?.trim()?.takeIf { it.isNotBlank() }?.let { put("username", it) }
            })
        }
    }

    suspend fun profile(): JSONObject = withContext(Dispatchers.IO) {
        runCatching {
            callRpc("linko_get_or_create_profile", JSONObject())
        }.getOrElse {
            get("/profile")
        }
    }

    suspend fun search(query: String): List<FriendSearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()

        runCatching {
            val response = callRpcRaw("linko_search_friends", JSONObject().put("p_query", clean))
            val array = if (response.startsWith("[")) JSONArray(response) else JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(parseFriend(item))
                }
            }
        }.getOrElse {
            val json = get("/search?q=" + java.net.URLEncoder.encode(clean, "UTF-8"))
            val array = json.optJSONArray("results") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(parseFriend(item))
                }
            }
        }
    }

    suspend fun friends(): JSONObject = withContext(Dispatchers.IO) {
        runCatching {
            callRpc("linko_get_friends", JSONObject())
        }.getOrElse {
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
    }

    suspend fun requests(): JSONObject = withContext(Dispatchers.IO) {
        runCatching {
            callRpc("linko_get_friend_requests", JSONObject())
        }.getOrElse {
            get("/requests")
        }
    }

    suspend fun sendRequest(receiverUserId: String): JSONObject = withContext(Dispatchers.IO) {
        val clean = receiverUserId.trim()
        runCatching {
            callRpc("linko_send_friend_request", JSONObject().put("p_receiver_user_id", clean))
        }.getOrElse {
            post("/requests", JSONObject().put("receiverUserId", clean))
        }
    }

    suspend fun respond(requestId: String, accepted: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val status = if (accepted) "accepted" else "declined"
        runCatching {
            callRpc("linko_respond_friend_request", JSONObject().put("p_request_id", requestId).put("p_status", status))
        }.getOrElse {
            post("/requests/respond", JSONObject().put("requestId", requestId).put("status", status))
        }
    }

    suspend fun removeFriend(userId: String): Boolean = withContext(Dispatchers.IO) {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: return@withContext false
        val clean = userId.trim()
        val url = "${BuildConfig.LINKO_SUPABASE_URL}/rest/v1/friend_requests?or=(and(sender_id.eq.$clean),and(receiver_id.eq.$clean))"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFriend(item: JSONObject): FriendSearchResult = FriendSearchResult(
        userId = item.optString("user_id"),
        linkoId = item.optString("linko_id"),
        displayName = item.optString("display_name").ifBlank { "LINKO User" },
        deviceId = item.optString("device_id").takeIf { it.isNotBlank() },
        deviceName = item.optString("device_name").takeIf { it.isNotBlank() },
        isSharing = item.optBoolean("is_sharing", false),
        relationshipStatus = item.optString("relationship_status", "none"),
        requestId = item.optString("request_id").takeIf { it.isNotBlank() && it != "null" },
        username = item.optString("username").trim().removePrefix("@").takeIf { it.isNotBlank() },
    )

    private fun get(path: String): JSONObject = requestHttp("GET", edgeBaseUrl + path, null)
    private fun post(path: String, body: JSONObject): JSONObject = requestHttp("POST", edgeBaseUrl + path, body)

    private fun callRpc(functionName: String, body: JSONObject): JSONObject {
        val raw = callRpcRaw(functionName, body)
        return when {
            raw.startsWith("{") -> JSONObject(raw)
            raw.startsWith("[") -> JSONObject().put("results", JSONArray(raw))
            else -> JSONObject()
        }
    }

    private fun callRpcRaw(functionName: String, body: JSONObject): String {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val endpoint = "$rpcBaseUrl/$functionName"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { out ->
                out.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty().trim()
            if (status !in 200..299) {
                val errorMsg = runCatching { JSONObject(text).optString("message", "http_$status") }.getOrDefault("http_$status")
                throw LinkoNetworkException(errorMsg, status)
            }
            return text.ifBlank { "{}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestHttp(method: String, fullUrl: String, body: JSONObject?): JSONObject {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
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
