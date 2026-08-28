package com.linkshare.app.network

import com.linkshare.app.BuildConfig
import com.linkshare.app.auth.LinkoAuth
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Account-scoped profile API communicating directly with Supabase PostgREST tables.
 */
class LinkoProfileApi(
    private val accessTokenProvider: () -> String?,
    private val userIdProvider: () -> String?,
    private val refreshProvider: (suspend () -> Boolean)? = { LinkoAuth.current()?.refreshSession()?.success == true },
) {
    suspend fun load(): ProfileRecord {
        val uid = userId()
        val path = "/rest/v1/profiles?user_id=eq.$uid&select=*"
        val result = request("GET", path)
        val profileJson = when {
            result.optJSONArray("data") != null -> result.optJSONArray("data")?.optJSONObject(0)
            result.has("linko_id") -> result
            else -> null
        } ?: throw LinkoNetworkException("profile_not_found")

        return profileJson.toProfile().also { 
            LinkoAuth.current()?.saveProfile(it.displayName, it.linkoId, it.username) 
        }
    }

    /**
     * Updates the user's display name directly in Supabase profiles table.
     */
    suspend fun updateDisplayName(displayName: String): ProfileRecord {
        val clean = displayName.trim()
        require(clean.length in 2..40) { "Display name must be 2–40 characters." }
        val uid = userId()
        val path = "/rest/v1/profiles?user_id=eq.$uid"
        val body = JSONObject().put("display_name", clean)
        val result = request("PATCH", path, body, preferReturn = true)
        
        val profileJson = when {
            result.optJSONArray("data") != null -> result.optJSONArray("data")?.optJSONObject(0)
            result.has("linko_id") -> result
            else -> null
        }

        val updated = if (profileJson != null) {
            profileJson.toProfile()
        } else {
            // Fallback reload
            load()
        }

        LinkoAuth.current()?.saveProfile(updated.displayName, updated.linkoId, updated.username)
        return updated
    }

    private fun userId(): String = userIdProvider()?.takeIf { it.isNotBlank() }
        ?: throw LinkoNetworkException("auth_user_required")

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        preferReturn: Boolean = false
    ): JSONObject {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw LinkoNetworkException("auth_required")
        val first = execute(method, path, body, token, preferReturn)
        if (first.status == 401 && refreshProvider?.invoke() == true) {
            val newToken = accessTokenProvider()?.takeIf { it.isNotBlank() }
                ?: throw LinkoNetworkException("auth_required")
            return parseResponse(execute(method, path, body, newToken, preferReturn))
        }
        return parseResponse(first)
    }

    private fun execute(
        method: String,
        path: String,
        body: JSONObject?,
        token: String,
        preferReturn: Boolean
    ): HttpResult {
        val url = "${BuildConfig.LINKO_SUPABASE_URL.trimEnd('/')}$path"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (preferReturn) {
                setRequestProperty("Prefer", "return=representation")
            }
        }
        return try {
            body?.let { payload ->
                connection.outputStream.use { out ->
                    out.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResult(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (e: Exception) {
            throw LinkoNetworkException(e.message?.takeIf { it.isNotBlank() } ?: "network_error")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(result: HttpResult): JSONObject {
        if (result.status !in 200..299) {
            val obj = runCatching { JSONObject(result.body.ifBlank { "{}" }) }.getOrNull()
            val message = obj?.optString("message").orEmpty()
                .ifBlank { obj?.optString("error").orEmpty() }
                .ifBlank { "http_${result.status}" }
            throw LinkoNetworkException(message, result.status)
        }
        val trimmed = result.body.trim()
        return when {
            trimmed.startsWith("[") -> {
                val array = runCatching { JSONArray(trimmed) }.getOrDefault(JSONArray())
                JSONObject().put("data", array)
            }
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> JSONObject()
        }
    }

    private fun JSONObject.toProfile(): ProfileRecord {
        val expectedUserId = userId()
        val responseUserId = optString("user_id").ifBlank { expectedUserId }
        if (responseUserId != expectedUserId) {
            throw LinkoNetworkException("profile_account_mismatch")
        }
        val linkoId = optString("linko_id")
        val displayName = optString("display_name").ifBlank { "LINKO User" }
        val username = optString("username").removePrefix("@").takeIf { it.isNotBlank() }
        if (linkoId.isBlank()) throw LinkoNetworkException("profile_linko_id_missing")
        return ProfileRecord(responseUserId, linkoId, displayName, username)
    }

    private data class HttpResult(val status: Int, val body: String)
}

data class ProfileRecord(val userId: String, val linkoId: String, val displayName: String, val username: String? = null)
