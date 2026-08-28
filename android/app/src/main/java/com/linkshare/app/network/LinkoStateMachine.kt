package com.linkshare.app.network

/** UI-facing state machine. Transport and protocol events transition the state. */
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
        AUTHORIZED,
        SIGNALING,
        HANDSHAKING,
        TUNNEL_ESTABLISHED,
        TRAFFIC_ACTIVE,
        DISCONNECTING,
        CONNECTED,
        DENIED,
        REVOKED,
        EXPIRED,
        DISCONNECTED,
        AUTH_FAILED,
        SESSION_EXPIRED,
        HANDSHAKE_FAILED,
        DIRECT_PATH_FAILED,
        RELAY_FAILED,
        NETWORK_LOST,
        PROVIDER_OFFLINE,
        CLIENT_OFFLINE,
        TIMEOUT,
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
        "approved", "authorized" -> Connection.AUTHORIZED
        "signaling" -> Connection.SIGNALING
        "handshaking" -> Connection.HANDSHAKING
        "tunnel_established" -> Connection.TUNNEL_ESTABLISHED
        "traffic_active" -> Connection.TRAFFIC_ACTIVE
        "connecting" -> Connection.HANDSHAKING
        "connected" -> Connection.CONNECTED
        "denied" -> Connection.DENIED
        "revoked" -> Connection.REVOKED
        "expired" -> Connection.EXPIRED
        "auth_failed" -> Connection.AUTH_FAILED
        "session_expired" -> Connection.SESSION_EXPIRED
        "handshake_failed" -> Connection.HANDSHAKE_FAILED
        "relay_failed" -> Connection.RELAY_FAILED
        "network_lost" -> Connection.NETWORK_LOST
        "provider_offline" -> Connection.PROVIDER_OFFLINE
        "timeout" -> Connection.TIMEOUT
        "disconnecting" -> Connection.DISCONNECTING
        "disconnected", "failed" -> Connection.DISCONNECTED
        else -> Connection.IDLE
    }

    fun canRequestConnection(friendship: Friendship, availability: Availability): Boolean =
        friendship == Friendship.FRIEND && availability != Availability.OFFLINE

    fun isFriend(friendship: Friendship): Boolean = friendship == Friendship.FRIEND
}
