package com.linkshare.app.model

enum class AppMode {
    Host,
    Client
}

enum class ConnectionPhase {
    Idle,
    Requesting,
    Handshaking,
    Connected,
    Retrying,
    Failed
}

enum class PrototypeScreen {
    Welcome,
    CreateAccount,
    Verify,
    Profile,
    RegisterDevice,
    Permissions,
    Friends,
    FindFriends,
    FriendProfile,
    RequestSent,
    IncomingRequest,
    BlockedRemoved,
    HomeEngine,
    RxSelectFriend,
    RxRequest,
    RxWaiting,
    RxApproved,
    RxConnecting,
    RxDirectPath,
    RxRelayFallback,
    Connected,
    NetworkQuality,
    Usage,
    ProviderIncoming,
    ProviderAuthorization,
    ProviderSharingSetup,
    ProviderSharingActive,
    ProviderLiveUsage,
    SessionDetails,
    SessionHistory,
    ConnectionLost,
    Reconnecting,
    NetworkSwitching,
    SessionExpired,
    DeviceIdentity,
    SecurityEngine,
    KeyRevoked,
    Privacy,
    DataRetention,
    Settings,
    DeleteAccount
}

data class Friend(
    val id: String,
    val name: String,
    val initials: String,
    val cityHint: String,
    val trustNote: String,
    val isSharing: Boolean,
    val accentHex: Long,
    val deviceName: String = "Unknown Device",
    val distanceLabel: String = "Remote"
)

data class IncomingRequest(
    val id: String,
    val friendName: String,
    val initials: String,
    val deviceName: String,
    val distanceLabel: String,
    val requestedAtLabel: String
)

data class UsageStats(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val sessionSeconds: Long = 0,
    val connectedClients: Int = 0
)

data class ConnectionUiState(
    val mode: AppMode = AppMode.Host,
    val screen: PrototypeScreen = PrototypeScreen.Welcome,
    val hostSharingEnabled: Boolean = false,
    val connectionPhase: ConnectionPhase = ConnectionPhase.Idle,
    val activeFriend: Friend? = null,
    val retryAttempt: Int = 0,
    val hasVpnPermission: Boolean = false,
    val usageStats: UsageStats = UsageStats(),
    val friends: List<Friend> = emptyList(),
    val incomingRequest: IncomingRequest? = null,
    val eventMessage: String? = null,
    val sessionId: String = "",
    val peerId: String = "",
    val sessionKey: ByteArray = ByteArray(32),
    val relayEndpoint: String = "",
    val relayToken: String = ""
)
