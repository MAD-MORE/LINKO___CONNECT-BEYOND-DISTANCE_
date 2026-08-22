package com.linkshare.app.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec

/** Stable device key identity backed by Android Keystore. */
class LinkoDeviceIdentity {
    private val alias = "linko_device_signing_key"
    private val store: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun publicKeyBase64(): String = Base64.encodeToString(getOrCreateKeyPair().public.encoded, Base64.NO_WRAP)

    fun deviceFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(getOrCreateKeyPair().public.encoded)
        return digest.joinToString("") { "%02x".format(it) }.take(24).uppercase()
    }

    /** Permanently replaces this installation's signing key and therefore its device ID. */
    fun rotate(): String {
        if (store.containsAlias(alias)) store.deleteEntry(alias)
        return deviceFingerprint()
    }

    private fun getOrCreateKeyPair(): KeyPair {
        val existing = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return KeyPair(existing.certificate.publicKey, existing.privateKey)
        val generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        return generator.generateKeyPair()
    }
}
