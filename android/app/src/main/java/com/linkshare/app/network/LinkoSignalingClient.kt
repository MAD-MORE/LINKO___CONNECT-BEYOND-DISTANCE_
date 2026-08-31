package com.linkshare.app.network

import com.linkshare.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Authenticated client for Supabase-backed signaling. */
class LinkoSignalingClient(
    private val baseUrl: String = BuildConfig.LINKO_SUPABASE_URL,
    private val accessToken: String,
) {
    suspend fun requestTicket(sessionId: String): SignalingTicket = withContext(Dispatchers.IO) {
        val response = rpc("linko_signaling_ticket", JSONObject().put("p_session_id", sessionId))
        SignalingTicket(
            sessionId = response.optString("sessionId", sessionId),
            deviceId = response.optString("deviceId"),
            expiresAtEpochMillis = response.optLong("expiresAt", System.currentTimeMillis() + 300_000L)
        )
    }

    suspend fun send(sessionId: String, kind: SignalKind, payload: JSONObject): SignalEnvelope = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_session_id", sessionId)
            .put("p_kind", kind.wireValue)
            .put("p_payload", payload)
        val response = rpc("linko_send_signal", body)
        SignalEnvelope.fromJson(response)
    }

    suspend fun receive(sessionId: String): List<SignalEnvelope> = withContext(Dispatchers.IO) {
        val response = rpc("linko_receive_signals", JSONObject().put("p_session_id", sessionId))
        val result = response.optJSONArray("signals") ?: org.json.JSONArray()
        buildList(result.length()) { for (i in 0 until result.length()) add(SignalEnvelope.fromJson(result.getJSONObject(i))) }
    }

    private fun rpc(function: String, body: JSONObject): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + "/rest/v1/rpc/" + function).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { out -> out.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) throw LinkoSignalingException(status, json.optString("message", json.optString("error", "supabase_rpc_error")))
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
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            sessionId = json.optString("sessionId"),
            senderDeviceId = json.optString("senderDeviceId"),
            recipientDeviceId = json.optString("recipientDeviceId"),
            kind = when (json.optString("kind")) { "offer" -> SignalKind.OFFER; "answer" -> SignalKind.ANSWER; else -> SignalKind.ICE },
            payload = json.optJSONObject("payload") ?: JSONObject(),
            createdAtEpochMillis = json.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

class LinkoSignalingException(val statusCode: Int, message: String) : RuntimeException(message)
