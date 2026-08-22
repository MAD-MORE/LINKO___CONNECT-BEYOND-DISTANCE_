package com.linkshare.app.data

import com.linkshare.app.model.Friend

/**
 * Production boundary for LINKO signaling.
 *
 * The signaling service must authenticate both peers, carry the connection
 * request/approval, exchange ephemeral tunnel negotiation data, and report
 * whether a direct path or relay path was selected. No traffic payloads
 * should be routed through this service.
 */
interface SignalingRepository {
    suspend fun requestConnection(friend: Friend): SignalingResult
    suspend fun awaitApproval(requestId: String): ApprovalResult
    suspend fun negotiate(requestId: String): NegotiationResult
    suspend fun closeSession(sessionId: String)
}

sealed interface SignalingResult {
    data class Accepted(val requestId: String) : SignalingResult
    data class Rejected(val reason: String) : SignalingResult
    data class Failed(val reason: String) : SignalingResult
}

sealed interface ApprovalResult {
    data class Approved(val sessionId: String) : ApprovalResult
    data class Denied(val reason: String) : ApprovalResult
    data class Expired(val reason: String) : ApprovalResult
    data class Failed(val reason: String) : ApprovalResult
}

sealed interface NegotiationResult {
    data class Direct(val sessionId: String, val peerEndpoint: String) : NegotiationResult
    data class Relay(val sessionId: String, val relayEndpoint: String) : NegotiationResult
    data class Failed(val reason: String) : NegotiationResult
}
