package com.linkshare.app.network

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest

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
    val relayUrl: String?,
    val expiresAtEpochSeconds: Long
)

data class HostSession(
    val sessionId: String,
    val clientPublicKey: String,
    val allowedUntilEpochSeconds: Long
)
