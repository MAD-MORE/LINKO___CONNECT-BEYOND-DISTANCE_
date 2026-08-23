package com.linkshare.app.network

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Real HTTP implementation of the Android control-plane contract. */
class LinkoControlPlaneApi(
    private val baseUrl: String,
    private val accessTokenProvider: () -> String?,
) : LinkShareApi {
    override suspend fun getFriends(): List<Friend> = emptyList()

    override suspend fun watchIncomingRequests(onRequest: (IncomingRequest) -> Unit) {
        // Kept for the shared interface; ProviderRuntime uses the authenticated
        // provider request endpoint below so background listening is explicit.
        getPendingProviderRequests().forEach { onRequest(it.toIncomingRequest()) }
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
        withContext(Dispatchers.IO) { request("POST", "/v1/sessions/$requestId/transition", JSONObject().put("state", "denied")) }
    }

    suspend fun getPendingProviderRequests(): List<ProviderRequest> = withContext(Dispatchers.IO) {
        val json = request("GET", "/v1/provider/requests")
        val array = json.optJSONArray("requests") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(ProviderRequest(
                    id = item.getString("id"),
                    receiverDeviceId = item.getString("receiverDeviceId"),
                    state = item.optString("state", "requested"),
                    expiresAt = item.optLong("expiresAt", 0L),
                ))
            }
        }
    }

    suspend fun markPresence() = withContext(Dispatchers.IO) {
        request("POST", "/v1/devices/presence", JSONObject())
    }

    suspend fun health(): JSONObject = withContext(Dispatchers.IO) { request("GET", "/health", authenticated = false) }
    suspend fun transition(sessionId: String, state: String): JSONObject = withContext(Dispatchers.IO) { request("POST", "/v1/sessions/$sessionId/transition", JSONObject().put("state", state)) }
    suspend fun tunnelConfig(sessionId: String): JSONObject = withContext(Dispatchers.IO) { request("GET", "/v1/sessions/$sessionId/tunnel") }

    private fun requireConfiguredDeviceId(): String = accessTokenProvider()?.substringBefore(':')?.takeIf { it.isNotBlank() }
        ?: throw LinkoNetworkException("device_auth_required")

    private fun request(method: String, path: String, body: JSONObject? = null, authenticated: Boolean = true): JSONObject {
        require(baseUrl.startsWith("https://")) { "control_plane_https_required" }
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_auth_required")
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        try {
            body?.let { payload -> connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) throw LinkoNetworkException(json.optString("error", "http_$status"), status)
            return json
        } finally { connection.disconnect() }
    }
}

data class ProviderRequest(val id: String, val receiverDeviceId: String, val state: String, val expiresAt: Long) {
    fun toIncomingRequest() = IncomingRequest(id, "LINKO friend", "L", receiverDeviceId, "REMOTE", "NOW")
}
class LinkoNetworkException(message: String, val statusCode: Int? = null) : RuntimeException(message)
