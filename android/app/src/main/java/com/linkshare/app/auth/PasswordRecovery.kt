package com.linkshare.app.auth

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Password recovery request against Supabase Auth. */
object PasswordRecovery {
    private const val BASE_URL = "https://pbnvssbtshvesqwhckfa.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_lUMjChFhCBKATMQzEpD5vg_ZdSc6Fw9"
    private val emailRegex = Regex("^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}")

    fun send(email: String): AuthResult {
        val normalized = email.trim().lowercase()
        if (normalized.length > 254 || !emailRegex.matches(normalized)) {
            return AuthResult(false, "valid_email_required", false)
        }
        return try {
            val connection = (URL("$BASE_URL/auth/v1/recover").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("apikey", PUBLISHABLE_KEY)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                connection.outputStream.use { it.write(JSONObject().put("email", normalized).toString().toByteArray()) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status in 200..299) AuthResult(true, "reset_email_sent", false)
                else AuthResult(false, parseError(body, status), false)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            AuthResult(false, e.message ?: "network_error", false)
        }
    }

    private fun parseError(body: String, status: Int): String {
        return try {
            val obj = JSONObject(body.ifBlank { "{}" })
            val raw = obj.optString("msg").ifBlank { obj.optString("message") }.ifBlank { "auth_http_$status" }
            if (status == 429) "too_many_requests" else raw
        } catch (_: Exception) {
            if (status == 429) "too_many_requests" else "auth_http_$status"
        }
    }
}
