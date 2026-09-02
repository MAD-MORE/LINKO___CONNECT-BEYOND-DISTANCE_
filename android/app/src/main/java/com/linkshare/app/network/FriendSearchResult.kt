package com.linkshare.app.network

/** A real LINKO account returned by friend discovery or friend-list APIs. */
data class FriendSearchResult(
    val userId: String,
    val linkoId: String,
    val displayName: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val isSharing: Boolean = false,
    val isOnline: Boolean = false,
    val relationshipStatus: String = "none",
    val requestId: String? = null,
    val username: String? = null,
)
