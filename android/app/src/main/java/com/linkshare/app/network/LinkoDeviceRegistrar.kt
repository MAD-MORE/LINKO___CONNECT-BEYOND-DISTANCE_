package com.linkshare.app.network

import com.linkshare.app.BuildConfig
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoDeviceIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Registers the local device through the authenticated Supabase control-plane RPC. */
class LinkoDeviceRegistrar(
    private val baseUrl: String,
    private val auth: LinkoAuth,
) {
    fun ensureRegistered(): Boolean {
        val supabaseToken = auth.currentAccessToken() ?: return false
        if (auth.hasRegisteredDevice()) return true
        require(baseUrl.startsWith("https://")) { "control_plane_https_required" }

        val identity = LinkoDeviceIdentity()
        val body = JSONObject()
            .put("p_public_key", identity.publicKeyBase64())
            .put("p_name", "Android-${identity.deviceFingerprint()}")
            .put("p_roles", JSONArray().put("receiver").put("provider"))

        val connection = (URL(baseUrl.trimEnd('/') + "/rest/v1/rpc/linko_register_device").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $supabaseToken")
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw LinkoNetworkException(JSONObject(response.ifBlank { "{}" }).optString("message", "http_$status"), status)
            }
            val json = JSONObject(response)
            val device = json.getJSONObject("device")
            // The control plane uses the normal Supabase access token; refreshes remain authoritative.
            auth.saveLinkoSession(device.getString("id"), supabaseToken, json.optJSONObject("user")?.optString("id"))
            return true
        } finally {
            connection.disconnect()
        }
    }
}
