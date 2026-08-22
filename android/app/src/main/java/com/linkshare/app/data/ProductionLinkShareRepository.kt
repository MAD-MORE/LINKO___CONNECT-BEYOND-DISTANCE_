package com.linkshare.app.data

import com.linkshare.app.model.Friend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Production connection boundary.
 * Signaling is real HTTPS; packet forwarding is intentionally delegated to
 * the platform VPN/tunnel implementation rather than simulated here.
 */
class ProductionLinkShareRepository(
    private val signaling: HttpSignalingRepository
) {
    suspend fun request(friend: Friend): String = signaling.requestHostAccess(friend)

    suspend fun status(requestId: String): JSONObject = signaling.getRequest(requestId)

    suspend fun negotiate(sessionId: String, type: String, payload: String): JSONObject =
        signaling.negotiate(sessionId, type, payload)

    suspend fun close(sessionId: String) = signaling.closeSession(sessionId)

    suspend fun buildTunnelPacket(plainPacket: ByteArray, sessionKey: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            // Reserved for the audited tunnel implementation. Never send plaintext
            // packets to the relay. Failing closed is safer than silently exposing data.
            error("Encrypted tunnel provider is not configured")
        }
}