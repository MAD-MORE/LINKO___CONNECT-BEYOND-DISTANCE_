package com.linkshare.app.vpn

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec

object KeyManager {
    private const val PREFS_NAME = "linko_keys"
    private const val KEY_PRIVATE = "wg_private"
    private const val KEY_PUBLIC = "wg_public"

    fun ensureKeypair(context: Context): Pair<ByteArray, ByteArray> {
        val prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val privB64 = prefs.getString(KEY_PRIVATE, null)
        val pubB64 = prefs.getString(KEY_PUBLIC, null)
        if (privB64 != null && pubB64 != null) {
            return Pair(Base64.decode(privB64, Base64.DEFAULT), Base64.decode(pubB64, Base64.DEFAULT))
        }

        // Android 12+ provides the X25519 JCA algorithm used by modern key agreement.
        // Do not require Bouncy Castle FIPS just to generate a keypair.
        val kpg = KeyPairGenerator.getInstance("X25519")
        val kp: KeyPair = kpg.generateKeyPair()
        val priv = kp.private.encoded
        val pub = kp.public.encoded

        prefs.edit()
            .putString(KEY_PRIVATE, Base64.encodeToString(priv, Base64.NO_WRAP))
            .putString(KEY_PUBLIC, Base64.encodeToString(pub, Base64.NO_WRAP))
            .apply()

        return Pair(priv, pub)
    }

    fun publicKeyFromBytes(pubBytes: ByteArray): Any? {
        return try {
            val kf = KeyFactory.getInstance("X25519")
            val spec = X509EncodedKeySpec(pubBytes)
            kf.generatePublic(spec)
        } catch (_: Exception) {
            null
        }
    }
}
