package com.linkshare.app.network

/** UI-facing states. Transport events only move the state; the backend remains authoritative. */
object LinkoStateMachine {
    enum class Friendship {
        NONE,
        OUTGOING_PENDING,
        INCOMING_PENDING,
        FRIEND,
        DECLINED,
    }

    enum class Availability {
        OFFLINE,
        ONLINE,
        READY,
        CONNECTING,
        SHARING,
        CONNECTED,
    }

    enum class Connection {
        IDLE,
        REQUESTED,
        APPROVED,
        SIGNALING,
        CONNECTING,
        CONNECTED,
        DENIED,
        REVOKED,
        EXPIRED,
        DISCONNECTED,
    }

    fun friendshipFromBackend(state: String?): Friendship = when (state?.trim()?.lowercase()) {
        "friend", "accepted" -> Friendship.FRIEND
        "outgoing_pending", "pending_outgoing" -> Friendship.OUTGOING_PENDING
        "incoming_pending", "pending_incoming" -> Friendship.INCOMING_PENDING
        "declined" -> Friendship.DECLINED
        else -> Friendship.NONE
    }

    fun availabilityFromPresence(state: String?, online: Boolean): Availability = when {
        !online || state.equals("offline", ignoreCase = true) -> Availability.OFFLINE
        state.equals("ready", ignoreCase = true) -> Availability.READY
        state.equals("sharing", ignoreCase = true) -> Availability.SHARING
        state.equals("connecting", ignoreCase = true) -> Availability.CONNECTING
        state.equals("connected", ignoreCase = true) -> Availability.CONNECTED
        else -> Availability.ONLINE
    }

    fun connectionFromBackend(state: String?): Connection = when (state?.trim()?.lowercase()) {
        "requested" -> Connection.REQUESTED
        "approved" -> Connection.APPROVED
        "signaling" -> Connection.SIGNALING
        "connecting" -> Connection.CONNECTING
        "connected" -> Connection.CONNECTED
        "denied" -> Connection.DENIED
        "revoked" -> Connection.REVOKED
        "expired" -> Connection.EXPIRED
        "disconnected", "failed" -> Connection.DISCONNECTED
        else -> Connection.IDLE
    }

    fun canRequestConnection(friendship: Friendship, availability: Availability): Boolean =
        friendship == Friendship.FRIEND && availability != Availability.OFFLINE

    fun isFriend(friendship: Friendship): Boolean = friendship == Friendship.FRIEND
}
