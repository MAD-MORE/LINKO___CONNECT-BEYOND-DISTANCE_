package com.linkshare.app.network

import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoDeviceIdentity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Exchanges a Supabase user session for a persisted LINKO device session. */
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
            .put("publicKey", identity.publicKeyBase64())
            .put("name", "Android-${identity.deviceFingerprint()}")
            .put("roles", org.json.JSONArray().put("receiver").put("provider"))

        val connection = (URL(baseUrl.trimEnd('/') + "/v1/devices/register").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $supabaseToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw LinkoNetworkException(JSONObject(response.ifBlank { "{}" }).optString("error", "http_$status"), status)
            val json = JSONObject(response)
            val device = json.getJSONObject("device")
            val linkoToken = json.getString("accessToken")
            auth.saveLinkoSession(device.getString("id"), linkoToken, json.optJSONObject("user")?.optString("id"))
            return true
        } finally {
            connection.disconnect()
        }
    }
}
