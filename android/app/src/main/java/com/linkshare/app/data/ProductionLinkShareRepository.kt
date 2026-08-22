package com.linkshare.app.data

import com.linkshare.app.model.Friend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Production control-plane boundary. */
class ProductionLinkShareRepository(
    private val signaling: HttpSignalingRepository
) {
    suspend fun request(receiverId: String, friend: Friend): String =
        signaling.requestHostAccess(receiverId, friend)

    suspend fun status(requestId: String): JSONObject = signaling.getRequest(requestId)

    suspend fun pending(providerId: String): List<JSONObject> =
        signaling.listPendingRequests(providerId)

    suspend fun approve(requestId: String, providerId: String): JSONObject =
        signaling.approveRequest(requestId, providerId)

    suspend fun deny(requestId: String, providerId: String): JSONObject =
        signaling.denyRequest(requestId, providerId)

    suspend fun createSession(requestId: String): JSONObject = signaling.createSession(requestId)

    suspend fun negotiate(sessionId: String, type: String, payload: String): JSONObject =
        signaling.negotiate(sessionId, type, payload)

    suspend fun close(sessionId: String) = signaling.closeSession(sessionId)

    suspend fun buildTunnelPacket(plainPacket: ByteArray, sessionKey: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            error("Encrypted peer transport is not configured")
        }
}
