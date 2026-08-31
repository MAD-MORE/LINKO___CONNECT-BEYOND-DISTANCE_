package com.linkshare.app.network

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** LINKO control-plane client. Production control operations use authenticated Supabase RPCs. */
class LinkoControlPlaneApi(
    private val baseUrl: String,
    private val accessTokenProvider: () -> String?,
    private val deviceIdProvider: () -> String? = { null },
) : LinkShareApi {
    private val isFriendFunction = baseUrl.contains("/functions/v1/linko-friends")

    override suspend fun getFriends(): List<Friend> = withContext(Dispatchers.IO) {
        val json = requestHttp("GET", "/friends")
        parseFriends(json.optJSONArray("friends") ?: JSONArray())
    }

    suspend fun searchUsers(query: String): List<Friend> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) emptyList() else {
            val encoded = java.net.URLEncoder.encode(clean, "UTF-8")
            val json = requestHttp("GET", "/search?q=$encoded")
            parseFriends(json.optJSONArray("results") ?: JSONArray())
        }
    }

    suspend fun sendFriendRequest(userId: String): Boolean = withContext(Dispatchers.IO) {
        requestHttp("POST", "/requests", JSONObject().put("receiverUserId", userId))
        true
    }

    override suspend fun watchIncomingRequests(onRequest: (IncomingRequest) -> Unit) {
        getPendingProviderRequests().forEach { onRequest(it.toIncomingRequest()) }
    }

    suspend fun providerDeviceForUser(friendUserId: String): JSONObject = withContext(Dispatchers.IO) {
        val clean = friendUserId.trim()
        require(clean.isNotBlank()) { "friend_user_required" }
        rpc("linko_provider_for_user", JSONObject().put("p_friend_user_id", clean))
    }

    override suspend fun requestAccess(hostId: String): SignalingSession = withContext(Dispatchers.IO) {
        val receiverDeviceId = requireConfiguredDeviceId()
        val session = rpc(
            "linko_create_session",
            JSONObject().put("p_receiver_device_id", receiverDeviceId).put("p_provider_device_id", hostId),
        )
        SignalingSession(
            session.getString("id"),
            session.optString("providerPublicKey", ""),
            session.optString("relayUrl").ifBlank { null },
            session.optLong("expiresAt", 0L) / 1000L,
        )
    }

    override suspend fun approveRequest(requestId: String): HostSession = withContext(Dispatchers.IO) {
        val session = rpc(
            "linko_transition_session",
            JSONObject().put("p_session_id", requestId).put("p_state", "approved"),
        )
        HostSession(session.getString("id"), session.optString("receiverPublicKey", ""), session.optLong("expiresAt", 0L) / 1000L)
    }

    override suspend fun denyRequest(requestId: String) = withContext(Dispatchers.IO) {
        rpc("linko_transition_session", JSONObject().put("p_session_id", requestId).put("p_state", "denied"))
        Unit
    }

    suspend fun getPendingProviderRequests(): List<ProviderRequest> = withContext(Dispatchers.IO) {
        val json = rpc("linko_pending_provider_requests")
        val array = json.optJSONArray("requests") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(ProviderRequest(item.getString("id"), item.getString("receiverDeviceId"), item.optString("state", "requested"), item.optLong("expiresAt", 0L)))
            }
        }
    }

    suspend fun markPresence() = withContext(Dispatchers.IO) {
        rpc("linko_mark_presence", JSONObject().put("p_device_id", requireConfiguredDeviceId()))
    }

    suspend fun health(): JSONObject = withContext(Dispatchers.IO) {
        rpc("linko_control_health")
    }

    suspend fun transition(sessionId: String, state: String): JSONObject = withContext(Dispatchers.IO) {
        rpc("linko_transition_session", JSONObject().put("p_session_id", sessionId).put("p_state", state))
    }

    suspend fun tunnelConfig(sessionId: String): JSONObject = withContext(Dispatchers.IO) {
        rpc("linko_tunnel_config", JSONObject().put("p_session_id", sessionId))
    }

    private fun parseFriends(array: JSONArray): List<Friend> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("display_name", "LINKO User").ifBlank { "LINKO User" }
            val linkoId = item.optString("linko_id", "")
            add(Friend(item.optString("user_id", linkoId), name, initials(name), linkoId, "REAL LINKO USER", false, 0xFF4C8DFF))
        }
    }

    private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "L" }
    private fun requireConfiguredDeviceId(): String = deviceIdProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_auth_required")

    private fun rpc(function: String, body: JSONObject? = null, authenticated: Boolean = true): JSONObject {
        if (!baseUrl.startsWith("https://")) throw LinkoNetworkException("control_plane_https_required")
        val token = if (authenticated) accessTokenProvider()?.takeIf { it.isNotBlank() } else null
        if (authenticated && token == null) throw LinkoNetworkException("device_auth_required")
        val url = URL(baseUrl.trimEnd('/') + "/rest/v1/rpc/" + function)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", com.linkshare.app.BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            connection.outputStream.use { it.write((body?.toString() ?: "{}").toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrNull()
                val message = error?.optString("message").orEmpty().ifBlank { error?.optString("error").orEmpty() }.ifBlank { "http_$status" }
                throw LinkoNetworkException(message, status)
            }
            JSONObject(text.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }

    private fun requestHttp(method: String, path: String, body: JSONObject? = null): JSONObject {
        if (!isFriendFunction) throw LinkoNetworkException("unsupported_control_plane_http_path")
        if (!baseUrl.startsWith("https://")) throw LinkoNetworkException("control_plane_https_required")
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", com.linkshare.app.BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            body?.let { connection.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw LinkoNetworkException("http_$status", status)
            JSONObject(text.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }
}

data class ProviderRequest(val id: String, val receiverDeviceId: String, val state: String, val expiresAt: Long) {
    fun toIncomingRequest() = IncomingRequest(id, "LINKO friend", "L", receiverDeviceId, "REMOTE", "NOW")
}

class LinkoNetworkException(message: String, val statusCode: Int? = null) : RuntimeException(message)
