package com.linkshare.app.network

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LinkoControlPlaneApi(
    private val baseUrl: String,
    private val accessTokenProvider: () -> String?,
    private val deviceIdProvider: () -> String? = { null },
) : LinkShareApi {
    override suspend fun getFriends(): List<Friend> = withContext(Dispatchers.IO) {
        val json = request("GET", "/friends")
        parseFriends(json.optJSONArray("friends") ?: JSONArray())
    }

    suspend fun searchUsers(query: String): List<Friend> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(clean, "UTF-8")
        val json = request("GET", "/search?q=$encoded")
        parseFriends(json.optJSONArray("results") ?: JSONArray())
    }

    suspend fun sendFriendRequest(userId: String): Boolean = withContext(Dispatchers.IO) {
        request("POST", "/requests", JSONObject().put("receiverUserId", userId))
        true
    }

    override suspend fun watchIncomingRequests(onRequest: (IncomingRequest) -> Unit) {
        getPendingProviderRequests().forEach { onRequest(it.toIncomingRequest()) }
    }

    override suspend fun requestAccess(hostId: String): SignalingSession = withContext(Dispatchers.IO) {
        val receiverDeviceId = requireConfiguredDeviceId()
        val session = request(
            "POST", "/v1/sessions",
            JSONObject().put("receiverDeviceId", receiverDeviceId).put("providerDeviceId", hostId),
        )
        SignalingSession(
            session.getString("id"),
            session.optString("providerPublicKey", ""),
            session.optString("relayUrl").ifBlank { null },
            session.optLong("expiresAt", 0L) / 1000L,
        )
    }

    override suspend fun approveRequest(requestId: String): HostSession = withContext(Dispatchers.IO) {
        val session = request(
            "POST", "/v1/sessions/$requestId/transition",
            JSONObject().put("state", "approved"),
        )
        HostSession(
            session.getString("id"),
            session.optString("receiverPublicKey", ""),
            session.optLong("expiresAt", 0L) / 1000L,
        )
    }

    override suspend fun denyRequest(requestId: String) = withContext(Dispatchers.IO) {
        request("POST", "/v1/sessions/$requestId/transition", JSONObject().put("state", "denied"))
        Unit
    }

    suspend fun getPendingProviderRequests(): List<ProviderRequest> = withContext(Dispatchers.IO) {
        val json = request("GET", "/v1/provider/requests")
        val array = json.optJSONArray("requests") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ProviderRequest(
                        item.getString("id"),
                        item.getString("receiverDeviceId"),
                        item.optString("state", "requested"),
                        item.optLong("expiresAt", 0L),
                    ),
                )
            }
        }
    }

    suspend fun markPresence() = withContext(Dispatchers.IO) {
        request("POST", "/v1/devices/presence", JSONObject())
    }

    suspend fun health(): JSONObject = withContext(Dispatchers.IO) {
        request("GET", "/health", authenticated = false)
    }

    suspend fun transition(sessionId: String, state: String): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/v1/sessions/$sessionId/transition", JSONObject().put("state", state))
    }

    suspend fun tunnelConfig(sessionId: String): JSONObject = withContext(Dispatchers.IO) {
        request("GET", "/v1/sessions/$sessionId/tunnel")
    }

    private fun parseFriends(array: JSONArray): List<Friend> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("display_name", "LINKO User").ifBlank { "LINKO User" }
            val linkoId = item.optString("linko_id", "")
            add(
                Friend(
                    item.optString("user_id", linkoId),
                    name,
                    initials(name),
                    linkoId,
                    "REAL LINKO USER",
                    false,
                    0xFF4C8DFF,
                ),
            )
        }
    }

    private fun initials(name: String): String = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "L" }

    private fun requireConfiguredDeviceId(): String =
        deviceIdProvider()?.takeIf { it.isNotBlank() }
            ?: throw LinkoNetworkException("device_auth_required")

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true,
    ): JSONObject {
        if (!baseUrl.startsWith("https://")) {
            throw LinkoNetworkException("control_plane_https_required")
        }

        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                val token = accessTokenProvider()?.takeIf { it.isNotBlank() }
                    ?: throw LinkoNetworkException("device_auth_required")
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            body?.let { payload ->
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                throw LinkoNetworkException(json.optString("error", "http_$status"), status)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}

data class ProviderRequest(
    val id: String,
    val receiverDeviceId: String,
    val state: String,
    val expiresAt: Long,
) {
    fun toIncomingRequest() = IncomingRequest(id, "LINKO friend", "L", receiverDeviceId, "REMOTE", "NOW")
}

class LinkoNetworkException(message: String, val statusCode: Int? = null) : RuntimeException(message)
