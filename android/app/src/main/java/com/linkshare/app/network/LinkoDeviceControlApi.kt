package com.linkshare.app.network

import android.content.Context
import android.os.Build
import android.util.Base64
import com.linkshare.app.BuildConfig
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoDeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Permanent LINKO control-plane client communicating directly with Supabase via authenticated PostgreSQL RPCs.
 * Supabase carries authentication, session state and signaling only; it is never a data-plane relay.
 */
class LinkoDeviceControlApi(
    private val context: Context,
    private val auth: LinkoAuth = LinkoAuth(context.applicationContext),
    private val identity: LinkoDeviceIdentity = LinkoDeviceIdentity(),
    private val baseUrl: String = BuildConfig.LINKO_SUPABASE_URL,
) {
    suspend fun ensureRegistered(): DeviceRegistration {
        return withContext(Dispatchers.IO) {
            val existingId = auth.currentDeviceId()
            val access = auth.currentAccessToken()?.takeIf { it.isNotBlank() }
            if (!existingId.isNullOrBlank() && access != null) {
                return@withContext DeviceRegistration(existingId, access, auth.currentUserId())
            }
            val token = access ?: run {
                val refreshed = auth.refreshSession()
                if (!refreshed.success) throw LinkoNetworkException("device_auth_required")
                auth.currentAccessToken()?.takeIf { it.isNotBlank() }
                    ?: throw LinkoNetworkException("device_auth_required")
            }
            val userId = auth.currentUserId()?.takeIf { it.isNotBlank() }
                ?: auth.refreshAccountIdentity().userId
                ?: throw LinkoNetworkException("auth_user_missing")

            val body = JSONObject()
                .put("p_public_key", identity.publicKeyBase64())
                .put("p_name", "LINKO ${Build.MANUFACTURER} ${Build.MODEL}".trim())
                .put("p_roles", JSONArray().put("provider").put("receiver"))

            val response = rpc("linko_register_device", body, token)
            val deviceId = response.optString("id").takeIf { it.isNotBlank() }
                ?: response.optString("device_id").takeIf { it.isNotBlank() }
                ?: throw LinkoNetworkException("invalid_device_registration_payload")
            auth.saveDeviceId(deviceId)
            DeviceRegistration(deviceId, token, userId)
        }
    }

    suspend fun touchPresence(): PresenceResult = withContext(Dispatchers.IO) {
        val deviceId = auth.currentDeviceId()
        val body = if (deviceId != null) JSONObject().put("p_device_id", deviceId) else JSONObject()
        val result = rpc("linko_mark_presence", body, authToken())
        PresenceResult(result.optString("deviceId", deviceId.orEmpty()), result.optLong("lastSeenAt", System.currentTimeMillis()))
    }

    suspend fun providerDeviceForUser(friendUserId: String): ProviderDevice = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_friend_user_id", friendUserId.trim())
        val json = rpc("linko_provider_for_user", body, authToken())
        val device = json.optJSONObject("device") ?: throw LinkoNetworkException("provider_not_available")
        ProviderDevice(device.optString("id"), json.optBoolean("online", false), json.optLong("lastSeenAt", 0L))
    }

    suspend fun requestSession(providerDeviceId: String): DeviceSession = withContext(Dispatchers.IO) {
        val receiver = auth.currentDeviceId()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_auth_required")
        val body = JSONObject()
            .put("p_receiver_device_id", receiver)
            .put("p_provider_device_id", providerDeviceId)
        val json = rpc("linko_create_session", body, authToken())
        DeviceSession(json.optString("id"), json.optString("state", "requested"), json.optLong("expiresAt", 0L))
    }

    suspend fun transition(sessionId: String, state: String): DeviceSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_session_id", sessionId)
            .put("p_state", state)
        val json = rpc("linko_transition_session", body, authToken())
        DeviceSession(json.optString("id", sessionId), json.optString("state", state), json.optLong("expiresAt", 0L))
    }

    suspend fun session(sessionId: String): DeviceSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_session_id", sessionId)
        val json = rpc("linko_get_session", body, authToken())
        DeviceSession(json.optString("id", sessionId), json.optString("state", "idle"), json.optLong("expiresAt", 0L))
    }

    suspend fun tunnelConfig(sessionId: String): TunnelConfig = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_session_id", sessionId)
        val json = rpc("linko_tunnel_config", body, authToken())
        val endpoint = json.optJSONObject("endpoint")
        val host = json.optString("host").ifBlank { endpoint?.optString("host").orEmpty() }
        val port = if (json.has("port") && !json.isNull("port")) json.optInt("port", -1) else endpoint?.optInt("port", -1) ?: -1
        val keyB64 = json.optString("key").trim()

        // Direct-P2P mode intentionally has no server endpoint. The endpoint is
        // discovered by STUN and exchanged through authenticated signaling.
        if (keyB64.isBlank()) throw LinkoNetworkException("invalid_tunnel_key")
        val key = runCatching { Base64.decode(keyB64, Base64.DEFAULT) }
            .getOrElse { throw LinkoNetworkException("invalid_tunnel_key") }
        if (key.size != 32) throw LinkoNetworkException("invalid_tunnel_key_length")

        val role = json.optString("role").trim().lowercase()
        if (role != "provider" && role != "receiver") throw LinkoNetworkException("invalid_tunnel_role")

        val expiresAt = json.optLong("expiresAt", 0L)
        if (expiresAt <= System.currentTimeMillis()) throw LinkoNetworkException("tunnel_config_expired")

        TunnelConfig(sessionId, host, port, key, role, expiresAt, transport = json.optString("transport", "direct_udp"), relay = json.optBoolean("relay", false))
    }

    suspend fun pendingProviderRequests(): List<ProviderRequest> = withContext(Dispatchers.IO) {
        val json = rpc("linko_pending_provider_requests", JSONObject(), authToken())
        val array = json.optJSONArray("requests") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(ProviderRequest(item.optString("id"), item.optString("receiverDeviceId"), item.optString("state", "requested"), item.optLong("expiresAt", 0L)))
            }
        }
    }

    private fun authToken(): String = auth.currentAccessToken()?.takeIf { it.isNotBlank() }
        ?: throw LinkoNetworkException("device_auth_required")

    private fun rpc(function: String, body: JSONObject = JSONObject(), token: String): JSONObject {
        require(baseUrl.startsWith("https://")) { "control_plane_https_required" }
        val targetUrl = baseUrl.trimEnd('/') + "/rest/v1/rpc/" + function
        val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val parsed = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrNull()
                val message = parsed?.optString("message").orEmpty()
                    .ifBlank { parsed?.optString("error").orEmpty() }
                    .ifBlank { "http_$status" }
                throw LinkoNetworkException(message, status)
            }
            if (text.trim().startsWith("{")) JSONObject(text) else JSONObject().put("result", text)
        } finally {
            connection.disconnect()
        }
    }
}

data class DeviceRegistration(val deviceId: String, val token: String, val userId: String?)
data class PresenceResult(val deviceId: String, val lastSeenAt: Long)
data class ProviderDevice(val deviceId: String, val online: Boolean, val lastSeenAt: Long)
data class DeviceSession(val id: String, val state: String, val expiresAt: Long)
data class TunnelConfig(
    val sessionId: String,
    val host: String,
    val port: Int,
    val key: ByteArray,
    val role: String,
    val expiresAt: Long,
    val transport: String = "direct_udp",
    val relay: Boolean = false,
)
