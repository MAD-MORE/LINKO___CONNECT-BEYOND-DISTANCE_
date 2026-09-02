package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.linkshare.app.Screen
import com.linkshare.app.activeNavTab
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.*
import com.linkshare.app.onboardingScreens
import com.linkshare.app.ui.components.NavBadge
import com.linkshare.app.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LinkoApp(auth: LinkoAuth, runtime: LinkoRuntime, updateManager: com.linkshare.app.update.LinkoUpdateManager? = null) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val signedIn = auth.isSignedIn()
    val route = entry?.destination?.route ?: if (signedIn) Screen.HomeEngine.route else Screen.Welcome.route
    val onboarding = route in onboardingScreens
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var splashShowing by remember { mutableStateOf(true) }
    var startupProgress by remember { mutableStateOf("Initializing Cryptographic Keystore…") }
    var bootstrapFailed by remember { mutableStateOf(false) }
    var offlineBypass by remember { mutableStateOf(false) }

    val onDeleteAccount: () -> Unit = {
        if (!deleting) {
            deleting = true
            scope.launch {
                withContext(Dispatchers.IO) { auth.signOut() }
                deleting = false
                nav.navigate(Screen.Welcome.route) { popUpTo(Screen.Welcome.route) { inclusive = true } }
            }
        }
    }

    LaunchedEffect(Unit) {
        val ok = withContext(Dispatchers.IO) { runCatching { runtime.initialize { startupProgress = it } }.getOrDefault(true) }
        if (!ok && signedIn && !auth.isSignedIn()) bootstrapFailed = true
        splashShowing = false
    }

    if ((splashShowing || bootstrapFailed) && !offlineBypass) {
        StartupSplashScreen(
            statusMessage = startupProgress,
            failed = bootstrapFailed,
            onRetry = { bootstrapFailed = false; splashShowing = true; scope.launch { val ok = withContext(Dispatchers.IO) { runCatching { runtime.initialize { startupProgress = it } }.getOrDefault(true) }; bootstrapFailed = !ok; splashShowing = false } },
            onContinueOffline = { offlineBypass = true; splashShowing = false; bootstrapFailed = false },
            onSignOut = { scope.launch { withContext(Dispatchers.IO) { auth.signOut() }; nav.navigate(Screen.Welcome.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } } }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(GradientTop, GradientMid))).systemBarsPadding()) {
        if (!onboarding && route != Screen.HomeEngine.route) AppBar(appBarTitle(route) ?: "LINKO") { nav.popBackStack() }
        Box(Modifier.weight(1f)) {
            NavHost(navController = nav, startDestination = if (signedIn) Screen.HomeEngine.route else Screen.Welcome.route) {
                composable(Screen.Welcome.route) { WelcomeScreen({ nav.navigate(Screen.SignUp.route) }, { nav.navigate(Screen.SignIn.route) }) }
                composable(Screen.SignUp.route) { LinkoSignUpScreen(auth) { nav.navigate(Screen.HomeEngine.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } } }
                composable(Screen.SignIn.route) { SignInScreen(auth, onSignedIn = { splashShowing = true; bootstrapFailed = false; scope.launch { val ok = withContext(Dispatchers.IO) { runCatching { runtime.initialize() }.getOrDefault(true) }; bootstrapFailed = !ok && !auth.isSignedIn(); splashShowing = false; nav.navigate(Screen.HomeEngine.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } } }, onCreateAccount = { nav.navigate(Screen.SignUp.route) }, onForgotPassword = { nav.navigate(Screen.ForgotPassword.route) }) }
                composable(Screen.ForgotPassword.route) { ForgotPasswordScreen(auth, onCodeSent = { nav.navigate(Screen.RecoveryOtp.route) }, onBack = { nav.popBackStack() }) }
                composable(Screen.RecoveryOtp.route) { RecoveryOtpScreen(auth, onVerified = { nav.navigate(Screen.PasswordReset.route) }, onBack = { nav.popBackStack() }) }
                composable(Screen.PasswordReset.route) { PasswordResetScreen(auth) { auth.signOut(); nav.navigate(Screen.SignIn.route) } }
                composable(Screen.Profile.route) { RealAccountProfileScreen { nav.popBackStack() } }
                composable(Screen.RegisterDevice.route) { RegisterDeviceScreen { nav.navigate(Screen.Permissions.route) } }
                composable(Screen.Permissions.route) { PermissionsScreen { nav.navigate(Screen.HomeEngine.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } } }

                // Home Connect opens the Friends tab. The Friends tab owns the friend list and connection entry point.
                composable(Screen.HomeEngine.route) { HomeEngineScreen({ nav.navigate(Screen.Friends.route) }, { nav.navigate(Screen.ProviderReady.route) }) }

                // Friends tab shows existing friends, requests and request history. Add Friends opens Radar Discovery.
                composable(Screen.Friends.route) {
                    FriendsScreen(
                        onFindFriends = { nav.navigate(Screen.FindFriends.route) },
                        onFriendTap = { nav.navigate(Screen.FriendProfile.route) }
                    )
                }

                // Dedicated friend-search radar reached from + FIND FRIENDS.
                composable(Screen.FindFriends.route) { FindFriendsScreen { nav.navigate(Screen.FriendProfile.route) } }
                composable(Screen.FriendProfile.route) { RealFriendProfileScreen({ nav.navigate(Screen.RequestSent.route) }, { nav.navigate(Screen.RxSelectFriend.route) }) }
                composable(Screen.RequestSent.route) { RequestSentScreen { nav.navigate(Screen.Friends.route) } }
                composable(Screen.IncomingRequest.route) { IncomingRequestScreen({ nav.navigate(Screen.Friends.route) }, { nav.popBackStack() }) }
                composable(Screen.BlockedRemoved.route) { BlockedRemovedScreen { nav.popBackStack() } }
                composable(Screen.RxSelectFriend.route) { RxSelectFriendScreen { nav.navigate(Screen.RxConnecting.route) } }
                composable(Screen.RxRequest.route) { RxRequestScreen { nav.popBackStack() } }
                composable(Screen.RxWaiting.route) { RxWaitingScreen { nav.popBackStack() } }
                composable(Screen.RxApproved.route) { RxApprovedScreen { nav.navigate(Screen.RxConnecting.route) } }
                composable(Screen.RxConnecting.route) { ConnectionStatusScreen(onConnected = { nav.navigate(Screen.Connected.route) }, onFailed = { nav.navigate(Screen.ConnectionLost.route) }) }
                composable(Screen.RxDirectPath.route) { RxDirectPathScreen { nav.navigate(Screen.Connected.route) } }
                composable(Screen.RxRelayFallback.route) { RxRelayFallbackScreen { nav.navigate(Screen.Connected.route) } }
                composable(Screen.Connected.route) { ConnectedScreen({ nav.navigate(Screen.HomeEngine.route) { popUpTo(Screen.HomeEngine.route) { inclusive = true } } }, { nav.navigate(Screen.NetworkQuality.route) }) }
                composable(Screen.NetworkQuality.route) { NetworkQualityScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.Usage.route) { UsageScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.SessionDetails.route) { SessionDetailsScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.SessionHistory.route) { SessionHistoryScreen() }
                composable(Screen.Notifications.route) { NotificationsScreen() }
                composable(Screen.ProviderReady.route) { ProviderReadyScreen { nav.navigate(Screen.ProviderIncoming.route) } }
                composable(Screen.ProviderIncoming.route) { ProviderIncomingScreen({ nav.navigate(Screen.ProviderAuthorization.route) }, { LinkoEngineBridge.denyPendingProviderRequest(); nav.popBackStack() }) }
                composable(Screen.ProviderAuthorization.route) { ProviderAuthorizationScreen { LinkoEngineBridge.approvePendingProviderRequest { if (it == "approved") nav.navigate(Screen.ProviderSharingSetup.route) } } }
                composable(Screen.ProviderSharingSetup.route) { ProviderSharingSetupScreen { LinkoEngineBridge.startApprovedProviderSession { if (it == "starting") nav.navigate(Screen.ProviderSharingActive.route) } } }
                composable(Screen.ProviderSharingActive.route) { ProviderSharingActiveScreen({ nav.navigate(Screen.ProviderLiveUsage.route) }, { LinkoEngineBridge.disconnect(); nav.navigate(Screen.HomeEngine.route) }) }
                composable(Screen.ProviderLiveUsage.route) { ProviderLiveUsageScreen { LinkoEngineBridge.disconnect(); nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.ConnectionLost.route) { ConnectionLostScreen({ nav.navigate(Screen.Reconnecting.route) }, { nav.navigate(Screen.HomeEngine.route) }) }
                composable(Screen.Reconnecting.route) { RealReconnectingScreen(onConnected = { nav.navigate(Screen.Connected.route) }, onFailed = { nav.navigate(Screen.ConnectionLost.route) }) }
                composable(Screen.NetworkSwitching.route) { NetworkSwitchingScreen { nav.navigate(Screen.Connected.route) } }
                composable(Screen.SessionExpired.route) { SessionExpiredScreen({ nav.navigate(Screen.RxSelectFriend.route) }, { nav.navigate(Screen.HomeEngine.route) }) }
                composable(Screen.KeyRevoked.route) { KeyRevokedScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.Settings.route) { SettingsScreen({ nav.navigate(Screen.Profile.route) }, { nav.navigate(Screen.DeviceIdentity.route) }, { nav.navigate(Screen.Friends.route) }, { nav.navigate(Screen.BlockedRemoved.route) }, { nav.navigate(Screen.SessionHistory.route) }, { nav.navigate(Screen.SecurityEngine.route) }, { nav.navigate(Screen.Privacy.route) }, { nav.navigate(Screen.DataRetention.route) }, { nav.navigate(Screen.DeleteAccount.route) }) }
                composable(Screen.DeviceIdentity.route) { DeviceIdentityScreen { nav.popBackStack() } }
                composable(Screen.SecurityEngine.route) { SecurityEngineScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.Privacy.route) { PrivacyScreen { nav.navigate(Screen.DataRetention.route) } }
                composable(Screen.DataRetention.route) { DataRetentionScreen { nav.popBackStack() } }
                composable(Screen.DeleteAccount.route) { DeleteAccountScreen(onDeleteAccount) { nav.popBackStack() } }
            }
        }
        if (!onboarding) BottomNav(route, nav)
    }
}

