package com.linkshare.app.data

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest

/**
 * Transitional repository interface used by the legacy UI layer.
 * It intentionally contains no sample users, fake requests, artificial delays,
 * or simulated connection outcomes. Real data must come from the LINKO control plane.
 */
class MockLinkShareRepository {
    fun friends(): List<Friend> = emptyList()

    fun incomingRequest(): IncomingRequest? = null

    suspend fun requestHostAccess(friendId: String): Boolean = false

    suspend fun performWireGuardStyleHandshake(): Boolean = false

    suspend fun retryHandshakeOnWeakSignal(): Boolean = false
}
