package com.linkshare.app.auth

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class LinkoAuth(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("linko_auth", Context.MODE_PRIVATE)

    fun signUp(email: String, password: String, displayName: String): AuthResult {
        validate(email, password)
        val normalized = email.trim().lowercase()
        val pending = prefs.getString(KEY_PENDING_EMAIL, null)
        if (pending == normalized) {
            return AuthResult(false, "signup_pending_verification", false, null)
        }
        val body = JSONObject()
            .put("email", normalized)
            .put("password", password)
            .put("data", JSONObject().put("display_name", displayName.trim()))
        val created = handleAuth("/auth/v1/signup", "POST", body, saveTokens = false)
        if (created.success) {
            prefs.edit().putString(KEY_PENDING_EMAIL, normalized).apply()
        }
        return created
    }

    private fun validate(email: String, password: String) {
        require(email.trim().contains("@")) { "Enter a valid email address" }
        require(password.length >= 6) { "Password must be at least 6 characters" }
    }

    fun signIn(email: String, password: String): AuthResult {
        validate(email, password)
        val normalized = email.trim().lowercase()
        val body = JSONObject().put("email", normalized).put("password", password)
        return handleAuth("/auth/v1/token?grant_type=password", "POST", body, saveTokens = true)
    }

    // REST implementation restored from the previous version; duplicate signup guard is applied before this request.
    private fun handleAuth(path: String, method: String, body: JSONObject, saveTokens: Boolean): AuthResult {
        TODO("REST implementation")
    }

    companion object {
        private const val KEY_PENDING_EMAIL = "pending_signup_email"
    }
}

data class AuthResult(
    val success: Boolean,
    val message: String,
    val requiresVerification: Boolean,
    val userId: String?,
)
