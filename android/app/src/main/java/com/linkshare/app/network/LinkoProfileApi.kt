package com.linkshare.app.network

import com.linkshare.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Account-scoped profile API. Profile identity always comes from the authenticated Supabase user. */
class LinkoProfileApi(private val accessTokenProvider: () -> String?, private val userIdProvider: () -> String?) {
    suspend fun load(): ProfileRecord = request("GET", "profiles?user_id=eq.${encode(userId())}&select=user_id,linko_id,display_name", null).let { array ->
        val row = array.optJSONObject(0) ?: throw LinkoNetworkException("profile_not_found")
        ProfileRecord(row.optString("user_id"), row.optString("linko_id"), row.optString("display_name"))
    }

    suspend fun updateDisplayName(displayName: String): ProfileRecord {
        val clean = displayName.trim()
        require(clean.length in 2..40) { "Display name must be 2–40 characters." }
        val array = request("PATCH", "profiles?user_id=eq.${encode(userId())}&select=user_id,linko_id,display_name", JSONObject().put("display_name", clean))
        val row = array.optJSONObject(0) ?: throw LinkoNetworkException("profile_update_failed")
        return ProfileRecord(row.optString("user_id"), row.optString("linko_id"), row.optString("display_name"))
    }

    private fun userId(): String = userIdProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_user_required")

    private fun request(method: String, path: String, body: JSONObject?): JSONArray {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val connection = (URL("${BuildConfig.LINKO_SUPABASE_URL}/rest/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (method == "PATCH") setRequestProperty("Prefer", "return=representation")
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw LinkoNetworkException(JSONObject(text.ifBlank { "{}" }).optString("message", "http_$status"), status)
            return JSONArray(text.ifBlank { "[]" })
        } finally { connection.disconnect() }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

data class ProfileRecord(val userId: String, val linkoId: String, val displayName: String)
