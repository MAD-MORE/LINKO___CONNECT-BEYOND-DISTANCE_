package com.linkshare.app.vpn

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.X509EncodedKeySpec

object KeyManager {
    private const val PREFS_NAME = "linko_keys"
    private const val KEY_PRIVATE = "wg_private"
    private const val KEY_PUBLIC = "wg_public"

    init {
        // Ensure BouncyCastle provider available
        try {
            Security.addProvider(BouncyCastleFipsProvider())
        } catch (_: Throwable) {}
    }

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

        // Generate X25519 keypair (WireGuard uses Curve25519)
        val kpg = try {
            KeyPairGenerator.getInstance("X25519")
        } catch (e: Exception) {
            // Fallback: try provider-specific
            KeyPairGenerator.getInstance("X25519", "BCFIPS")
        }
        val kp: KeyPair = kpg.generateKeyPair()
        val priv = kp.private.encoded
        val pub = kp.public.encoded

        prefs.edit().putString(KEY_PRIVATE, Base64.encodeToString(priv, Base64.NO_WRAP)).apply()
        prefs.edit().putString(KEY_PUBLIC, Base64.encodeToString(pub, Base64.NO_WRAP)).apply()

        return Pair(priv, pub)
    }

    fun publicKeyFromBytes(pubBytes: ByteArray): Any? {
        return try {
            val kf = KeyFactory.getInstance("X25519")
            val spec = X509EncodedKeySpec(pubBytes)
            kf.generatePublic(spec)
        } catch (e: Exception) {
            null
        }
    }
}
