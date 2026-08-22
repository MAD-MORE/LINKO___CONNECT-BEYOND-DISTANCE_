package com.linkshare.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Authenticated client for the LINKO control-plane signaling API. */
class LinkoSignalingClient(
    private val baseUrl: String,
    private val accessToken: String,
) {
    suspend fun requestTicket(sessionId: String): SignalingTicket = withContext(Dispatchers.IO) {
        val response = request("POST", "/v1/sessions/$sessionId/signaling/ticket")
        SignalingTicket(
            sessionId = response.getString("sessionId"),
            deviceId = response.getString("deviceId"),
            expiresAtEpochMillis = response.getLong("expiresAt")
        )
    }

    suspend fun send(sessionId: String, kind: SignalKind, payload: JSONObject): SignalEnvelope = withContext(Dispatchers.IO) {
        val body = JSONObject().put("kind", kind.wireValue).put("payload", payload)
        val response = request("POST", "/v1/sessions/$sessionId/signaling", body)
        SignalEnvelope.fromJson(response)
    }

    suspend fun receive(sessionId: String): List<SignalEnvelope> = withContext(Dispatchers.IO) {
        val response = request("GET", "/v1/sessions/$sessionId/signaling")
        val result = response.getJSONArray("signals")
        buildList(result.length()) { for (i in 0 until result.length()) add(SignalEnvelope.fromJson(result.getJSONObject(i))) }
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) throw LinkoSignalingException(status, json.optString("error", "control_plane_error"))
            return json
        } finally {
            connection.disconnect()
        }
    }
}

enum class SignalKind(val wireValue: String) { OFFER("offer"), ANSWER("answer"), ICE("ice") }

data class SignalingTicket(val sessionId: String, val deviceId: String, val expiresAtEpochMillis: Long)

data class SignalEnvelope(
    val id: String,
    val sessionId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val kind: SignalKind,
    val payload: JSONObject,
    val createdAtEpochMillis: Long,
) {
    companion object {
        fun fromJson(json: JSONObject) = SignalEnvelope(
            id = json.getString("id"),
            sessionId = json.getString("sessionId"),
            senderDeviceId = json.getString("senderDeviceId"),
            recipientDeviceId = json.getString("recipientDeviceId"),
            kind = when (json.getString("kind")) { "offer" -> SignalKind.OFFER; "answer" -> SignalKind.ANSWER; else -> SignalKind.ICE },
            payload = json.optJSONObject("payload") ?: JSONObject(),
            createdAtEpochMillis = json.getLong("createdAt"),
        )
    }
}

class LinkoSignalingException(val statusCode: Int, message: String) : RuntimeException(message)
