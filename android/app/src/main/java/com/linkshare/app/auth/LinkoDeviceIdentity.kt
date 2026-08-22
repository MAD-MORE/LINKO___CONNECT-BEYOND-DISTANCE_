package com.linkshare.app.auth

import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec

/** Stable device key identity backed by Android Keystore. */
class LinkoDeviceIdentity {
    private val alias = "linko_device_signing_key"
    private val store: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun publicKeyBase64(): String {
        val public = getOrCreateKeyPair().public.encoded
        return Base64.encodeToString(public, Base64.NO_WRAP)
    }

    fun deviceFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(getOrCreateKeyPair().public.encoded)
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun getOrCreateKeyPair(): java.security.KeyPair {
        val existing = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return java.security.KeyPair(existing.certificate.publicKey, existing.privateKey)
        val generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair().also {
            // The Android Keystore provider persists this pair under the alias only
            // when KeyGenParameterSpec is used; recreate below with the platform spec.
            store.deleteEntry(alias)
            val keyGenerator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
            keyGenerator.initialize(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY,
                ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            keyGenerator.generateKeyPair()
        }
    }
}