@Composable private fun AppBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(Brush.verticalGradient(listOf(GradientTop.copy(alpha = .95f), GradientMid.copy(alpha = 0f)))), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(22.dp)) }
        Text(title, color = TextPrimary, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

private fun appBarTitle(route: String): String? = mapOf("sign_in" to "Sign In", "sign_up" to "Create Account", "forgot_password" to "Forgot Password", "recovery_otp" to "Verify Recovery", "password_reset" to "New Password", "profile" to "Profile", "register_device" to "Register Device", "permissions" to "Permissions", "friends" to "Friends", "find_friends" to "Friends", "friend_profile" to "Friend Profile", "request_sent" to "Request Sent", "incoming_request" to "Incoming Request", "blocked_removed" to "Trust Boundaries", "rx_select_friend" to "Choose Friend", "rx_request" to "Connection Request", "rx_waiting" to "Waiting", "rx_approved" to "Approved", "rx_connecting" to "Connecting", "rx_direct_path" to "Direct Path", "rx_relay_fallback" to "Connection Path", "connected" to "Connected", "network_quality" to "Network Quality", "usage" to "Usage", "session_details" to "Session", "session_history" to "Session History", "notifications" to "Notifications", "provider_ready" to "Provider Ready", "provider_incoming" to "Incoming Request", "provider_authorization" to "Authorize", "provider_sharing_setup" to "Sharing Setup", "provider_sharing_active" to "Sharing Active", "provider_live_usage" to "Live Usage", "connection_lost" to "Connection Lost", "reconnecting" to "Reconnecting", "network_switching" to "Network Switch", "session_expired" to "Session Expired", "key_revoked" to "Device Session Ended", "device_identity" to "Device Identity", "security_engine" to "Security Engine", "privacy" to "Privacy", "data_retention" to "Data Retention", "delete_account" to "Delete Account")[route]

private sealed class BottomNavItem(val label: String, val route: String, val iconFilled: ImageVector, val iconOutlined: ImageVector) {
    object Home : BottomNavItem("HOME", Screen.HomeEngine.route, Icons.Filled.Home, Icons.Outlined.Home)
    object Friends : BottomNavItem("FRIENDS", Screen.Friends.route, Icons.Filled.People, Icons.Outlined.People)
    object History : BottomNavItem("HISTORY", Screen.SessionHistory.route, Icons.Filled.History, Icons.Outlined.History)
    object Notifications : BottomNavItem("NOTIFICATIONS", Screen.Notifications.route, Icons.Filled.Notifications, Icons.Outlined.Notifications)
    object Settings : BottomNavItem("SETTINGS", Screen.Settings.route, Icons.Filled.Settings, Icons.Outlined.Settings)
}

private val bottomNavItems = listOf(BottomNavItem.Home, BottomNavItem.Friends, BottomNavItem.History, BottomNavItem.Notifications, BottomNavItem.Settings)

@Composable private fun BottomNav(route: String, nav: androidx.navigation.NavHostController) {
    val activeTab = activeNavTab(route)
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    var friendRequestCount by remember { mutableStateOf(0) }
    var hasIncomingConnection by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { LinkoRealtimeManager.events.collect { event -> when (event) {
        is LinkoRealtimeEvent.FriendRequestReceived -> friendRequestCount += 1
        is LinkoRealtimeEvent.FriendRequestAccepted, is LinkoRealtimeEvent.FriendRequestDeclined, is LinkoRealtimeEvent.FriendRemoved -> friendRequestCount = (friendRequestCount - 1).coerceAtLeast(0)
        is LinkoRealtimeEvent.IncomingConnectionRequest -> hasIncomingConnection = true
        is LinkoRealtimeEvent.SessionStateChanged -> hasIncomingConnection = event.state == "requested"
        else -> Unit
    } } }
    val isSharingLive = engineState.phase == LinkoConnectionPhase.Connected
    Surface(Modifier.fillMaxWidth().navigationBarsPadding(), color = GradientMid.copy(alpha = .96f), tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            bottomNavItems.forEach { item ->
                val selected = activeTab == item.label
                val activeColor = if (item == BottomNavItem.Friends) Green else Blue
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (selected) activeColor.copy(alpha = .13f) else androidx.compose.ui.graphics.Color.Transparent).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!selected) nav.navigate(item.route) { popUpTo(Screen.HomeEngine.route); launchSingleTop = true } }.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(if (selected) 22.dp else 0.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) activeColor else androidx.compose.ui.graphics.Color.Transparent))
                        Spacer(Modifier.height(3.dp))
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (selected) item.iconFilled else item.iconOutlined, item.label, tint = if (selected) activeColor else TextSub, modifier = Modifier.size(21.dp))
                            when (item) {
                                BottomNavItem.Notifications -> { val count = friendRequestCount + if (hasIncomingConnection) 1 else 0; if (count > 0) NavBadge(count = count, color = Red, modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-5).dp)) }
                                BottomNavItem.Home -> if (hasIncomingConnection) NavBadge(text = "⚡", color = Yellow, modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-5).dp)) else if (isSharingLive) NavBadge(text = "LIVE", color = Green, modifier = Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-5).dp))
                                BottomNavItem.Friends -> if (friendRequestCount > 0) NavBadge(count = friendRequestCount, color = Red, modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-5).dp))
                                else -> Unit
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(item.label, color = if (selected) activeColor else TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
