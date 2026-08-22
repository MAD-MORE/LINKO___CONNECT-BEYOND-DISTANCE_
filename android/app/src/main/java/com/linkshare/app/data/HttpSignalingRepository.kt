package com.linkshare.app.data

import com.linkshare.app.model.Friend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpSignalingRepository(
    private val baseUrl: String,
    private val tokenProvider: () -> String
) {
    suspend fun requestHostAccess(receiverId: String, friend: Friend): String = withContext(Dispatchers.IO) {
        post("/v1/connections/request", JSONObject().apply {
            put("receiverId", receiverId)
            put("providerId", friend.id)
        }).getString("id")
    }

    suspend fun getRequest(requestId: String): JSONObject = withContext(Dispatchers.IO) { get("/v1/connections/$requestId") }

    suspend fun listPendingRequests(providerId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val response = get("/v1/providers/$providerId/requests/pending")
        val items = response.optJSONArray("items") ?: return@withContext emptyList()
        buildList { for (index in 0 until items.length()) add(items.getJSONObject(index)) }
    }

    suspend fun approveRequest(requestId: String, providerId: String): JSONObject = withContext(Dispatchers.IO) {
        post("/v1/connections/$requestId/approve", JSONObject().apply { put("providerId", providerId) })
    }

    suspend fun denyRequest(requestId: String, providerId: String): JSONObject = withContext(Dispatchers.IO) {
        post("/v1/connections/$requestId/deny", JSONObject().apply { put("providerId", providerId) })
    }

    suspend fun createSession(requestId: String): JSONObject = withContext(Dispatchers.IO) { post("/v1/connections/$requestId/session", JSONObject()) }

    suspend fun publishSessionPublicKey(sessionId: String, role: String, publicKey: String): JSONObject = withContext(Dispatchers.IO) {
        post("/v1/sessions/$sessionId/key", JSONObject().apply {
            put("role", role)
            put("publicKey", publicKey)
        })
    }

    suspend fun getSessionPublicKeys(sessionId: String): JSONObject = withContext(Dispatchers.IO) {
        get("/v1/sessions/$sessionId/key")
    }

    suspend fun negotiate(sessionId: String, type: String, payload: String): JSONObject = withContext(Dispatchers.IO) {
        post("/v1/sessions/$sessionId/negotiate", JSONObject().apply {
            put("type", type)
            put("payload", payload)
        })
    }

    suspend fun closeSession(sessionId: String) = withContext(Dispatchers.IO) { request("DELETE", "/v1/sessions/$sessionId", null) }

    private fun get(path: String): JSONObject = request("GET", path, null)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            val token = tokenProvider()
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes -> connection.outputStream.use { it.write(bytes) } }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("LINKO signaling HTTP $code: $text")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
