package com.linkshare.app.network

import com.linkshare.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Account-scoped profile API. Profile identity always comes from the authenticated Supabase user. */
class LinkoProfileApi(
    private val accessTokenProvider: () -> String?,
    private val userIdProvider: () -> String?,
    private val refreshProvider: (suspend () -> Boolean)? = null,
) {
    suspend fun load(): ProfileRecord {
        val array = request("GET", "profiles?user_id=eq.${encode(userId())}&select=user_id,linko_id,username,display_name")
        val row = array.optJSONObject(0) ?: throw LinkoNetworkException("profile_not_found")
        return row.toProfile()
    }

    suspend fun updateDisplayName(displayName: String): ProfileRecord {
        val clean = displayName.trim()
        require(clean.length in 2..40) { "Display name must be 2–40 characters." }
        val array = request("PATCH", "profiles?user_id=eq.${encode(userId())}&select=user_id,linko_id,username,display_name", JSONObject().put("display_name", clean))
        val row = array.optJSONObject(0) ?: throw LinkoNetworkException("profile_update_failed")
        return row.toProfile()
    }

    private fun userId(): String = userIdProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_user_required")

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONArray {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
        val first = execute(method, path, body, token)
        if (first.status == 401) {
            val refreshed = refreshProvider?.invoke() == true
            if (refreshed) {
                val newToken = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("auth_required")
                val retry = execute(method, path, body, newToken)
                return parseResponse(retry)
            }
        }
        return parseResponse(first)
    }

    private fun execute(method: String, path: String, body: JSONObject?, token: String): HttpResult {
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
        return try {
            body?.let { connection.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResult(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (e: Exception) {
            throw LinkoNetworkException(e.message?.takeIf { it.isNotBlank() } ?: "network_error")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(result: HttpResult): JSONArray {
        if (result.status !in 200..299) {
            val body = result.body
            val message = runCatching {
                JSONObject(body.ifBlank { "{}" }).optString("message").ifBlank {
                    JSONObject(body.ifBlank { "{}" }).optString("msg")
                }
            }.getOrNull().orEmpty().ifBlank { "http_${result.status}" }
            throw LinkoNetworkException(message, result.status)
        }
        return JSONArray(result.body.ifBlank { "[]" })
    }

    private fun JSONObject.toProfile(): ProfileRecord = ProfileRecord(
        optString("user_id"),
        optString("linko_id"),
        optString("display_name"),
        optString("username").removePrefix("@").takeIf { it.isNotBlank() },
    )

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private data class HttpResult(val status: Int, val body: String)
}

data class ProfileRecord(val userId: String, val linkoId: String, val displayName: String, val username: String? = null)
