package com.linkshare.app.data

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpSignalingRepository(
    private val baseUrl: String,
    private val tokenProvider: () -> String
) {
    suspend fun requestHostAccess(friend: Friend): String = withContext(Dispatchers.IO) {
        val response = post("/v1/connections/request", JSONObject().apply {
            put("providerId", friend.id)
        })
        response.getString("id")
    }

    suspend fun getRequest(requestId: String): JSONObject = withContext(Dispatchers.IO) {
        get("/v1/connections/$requestId")
    }

    suspend fun negotiate(sessionId: String, type: String, payload: String): JSONObject = withContext(Dispatchers.IO) {
        post("/v1/sessions/$sessionId/negotiate", JSONObject().apply {
            put("type", type)
            put("payload", payload)
        })
    }

    suspend fun closeSession(sessionId: String) = withContext(Dispatchers.IO) {
        request("DELETE", "/v1/sessions/$sessionId", null)
    }

    private fun get(path: String): JSONObject = request("GET", path, null)

    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${tokenProvider()}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes ->
            connection.outputStream.use { it.write(bytes) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("LINKO signaling HTTP $code: $text")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}