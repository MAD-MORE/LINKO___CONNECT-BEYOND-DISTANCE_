package com.linkshare.app.model

enum class AppMode { Host, Client }

enum class ConnectionPhase {
    Idle,
    Requesting,
    Handshaking,
    Retrying,
    Connected,
    Failed;

    companion object {
        /** Compatibility aliases for the richer engine state machine. */
        val Connecting: ConnectionPhase = Requesting
        val Authenticating: ConnectionPhase = Requesting
        val Signaling: ConnectionPhase = Requesting
        val Establishing: ConnectionPhase = Handshaking
        val Securing: ConnectionPhase = Handshaking
        val Routing: ConnectionPhase = Handshaking
    }
}

data class Friend(val id: String, val name: String, val initials: String, val cityHint: String, val trustNote: String, val isSharing: Boolean, val accentHex: Long)
data class IncomingRequest(val id: String, val friendName: String, val initials: String, val deviceName: String, val distanceLabel: String, val requestedAtLabel: String)
data class UsageStats(val bytesSent: Long = 0, val bytesReceived: Long = 0, val sessionSeconds: Long = 0, val connectedClients: Int = 0)
data class ConnectionUiState(
    val mode: AppMode = AppMode.Host,
    val hostSharingEnabled: Boolean = false,
    val connectionPhase: ConnectionPhase = ConnectionPhase.Idle,
    val activeFriend: Friend? = null,
    val retryAttempt: Int = 0,
    val hasVpnPermission: Boolean = false,
    val usageStats: UsageStats = UsageStats(),
    val friends: List<Friend> = emptyList(),
    val incomingRequest: IncomingRequest? = null,
    val eventMessage: String? = null,
    val failureReason: String? = null
)
