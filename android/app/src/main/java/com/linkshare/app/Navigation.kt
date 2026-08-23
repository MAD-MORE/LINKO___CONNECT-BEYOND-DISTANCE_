package com.linkshare.app

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object SignIn : Screen("sign_in")
    object SignUp : Screen("sign_up")
    object ForgotPassword : Screen("forgot_password")
    object SignupOtp : Screen("signup_otp")
    object RecoveryOtp : Screen("recovery_otp")
    object PasswordReset : Screen("password_reset")
    object CreateAccount : Screen("create_account")
    object Verify : Screen("verify")
    object Profile : Screen("profile")
    object RegisterDevice : Screen("register_device")
    object Permissions : Screen("permissions")
    object Friends : Screen("friends")
    object FindFriends : Screen("find_friends")
    object FriendProfile : Screen("friend_profile")
    object RequestSent : Screen("request_sent")
    object IncomingRequest : Screen("incoming_request")
    object BlockedRemoved : Screen("blocked_removed")
    object HomeEngine : Screen("home_engine")
    object RxSelectFriend : Screen("rx_select_friend")
    object RxRequest : Screen("rx_request")
    object RxWaiting : Screen("rx_waiting")
    object RxApproved : Screen("rx_approved")
    object RxConnecting : Screen("rx_connecting")
    object RxDirectPath : Screen("rx_direct_path")
    object RxRelayFallback : Screen("rx_relay_fallback")
    object Connected : Screen("connected")
    object NetworkQuality : Screen("network_quality")
    object Usage : Screen("usage")
    object SessionDetails : Screen("session_details")
    object SessionHistory : Screen("session_history")
    object ProviderReady : Screen("provider_ready")
    object ProviderIncoming : Screen("provider_incoming")
    object ProviderAuthorization : Screen("provider_authorization")
    object ProviderSharingSetup : Screen("provider_sharing_setup")
    object ProviderSharingActive : Screen("provider_sharing_active")
    object ProviderLiveUsage : Screen("provider_live_usage")
    object ConnectionLost : Screen("connection_lost")
    object Reconnecting : Screen("reconnecting")
    object NetworkSwitching : Screen("network_switching")
    object SessionExpired : Screen("session_expired")
    object DeviceIdentity : Screen("device_identity")
    object SecurityEngine : Screen("security_engine")
    object KeyRevoked : Screen("key_revoked")
    object Privacy : Screen("privacy")
    object DataRetention : Screen("data_retention")
    object Settings : Screen("settings")
    object DeleteAccount : Screen("delete_account")
}

val onboardingScreens = setOf(Screen.Welcome.route, Screen.SignIn.route, Screen.SignUp.route, Screen.ForgotPassword.route, Screen.SignupOtp.route, Screen.RecoveryOtp.route, Screen.PasswordReset.route, Screen.CreateAccount.route, Screen.Verify.route, Screen.Profile.route, Screen.RegisterDevice.route, Screen.Permissions.route)
val bottomNavScreens = setOf(Screen.HomeEngine.route, Screen.Friends.route, Screen.SessionHistory.route, Screen.Settings.route)
fun activeNavTab(route: String?): String = when {
    route == null -> "HOME"
    route.startsWith("home") || route.startsWith("rx") || route.startsWith("connected") || route.startsWith("network") || route.startsWith("usage") || route.startsWith("session_details") || route.startsWith("provider") || route.startsWith("connection") || route.startsWith("reconnect") -> "HOME"
    route.startsWith("friend") || route.startsWith("find") || route.startsWith("request") || route.startsWith("incoming") || route.startsWith("blocked") -> "FRIENDS"
    route == Screen.SessionHistory.route -> "HISTORY"
    route.startsWith("settings") || route.startsWith("privacy") || route.startsWith("data") || route.startsWith("device") || route.startsWith("security") || route.startsWith("key") || route.startsWith("delete") -> "SETTINGS"
    else -> "HOME"
}
