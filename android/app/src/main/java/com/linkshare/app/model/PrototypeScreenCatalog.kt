package com.linkshare.app.model

/**
 * Frozen LINKO prototype navigation contract.
 *
 * This catalog deliberately contains no visual styling. It gives the production
 * UI a stable, exhaustive state contract while the Compose implementation is
 * mapped screen-by-screen to the frozen prototype reference.
 */
data class PrototypeScreenDefinition(
    val screen: PrototypeScreen,
    val route: String,
    val group: PrototypeScreenGroup,
    val primaryFlow: Boolean = false
)

enum class PrototypeScreenGroup {
    Onboarding,
    FriendsTrust,
    Receiver,
    Provider,
    Sessions,
    SecurityPrivacy
}

object PrototypeScreenCatalog {
    val all: List<PrototypeScreenDefinition> = listOf(
        // Onboarding
        PrototypeScreenDefinition(PrototypeScreen.Welcome, "welcome", PrototypeScreenGroup.Onboarding),
        PrototypeScreenDefinition(PrototypeScreen.CreateAccount, "create-account", PrototypeScreenGroup.Onboarding),
        PrototypeScreenDefinition(PrototypeScreen.Verify, "verify", PrototypeScreenGroup.Onboarding),
        PrototypeScreenDefinition(PrototypeScreen.Profile, "profile", PrototypeScreenGroup.Onboarding),
        PrototypeScreenDefinition(PrototypeScreen.RegisterDevice, "register-device", PrototypeScreenGroup.Onboarding),
        PrototypeScreenDefinition(PrototypeScreen.Permissions, "permissions", PrototypeScreenGroup.Onboarding),

        // Friends / trust
        PrototypeScreenDefinition(PrototypeScreen.Friends, "friends", PrototypeScreenGroup.FriendsTrust),
        PrototypeScreenDefinition(PrototypeScreen.FindFriends, "find-friends", PrototypeScreenGroup.FriendsTrust),
        PrototypeScreenDefinition(PrototypeScreen.FriendProfile, "friend-profile", PrototypeScreenGroup.FriendsTrust),
        PrototypeScreenDefinition(PrototypeScreen.RequestSent, "request-sent", PrototypeScreenGroup.FriendsTrust),
        PrototypeScreenDefinition(PrototypeScreen.IncomingRequest, "incoming-request", PrototypeScreenGroup.FriendsTrust),
        PrototypeScreenDefinition(PrototypeScreen.BlockedRemoved, "blocked-removed", PrototypeScreenGroup.FriendsTrust),

        // Receiver
        PrototypeScreenDefinition(PrototypeScreen.HomeEngine, "home-engine", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.RxSelectFriend, "rx-select-friend", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.RxRequest, "rx-request", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.RxWaiting, "rx-waiting", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.RxApproved, "rx-approved", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.RxConnecting, "rx-connecting", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.RxDirectPath, "rx-direct-path", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.RxRelayFallback, "rx-relay-fallback", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.Connected, "connected", PrototypeScreenGroup.Receiver, true),
        PrototypeScreenDefinition(PrototypeScreen.NetworkQuality, "network-quality", PrototypeScreenGroup.Receiver),
        PrototypeScreenDefinition(PrototypeScreen.Usage, "usage", PrototypeScreenGroup.Receiver),

        // Provider
        PrototypeScreenDefinition(PrototypeScreen.ProviderIncoming, "provider-incoming", PrototypeScreenGroup.Provider, true),
        PrototypeScreenDefinition(PrototypeScreen.ProviderAuthorization, "provider-authorization", PrototypeScreenGroup.Provider, true),
        PrototypeScreenDefinition(PrototypeScreen.ProviderSharingSetup, "provider-sharing-setup", PrototypeScreenGroup.Provider, true),
        PrototypeScreenDefinition(PrototypeScreen.ProviderSharingActive, "provider-sharing-active", PrototypeScreenGroup.Provider, true),
        PrototypeScreenDefinition(PrototypeScreen.ProviderLiveUsage, "provider-live-usage", PrototypeScreenGroup.Provider, true),

        // Sessions / failures
        PrototypeScreenDefinition(PrototypeScreen.SessionDetails, "session-details", PrototypeScreenGroup.Sessions),
        PrototypeScreenDefinition(PrototypeScreen.SessionHistory, "session-history", PrototypeScreenGroup.Sessions),
        PrototypeScreenDefinition(PrototypeScreen.ConnectionLost, "connection-lost", PrototypeScreenGroup.Sessions),
        PrototypeScreenDefinition(PrototypeScreen.Reconnecting, "reconnecting", PrototypeScreenGroup.Sessions),
        PrototypeScreenDefinition(PrototypeScreen.NetworkSwitching, "network-switching", PrototypeScreenGroup.Sessions),
        PrototypeScreenDefinition(PrototypeScreen.SessionExpired, "session-expired", PrototypeScreenGroup.Sessions),

        // Device / security / privacy
        PrototypeScreenDefinition(PrototypeScreen.DeviceIdentity, "device-identity", PrototypeScreenGroup.SecurityPrivacy),
        PrototypeScreenDefinition(PrototypeScreen.SecurityEngine, "security-engine", PrototypeScreenGroup.SecurityPrivacy),
        PrototypeScreenDefinition(PrototypeScreen.KeyRevoked, "key-revoked", PrototypeScreenGroup.SecurityPrivacy),
        PrototypeScreenDefinition(PrototypeScreen.Privacy, "privacy", PrototypeScreenGroup.SecurityPrivacy),
        PrototypeScreenDefinition(PrototypeScreen.DataRetention, "data-retention", PrototypeScreenGroup.SecurityPrivacy),
        PrototypeScreenDefinition(PrototypeScreen.Settings, "settings", PrototypeScreenGroup.SecurityPrivacy),
        PrototypeScreenDefinition(PrototypeScreen.DeleteAccount, "delete-account", PrototypeScreenGroup.SecurityPrivacy)
    )

    private val byScreen = all.associateBy { it.screen }
    private val byRoute = all.associateBy { it.route }

    fun definition(screen: PrototypeScreen): PrototypeScreenDefinition =
        byScreen.getValue(screen)

    fun screenForRoute(route: String): PrototypeScreen? =
        byRoute[route]?.screen

    fun primaryFlow(): List<PrototypeScreenDefinition> =
        all.filter { it.primaryFlow }
}
