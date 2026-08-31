package com.linkshare.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * LinkoSecurePrefs
 *
 * A thin wrapper over EncryptedSharedPreferences for securely storing
 * Linko's sensitive values on the device.
 *
 * All values are encrypted using AES-256-GCM with an AES-256 master key
 * stored in the Android Keystore.
 *
 * IMPORTANT: This file is excluded from Android Auto Backup.
 * Device JWTs and device IDs must not be restored to a different device.
 */
object LinkoSecurePrefs {

    private const val PREFS_NAME = "linko_secure_v1"

    private const val KEY_DEVICE_JWT = "device_jwt"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_SUPABASE_ACCESS_TOKEN = "supabase_access_token"
    private const val KEY_SUPABASE_REFRESH_TOKEN = "supabase_refresh_token"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // -------------------------------------------------------------------------
    // Device JWT (Linko-issued device-level auth token)
    // -------------------------------------------------------------------------

    fun getDeviceJwt(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_JWT, null)

    fun setDeviceJwt(context: Context, jwt: String) =
        prefs(context).edit().putString(KEY_DEVICE_JWT, jwt).apply()

    fun clearDeviceJwt(context: Context) =
        prefs(context).edit().remove(KEY_DEVICE_JWT).apply()

    // -------------------------------------------------------------------------
    // Device ID (permanent per-installation UUID)
    // -------------------------------------------------------------------------

    fun getDeviceId(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_ID, null)

    fun setDeviceId(context: Context, id: String) =
        prefs(context).edit().putString(KEY_DEVICE_ID, id).apply()

    // -------------------------------------------------------------------------
    // User identity (from Supabase)
    // -------------------------------------------------------------------------

    fun getUserId(context: Context): String? =
        prefs(context).getString(KEY_USER_ID, null)

    fun setUserId(context: Context, id: String) =
        prefs(context).edit().putString(KEY_USER_ID, id).apply()

    fun getUserEmail(context: Context): String? =
        prefs(context).getString(KEY_USER_EMAIL, null)

    fun setUserEmail(context: Context, email: String) =
        prefs(context).edit().putString(KEY_USER_EMAIL, email).apply()

    // -------------------------------------------------------------------------
    // Supabase tokens (for re-authentication and Realtime subscriptions)
    // -------------------------------------------------------------------------

    fun getSupabaseAccessToken(context: Context): String? =
        prefs(context).getString(KEY_SUPABASE_ACCESS_TOKEN, null)

    fun setSupabaseAccessToken(context: Context, token: String) =
        prefs(context).edit().putString(KEY_SUPABASE_ACCESS_TOKEN, token).apply()

    fun getSupabaseRefreshToken(context: Context): String? =
        prefs(context).getString(KEY_SUPABASE_REFRESH_TOKEN, null)

    fun setSupabaseRefreshToken(context: Context, token: String) =
        prefs(context).edit().putString(KEY_SUPABASE_REFRESH_TOKEN, token).apply()

    // -------------------------------------------------------------------------
    // FCM token (for push notifications)
    // -------------------------------------------------------------------------

    fun getFcmToken(context: Context): String? =
        prefs(context).getString(KEY_FCM_TOKEN, null)

    fun setFcmToken(context: Context, token: String) =
        prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()

    // -------------------------------------------------------------------------
    // Bulk operations
    // -------------------------------------------------------------------------

    /**
     * Clear all Linko secure preferences.
     * Called on sign-out. Also triggers Keystore key deletion via [LinkoKeyManager].
     */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        LinkoKeyManager.deleteAllLinkoKeys()
    }

    /**
     * Check if the device is authenticated (has a device JWT).
     */
    fun isAuthenticated(context: Context): Boolean =
        getDeviceJwt(context) != null && getDeviceId(context) != null
}
