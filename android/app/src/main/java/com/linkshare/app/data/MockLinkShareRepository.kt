package com.linkshare.app.data

import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import kotlinx.coroutines.delay

class MockLinkShareRepository {
    fun friends(): List<Friend> = listOf(
        Friend(
            id = "nora",
            name = "Nora",
            initials = "NO",
            cityHint = "Across town",
            trustNote = "Shared with you until 9:30 PM",
            isSharing = true,
            accentHex = 0xFF66BFB5
        ),
        Friend(
            id = "kwesi",
            name = "Kwesi",
            initials = "KW",
            cityHint = "Kumasi",
            trustNote = "Available when he approves",
            isSharing = true,
            accentHex = 0xFFE2A15F
        ),
        Friend(
            id = "mina",
            name = "Mina",
            initials = "MI",
            cityHint = "Last shared yesterday",
            trustNote = "Not sharing right now",
            isSharing = false,
            accentHex = 0xFF8794A3
        )
    )

    fun incomingRequest(): IncomingRequest = IncomingRequest(
        id = "request-kwesi",
        friendName = "Kwesi",
        initials = "KW",
        deviceName = "Pixel 8",
        distanceLabel = "About 162 km away",
        requestedAtLabel = "Now"
    )

    suspend fun requestHostAccess(friendId: String): Boolean {
        delay(850)
        return friendId != "mina"
    }

    suspend fun performWireGuardStyleHandshake(): Boolean {
        delay(1_200)
        return false
    }

    suspend fun retryHandshakeOnWeakSignal(): Boolean {
        delay(1_500)
        return true
    }
}
