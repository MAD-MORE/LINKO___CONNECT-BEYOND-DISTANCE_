package com.linkshare.app.tunnel

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/** Private key remains in process memory; only the public key crosses signaling. */
class SessionKeyAgreement {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    fun publicKeyBase64(): String = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    fun deriveSessionKey(peerPublicKeyBase64: String, sessionId: String): ByteArray {
        val encoded = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT)
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keyPair.private)
        agreement.doPhase(peer, true)
        return SessionKeyDerivation.derive(agreement.generateSecret(), sessionId, 32)
    }
}
