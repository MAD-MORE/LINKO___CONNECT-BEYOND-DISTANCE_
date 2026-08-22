package com.linkshare.app.network

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Real HTTP implementation of the Android control-plane contract. */
class LinkoControlPlaneApi(
    private val baseUrl: String,
    private val accessTokenProvider: () -> String?,
) : LinkShareApi {
    override suspend fun getFriends(): List<Friend> = withContext(Dispatchers.IO) {
        // Friend discovery is intentionally backend-owned. Until the friends
        // endpoint is exposed, return an empty authoritative result rather
        // than silently inventing local friends.
        emptyList()
    }

    override suspend fun watchIncomingRequests(onRequest: (IncomingRequest) -> Unit) {
        // Incoming requests will be delivered by the signaling/event transport.
        // Polling is intentionally not substituted for a missing event API.
    }

    override suspend fun requestAccess(hostId: String): SignalingSession = withContext(Dispatchers.IO) {
        val receiverDeviceId = requireConfiguredDeviceId()
        val session = request("POST", "/v1/sessions", JSONObject()
            .put("receiverDeviceId", receiverDeviceId)
            .put("providerDeviceId", hostId))
        SignalingSession(
            sessionId = session.getString("id"),
            hostPublicKey = session.optString("providerPublicKey", ""),
            relayUrl = session.optString("relayUrl").ifBlank { null },
            expiresAtEpochSeconds = session.optLong("expiresAt", 0L) / 1000L
        )
    }

    override suspend fun approveRequest(requestId: String): HostSession = withContext(Dispatchers.IO) {
        val session = request("POST", "/v1/sessions/$requestId/transition", JSONObject().put("state", "approved"))
        HostSession(
            sessionId = session.getString("id"),
            clientPublicKey = session.optString("receiverPublicKey", ""),
            allowedUntilEpochSeconds = session.optLong("expiresAt", 0L) / 1000L
        )
    }

    override suspend fun denyRequest(requestId: String) {
        withContext(Dispatchers.IO) {
            request("POST", "/v1/sessions/$requestId/transition", JSONObject().put("state", "denied"))
        }
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

    private fun requireConfiguredDeviceId(): String =
        accessTokenProvider()?.substringBefore(':')?.takeIf { it.isNotBlank() }
            ?: throw LinkoNetworkException("device_auth_required")

    private fun request(method: String, path: String, body: JSONObject? = null, authenticated: Boolean = true): JSONObject {
        require(baseUrl.startsWith("https://")) { "control_plane_https_required" }
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
            body?.let { payload -> connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) throw LinkoNetworkException(json.optString("error", "http_$status"), status)
            return json
        } finally {
            connection.disconnect()
        }
    }
}

class LinkoNetworkException(message: String, val statusCode: Int? = null) : RuntimeException(message)
