package com.linkshare.app.tunnel

import android.content.Context
import android.util.Base64
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair

/**
 * Per-installation WireGuard identity. The private key never leaves the device.
 * It is stored in the app's private preferences and the public key is safe to publish
 * through the authenticated LINKO control plane.
 */
class LinkoWireGuardIdentity(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun keyPair(): KeyPair {
        val encoded = prefs.getString(PRIVATE_KEY, null)
        if (!encoded.isNullOrBlank()) {
            return KeyPair(Key.fromBase64(encoded))
        }
        val pair = KeyPair()
        prefs.edit().putString(PRIVATE_KEY, pair.getPrivateKey().toBase64()).apply()
        return pair
    }

    fun publicKeyBase64(): String = keyPair().getPublicKey().toBase64()

    fun privateKeyBase64(): String = keyPair().getPrivateKey().toBase64()

    fun publicKeyBytes(): ByteArray = Base64.decode(publicKeyBase64(), Base64.DEFAULT)

    companion object {
        private const val PREFS = "linko_wireguard_identity"
        private const val PRIVATE_KEY = "private_key"
    }
}
