package com.linkshare.app.network

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest

/** Compatibility API surface for friend operations. Connection transport is direct UDP only. */
interface LinkShareApi {
    suspend fun getFriends(): List<Friend>
    suspend fun watchIncomingRequests(onRequest: (IncomingRequest) -> Unit)
    suspend fun requestAccess(hostId: String): SignalingSession
    suspend fun approveRequest(requestId: String): HostSession
    suspend fun denyRequest(requestId: String)
}

data class SignalingSession(
    val sessionId: String,
    val hostPublicKey: String,
    val expiresAtEpochSeconds: Long,
    val transport: String = "direct_udp",
)

data class HostSession(
    val sessionId: String,
    val clientPublicKey: String,
    val allowedUntilEpochSeconds: Long,
    val transport: String = "direct_udp",
)
