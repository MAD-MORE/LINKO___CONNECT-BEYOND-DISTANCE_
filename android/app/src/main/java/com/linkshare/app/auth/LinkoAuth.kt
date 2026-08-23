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

        val created = handleAuth(
            "/auth/v1/signup",
            "POST",
            body,
            saveTokens = false,
        )

        if (created.success) {
            prefs.edit()
                .putString(KEY_PENDING_EMAIL, normalized)
                .apply()
        }

        return created
    }

    fun clearPendingSignup() {
        prefs.edit().remove(KEY_PENDING_EMAIL).apply()
    }

    fun pendingSignupEmail(): String? = prefs.getString(KEY_PENDING_EMAIL, null)

    fun signIn(email: String, password: String): AuthResult {
        validate(email, password)
        val normalized = email.trim().lowercase()
        val body = JSONObject()
            .put("email", normalized)
            .put("password", password)
        return handleAuth("/auth/v1/token?grant_type=password", "POST", body, saveTokens = true)
    }

    private fun validate(email: String, password: String) {
        require(email.trim().contains("@")) { "Enter a valid email address" }
        require(password.length >= 6) { "Password must be at least 6 characters" }
    }

    // Existing LINKO HTTP/auth implementation remains below this point.
    private fun handleAuth(path: String, method: String, body: JSONObject, saveTokens: Boolean): AuthResult {
        TODO("Existing handleAuth implementation")
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
