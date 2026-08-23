package com.linkshare.app.auth

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LinkoAuth(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("linko_auth", Context.MODE_PRIVATE)

    fun signUp(email: String, password: String, displayName: String): AuthResult {
        validate(email, password)
        val normalized = email.trim().lowercase()
        val body = JSONObject().put("email", normalized).put("password", password)
            .put("data", JSONObject().put("display_name", displayName.trim()))
        return request("/auth/v1/signup", "POST", body, saveTokens = true)
    }

    fun signIn(email: String, password: String): AuthResult {
        validate(email, password)
        val normalized = email.trim().lowercase()
        return request("/auth/v1/token?grant_type=password", "POST", JSONObject().put("email", normalized).put("password", password), saveTokens = true)
    }

    fun isSignedIn(): Boolean = !currentAccessToken().isNullOrBlank()
    fun currentAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun pendingVerificationEmail(): String? = null
    fun currentLinkoToken(): String? = prefs.getString(KEY_LINKO_TOKEN, null)
    fun currentDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun hasRegisteredDevice(): Boolean = !currentDeviceId().isNullOrBlank() && !currentLinkoToken().isNullOrBlank()

    fun saveLinkoSession(deviceId: String, linkoToken: String, userId: String?) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).putString(KEY_LINKO_TOKEN, linkoToken).apply()
    }

    fun clearLinkoSession() {
        prefs.edit().remove(KEY_DEVICE_ID).remove(KEY_LINKO_TOKEN).apply()
    }

    fun signOut() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN)
            .remove(KEY_LINKO_TOKEN).remove(KEY_DEVICE_ID).apply()
    }

    suspend fun verifySignupOtp(email: String, code: String): AuthResult = AuthResult(false, "verification_disabled")
    suspend fun sendSignupOtp(email: String): AuthResult = AuthResult(false, "verification_disabled")
    suspend fun verifyRecoveryOtp(email: String, code: String): AuthResult = verifyOtp(email, code, "recovery")
    suspend fun sendRecoveryOtp(email: String): AuthResult = resendOtp(email, "recovery")

    fun updatePassword(password: String): AuthResult {
        if (password.length !in 8..72) return AuthResult(false, "password_length_invalid")
        val token = currentAccessToken() ?: return AuthResult(false, "session_required", true)
        return request("/auth/v1/user", "PUT", JSONObject().put("password", password), token, false)
    }

    private fun validate(email: String, password: String) {
        require(emailRegex.matches(email.trim())) { "Enter a valid email address" }
        require(password.length >= 6) { "Password must be at least 6 characters" }
    }

    private fun verifyOtp(email: String, code: String, type: String): AuthResult {
        val normalized = email.trim().lowercase()
        if (!emailRegex.matches(normalized) || code.length != 6 || !code.all(Char::isDigit)) return AuthResult(false, "verification_code_invalid")
        return request("/auth/v1/verify", "POST", JSONObject().put("email", normalized).put("token", code).put("type", type), saveTokens = true)
    }

    private fun resendOtp(email: String, type: String): AuthResult {
        val normalized = email.trim().lowercase()
        if (!emailRegex.matches(normalized)) return AuthResult(false, "valid_email_required")
        return request("/auth/v1/resend", "POST", JSONObject().put("type", type).put("email", normalized), saveTokens = false)
    }

    private fun request(path: String, method: String, body: JSONObject, accessToken: String? = null, saveTokens: Boolean): AuthResult {
        return try {
            val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = method != "GET"
                setRequestProperty("apikey", PUBLISHABLE_KEY)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                if (method != "GET") connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) return AuthResult(false, parseError(response, status))
                val json = JSONObject(response.ifBlank { "{}" })
                val userId = json.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
                if (saveTokens) {
                    json.optString("access_token").takeIf { it.isNotBlank() }?.let { access ->
                        val edit = prefs.edit().putString(KEY_ACCESS_TOKEN, access)
                        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { edit.putString(KEY_REFRESH_TOKEN, it) }
                        edit.apply()
                    }
                }
                AuthResult(true, when {
                    path.endsWith("/signup") -> if (json.has("access_token")) "authenticated" else "account_created"
                    path.endsWith("/verify") -> "verified"
                    path.endsWith("/user") -> "password_updated"
                    json.has("access_token") -> "authenticated"
                    else -> "ok"
                }, false, userId)
            } finally { connection.disconnect() }
        } catch (e: Exception) {
            AuthResult(false, e.message?.takeIf { it.isNotBlank() } ?: "network_error")
        }
    }

    private fun parseError(body: String, status: Int): String {
        if (status == 429) return "too_many_requests"
        return try {
            val obj = JSONObject(body.ifBlank { "{}" })
            obj.optString("msg").ifBlank { obj.optString("message") }
                .ifBlank { obj.optString("error_description") }.ifBlank { obj.optString("error") }
                .ifBlank { "auth_http_$status" }.lowercase().replace(' ', '_')
        } catch (_: Exception) { "auth_http_$status" }
    }

    companion object {
        private const val BASE_URL = "https://pbnvssbtshvesqwhckfa.supabase.co"
        private const val PUBLISHABLE_KEY = "sb_publishable_lUMjChFhCBKATMQzEpD5vg_ZdSc6Fw9"
        private const val TIMEOUT_MS = 15_000
        private const val KEY_ACCESS_TOKEN = "supabase_access_token"
        private const val KEY_REFRESH_TOKEN = "supabase_refresh_token"
        private const val KEY_DEVICE_ID = "linko_device_id"
        private const val KEY_LINKO_TOKEN = "linko_access_token"
        private val emailRegex = Regex("^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}")
    }
}

data class AuthResult(
    val success: Boolean,
    val message: String,
    val requiresVerification: Boolean = false,
    val userId: String? = null,
)
