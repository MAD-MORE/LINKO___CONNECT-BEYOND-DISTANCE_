package com.linkshare.app.network

import android.content.Context
import android.os.Build
import com.linkshare.app.BuildConfig
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoDeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real LINKO device/control-plane client. The control plane is hosted by a Supabase Edge Function and authenticated with the user's Supabase JWT. */
class LinkoDeviceControlApi(
    private val context: Context,
    private val auth: LinkoAuth = LinkoAuth(context.applicationContext),
    private val identity: LinkoDeviceIdentity = LinkoDeviceIdentity(),
    private val baseUrl: String = LinkoRuntimeConfig.controlPlaneUrl,
) {
    suspend fun ensureRegistered(): DeviceRegistration = withContext(Dispatchers.IO) {
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
            .put("publicKey", identity.publicKeyBase64())
            .put("name", "LINKO ${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("roles", JSONArray().put("provider").put("receiver"))
        val response = request("POST", "/v1/devices/register", body, token)
        val device = response.optJSONObject("device") ?: throw LinkoNetworkException("device_registration_invalid")
        val deviceId = device.optString("id").takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_id_missing")
        // Keep the Supabase JWT in the existing session slot for backwards compatibility;
        // all requests below always prefer the current refreshed Supabase access token.
        auth.saveLinkoSession(deviceId, token, userId)
        DeviceRegistration(deviceId, token, userId)
    }

    suspend fun touchPresence(): PresenceResult = withContext(Dispatchers.IO) {
        val result = request("POST", "/v1/devices/presence", JSONObject(), deviceToken())
        PresenceResult(result.optString("deviceId"), result.optLong("lastSeenAt", 0L))
    }

    suspend fun providerDeviceForUser(friendUserId: String): ProviderDevice = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(friendUserId.trim(), "UTF-8")
        val json = request("GET", "/v1/providers/user/$encoded", null, deviceToken())
        val device = json.optJSONObject("device") ?: throw LinkoNetworkException("provider_not_available")
        ProviderDevice(device.optString("id"), json.optBoolean("online", false), json.optLong("lastSeenAt", 0L))
    }

    suspend fun requestSession(providerDeviceId: String): DeviceSession = withContext(Dispatchers.IO) {
        val receiver = auth.currentDeviceId()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_auth_required")
        val json = request("POST", "/v1/sessions", JSONObject().put("receiverDeviceId", receiver).put("providerDeviceId", providerDeviceId), deviceToken())
        DeviceSession(json.optString("id"), json.optString("state"), json.optLong("expiresAt", 0L))
    }

    suspend fun transition(sessionId: String, state: String): DeviceSession = withContext(Dispatchers.IO) {
        val json = request("POST", "/v1/sessions/${encode(sessionId)}/transition", JSONObject().put("state", state), deviceToken())
        DeviceSession(json.optString("id"), json.optString("state"), json.optLong("expiresAt", 0L))
    }

    suspend fun session(sessionId: String): DeviceSession = withContext(Dispatchers.IO) {
        val json = request("GET", "/v1/sessions/${encode(sessionId)}", null, deviceToken())
        DeviceSession(json.optString("id"), json.optString("state"), json.optLong("expiresAt", 0L))
    }

    suspend fun tunnelConfig(sessionId: String): TunnelConfig = withContext(Dispatchers.IO) {
        val json = request("GET", "/v1/sessions/${encode(sessionId)}/tunnel", null, deviceToken())
        val endpoint = json.optJSONObject("endpoint") ?: throw LinkoNetworkException("tunnel_endpoint_missing")
        val host = endpoint.optString("host").takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("tunnel_host_missing")
        val port = endpoint.optInt("port", -1)
        val key = runCatching { java.util.Base64.getUrlDecoder().decode(json.optString("key")) }.getOrNull()
        if (port !in 1..65535 || key?.size != 32) throw LinkoNetworkException("tunnel_credentials_invalid")
        TunnelConfig(json.optString("sessionId", sessionId), host, port, key, json.optString("role"), json.optLong("expiresAt", 0L))
    }

    suspend fun pendingProviderRequests(): List<ProviderRequest> = withContext(Dispatchers.IO) {
        val json = request("GET", "/v1/provider/requests", null, deviceToken())
        val array = json.optJSONArray("requests") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(ProviderRequest(item.optString("id"), item.optString("receiverDeviceId"), item.optString("state"), item.optLong("expiresAt", 0L)))
            }
        }
    }

    private suspend fun deviceToken(): String {
        val current = auth.currentAccessToken()?.takeIf { it.isNotBlank() }
        if (current != null) return current
        val refreshed = auth.refreshSession()
        if (!refreshed.success) throw LinkoNetworkException("device_auth_required")
        return auth.currentAccessToken()?.takeIf { it.isNotBlank() }
            ?: throw LinkoNetworkException("device_auth_required")
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun request(method: String, path: String, body: JSONObject?, bearer: String): JSONObject {
        if (!baseUrl.startsWith("https://")) throw LinkoNetworkException("control_plane_https_required")
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            auth.currentDeviceId()?.let { setRequestProperty("X-Linko-Device-Id", it) }
        }
        return try {
            body?.let { connection.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val parsed = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrNull()
                val message = parsed?.optString("error").orEmpty().ifBlank { "http_$status" }
                throw LinkoNetworkException(message, status)
            }
            JSONObject(text.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }
}

data class DeviceRegistration(val deviceId: String, val token: String, val userId: String?)
data class PresenceResult(val deviceId: String, val lastSeenAt: Long)
data class ProviderDevice(val deviceId: String, val online: Boolean, val lastSeenAt: Long)
data class DeviceSession(val id: String, val state: String, val expiresAt: Long)
data class TunnelConfig(val sessionId: String, val host: String, val port: Int, val key: ByteArray, val role: String, val expiresAt: Long)
