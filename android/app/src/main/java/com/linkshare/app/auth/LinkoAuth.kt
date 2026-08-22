package com.linkshare.app.auth

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Supabase Auth plus persistent LINKO session credentials. */
class LinkoAuth(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("linko_auth", Context.MODE_PRIVATE)

    fun currentAccessToken(): String? = readSecret(KEY_ACCESS)
    fun currentRefreshToken(): String? = readSecret(KEY_REFRESH)
    fun currentLinkoToken(): String? = readSecret(KEY_LINKO)
    fun currentDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun currentUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun isSignedIn(): Boolean = !currentAccessToken().isNullOrBlank()
    fun hasRegisteredDevice(): Boolean = !currentLinkoToken().isNullOrBlank() && !currentDeviceId().isNullOrBlank()

    fun signUp(email: String, password: String, displayName: String): AuthResult {
        validate(email, password)
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject().put("display_name", displayName.trim()))
        return handleAuth("/auth/v1/signup", "POST", body, saveTokens = true)
    }

    fun signIn(email: String, password: String): AuthResult {
        validate(email, password)
        val body = JSONObject().put("email", email.trim()).put("password", password)
        return handleAuth("/auth/v1/token?grant_type=password", "POST", body, saveTokens = true)
    }

    fun refresh(): AuthResult {
        val refresh = currentRefreshToken() ?: return AuthResult(false, "session_missing", false)
        return handleAuth("/auth/v1/token?grant_type=refresh_token", "POST", JSONObject().put("refresh_token", refresh), saveTokens = true)
    }

    fun signOut(): Boolean {
        val token = currentAccessToken()
        if (!token.isNullOrBlank()) request("/auth/v1/logout", "POST", null, token)
        clear()
        return true
    }

    fun updateDisplayName(displayName: String): AuthResult {
        val token = currentAccessToken() ?: return AuthResult(false, "session_missing", false)
        return handleAuth("/auth/v1/user", "PUT", JSONObject().put("data", JSONObject().put("display_name", displayName.trim())), false, token)
    }

    fun saveLinkoSession(deviceId: String, linkoToken: String, userId: String?) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).putString(KEY_USER_ID, userId).apply()
        writeSecret(KEY_LINKO, linkoToken)
    }

    fun clearLinkoSession() {
        prefs.edit().remove(KEY_DEVICE_ID).remove(KEY_USER_ID).apply()
        prefs.edit().remove(KEY_LINKO).apply()
    }

    private fun handleAuth(path: String, method: String, body: JSONObject?, saveTokens: Boolean, token: String? = null): AuthResult {
        return try {
            val response = request(path, method, body, token)
            if (response.first !in 200..299) {
                val obj = JSONObject(response.second.ifBlank { "{}" })
                val message = obj.optString("msg").ifBlank { obj.optString("error_description") }.ifBlank { obj.optString("message") }.ifBlank { "auth_http_${response.first}" }
                return AuthResult(false, message, false)
            }
            val json = JSONObject(response.second.ifBlank { "{}" })
            val access = json.optString("access_token").takeIf { it.isNotBlank() }
            val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
            val userId = json.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
            if (saveTokens && access != null) {
                writeSecret(KEY_ACCESS, access)
                refresh?.let { writeSecret(KEY_REFRESH, it) }
                userId?.let { prefs.edit().putString(KEY_USER_ID, it).apply() }
            }
            AuthResult(true, "ok", access != null, userId)
        } catch (e: Exception) {
            AuthResult(false, e.message ?: "network_error", false)
        }
    }

    private fun request(path: String, method: String, body: JSONObject?, accessToken: String?): Pair<Int, String> {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        try {
            body?.let { payload -> connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return status to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally { connection.disconnect() }
    }

    private fun validate(email: String, password: String) {
        require(email.contains('@') && email.contains('.')) { "valid_email_required" }
        require(password.length >= 8) { "password_min_8_chars" }
    }

    private fun clear() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_LINKO).remove(KEY_DEVICE_ID).remove(KEY_USER_ID).apply()
    }

    private fun writeSecret(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readSecret(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        return try {
            val parts = packed.split(':', limit = 2)
            if (parts.size != 2) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
        } catch (_: Exception) { clear(); null }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "linko_auth_key"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_LINKO = "linko_device_token"
        private const val KEY_DEVICE_ID = "linko_device_id"
        private const val KEY_USER_ID = "linko_user_id"
        private const val BASE_URL = "https://pbnvssbtshvesqwhckfa.supabase.co"
        private const val PUBLISHABLE_KEY = "sb_publishable_lUMjChFhCBKATMQzEpD5vg_ZdSc6Fw9"
    }
}

data class AuthResult(val success: Boolean, val message: String, val sessionCreated: Boolean, val userId: String? = null)
