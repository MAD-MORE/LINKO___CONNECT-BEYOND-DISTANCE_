package com.linkshare.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkshare.app.Screen
import com.linkshare.app.activeNavTab
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.onboardingScreens
import com.linkshare.app.ui.components.GhostButton
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.BG
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Border
import com.linkshare.app.ui.theme.Card
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LinkoApp(auth: LinkoAuth, runtime: LinkoRuntime) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val signedIn = auth.isSignedIn()
    val route = entry?.destination?.route ?: if (signedIn) Screen.HomeEngine.route else Screen.Welcome.route
    val onboarding = route in onboardingScreens
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var splashShowing by remember { mutableStateOf(true) }
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
        val start = System.currentTimeMillis()
        val ok = withContext(Dispatchers.IO) {
            runCatching { runtime.initialize() }.getOrDefault(true)
        }
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < 2200) {
            kotlinx.coroutines.delay(2200 - elapsed)
        }
        if (!ok && signedIn && !auth.isSignedIn()) {
            bootstrapFailed = true
        }
        splashShowing = false
    }

    if ((splashShowing || bootstrapFailed) && !offlineBypass) {
        StartupSplashScreen(
            failed = bootstrapFailed,
            onRetry = {
                bootstrapFailed = false
                splashShowing = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { runtime.initialize() }.getOrDefault(true) }
                    kotlinx.coroutines.delay(1200)
                    bootstrapFailed = !ok
                    splashShowing = false
                }
            },
            onContinueOffline = {
                offlineBypass = true
                splashShowing = false
                bootstrapFailed = false
            },
            onSignOut = {
                scope.launch {
                    withContext(Dispatchers.IO) { auth.signOut() }
                    nav.navigate(Screen.Welcome.route) { popUpTo(Screen.Welcome.route) { inclusive = true } }
                }
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        if (!onboarding && route != Screen.HomeEngine.route) AppBar(appBarTitle(route) ?: "LINKO") { nav.popBackStack() }
        Box(Modifier.weight(1f)) {
            NavHost(navController = nav, startDestination = if (signedIn) Screen.HomeEngine.route else Screen.Welcome.route) {
                composable(Screen.Welcome.route) { WelcomeScreen({ nav.navigate(Screen.SignUp.route) }, { nav.navigate(Screen.SignIn.route) }) }
                composable(Screen.SignUp.route) { LinkoSignUpScreen(auth) { nav.navigate(Screen.HomeEngine.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } } }
                composable(Screen.SignIn.route) {
                    SignInScreen(
                        auth,
                        onSignedIn = {
                            bootstrapping = true
                            bootstrapFailed = false
                            scope.launch {
                                val initialized = withContext(Dispatchers.IO) { runCatching { runtime.initialize() }.getOrDefault(true) }
                                bootstrapFailed = !initialized && !auth.isSignedIn()
                                bootstrapping = false
                                nav.navigate(Screen.HomeEngine.route) { popUpTo(Screen.Welcome.route) { inclusive = true } }
                            }
                        },
                        onCreateAccount = { nav.navigate(Screen.SignUp.route) },
                        onForgotPassword = { nav.navigate(Screen.ForgotPassword.route) }
                    )
                }
                composable(Screen.ForgotPassword.route) { ForgotPasswordScreen(auth, onCodeSent = { nav.navigate(Screen.RecoveryOtp.route) }, onBack = { nav.navigate(Screen.SignIn.route) { popUpTo(Screen.ForgotPassword.route) { inclusive = true } } }) }
                composable(Screen.RecoveryOtp.route) { RecoveryOtpScreen(auth, onVerified = { nav.navigate(Screen.PasswordReset.route) { popUpTo(Screen.RecoveryOtp.route) { inclusive = true } } }, onBack = { nav.popBackStack() }) }
                composable(Screen.PasswordReset.route) { PasswordResetScreen(auth) { auth.signOut(); nav.navigate(Screen.SignIn.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } } }
                composable(Screen.Profile.route) { RealAccountProfileScreen { nav.popBackStack() } }
                composable(Screen.RegisterDevice.route) { RegisterDeviceScreen { nav.navigate(Screen.Permissions.route) } }
                composable(Screen.Permissions.route) { PermissionsScreen { nav.navigate(Screen.HomeEngine.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } } }
                composable(Screen.HomeEngine.route) { HomeEngineScreen({ nav.navigate(Screen.RxSelectFriend.route) }, { nav.navigate(Screen.ProviderReady.route) }) }
                composable(Screen.ProviderReady.route) { ProviderReadyScreen { nav.navigate(Screen.ProviderIncoming.route) } }
                composable(Screen.Friends.route) { LiveFriendsScreen({ nav.navigate(Screen.FindFriends.route) }, { nav.navigate(Screen.FriendProfile.route) }) }
                composable(Screen.FindFriends.route) { FindFriendsScreen { nav.navigate(Screen.FriendProfile.route) } }
                composable(Screen.FriendProfile.route) { RealFriendProfileScreen({ nav.navigate(Screen.RequestSent.route) }, { nav.navigate(Screen.Connected.route) }) }
                composable(Screen.RequestSent.route) { RequestSentScreen { nav.popBackStack() } }
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
                composable(Screen.ProviderIncoming.route) { ProviderIncomingScreen({ nav.navigate(Screen.ProviderAuthorization.route) }, { LinkoEngineBridge.denyPendingProviderRequest(); nav.popBackStack() }) }
                composable(Screen.ProviderAuthorization.route) { ProviderAuthorizationScreen { LinkoEngineBridge.approvePendingProviderRequest { state -> if (state == "approved") nav.navigate(Screen.ProviderSharingSetup.route) } } }
                composable(Screen.ProviderSharingSetup.route) { ProviderSharingSetupScreen { LinkoEngineBridge.startApprovedProviderSession { state -> if (state == "starting") nav.navigate(Screen.ProviderSharingActive.route) } } }
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

@Composable
private fun LinkoStartupScreen(
    failed: Boolean,
    onRetry: () -> Unit = {},
    onContinueOffline: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(BG).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            if (!failed) {
                CircularProgressIndicator(color = Blue, strokeWidth = 3.dp)
                Spacer(Modifier.height(20.dp))
                Text("INITIALIZING LINKO", color = TextPrimary, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Loading your profile, device identity and connection services…", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
            } else {
                Text("OFFLINE OR SYNC DELAYED", color = TextPrimary, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Could not reach the server right now. You can continue offline using your cached profile.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(24.dp))
                PrimaryButton("CONTINUE OFFLINE", onContinueOffline, color = Blue)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("RETRY SYNC", onRetry, outline = true)
                Spacer(Modifier.height(10.dp))
                GhostButton("Sign Out", onSignOut)
            }
        }
    }
}

@Composable private fun AppBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onBack) { Text("‹", color = TextPrimary, fontSize = 28.sp) }
        Text(title, color = TextPrimary, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

private fun appBarTitle(route: String): String? = titles[route]

private val titles = mapOf(
    "sign_in" to "Sign In", "sign_up" to "Create Account", "forgot_password" to "Forgot Password",
    "recovery_otp" to "Verify Recovery", "password_reset" to "New Password", "profile" to "Profile",
    "register_device" to "Register Device", "permissions" to "Permissions", "friends" to "Friends",
    "find_friends" to "Find Friends", "friend_profile" to "Friend Profile", "request_sent" to "Request Sent",
    "incoming_request" to "Incoming Request", "blocked_removed" to "Trust Boundaries",
    "rx_select_friend" to "Choose Friend", "rx_request" to "Connection Request", "rx_waiting" to "Waiting",
    "rx_approved" to "Approved", "rx_connecting" to "Connecting", "rx_direct_path" to "Direct Path",
    "rx_relay_fallback" to "Relay Fallback", "connected" to "Connected", "network_quality" to "Network Quality",
    "usage" to "Usage", "session_details" to "Session", "session_history" to "Session History",
    "provider_ready" to "Provider Ready", "provider_incoming" to "Incoming Request",
    "provider_authorization" to "Authorize", "provider_sharing_setup" to "Sharing Setup",
    "provider_sharing_active" to "Sharing Active", "provider_live_usage" to "Live Usage",
    "connection_lost" to "Connection Lost", "reconnecting" to "Reconnecting", "network_switching" to "Network Switch",
    "session_expired" to "Session Expired", "key_revoked" to "Device Session Ended",
    "device_identity" to "Device Identity", "security_engine" to "Security Engine", "privacy" to "Privacy",
    "data_retention" to "Data Retention", "delete_account" to "Delete Account"
)

private sealed class BottomNavItem(
    val label: String,
    val route: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector
) {
    object Home : BottomNavItem("HOME", Screen.HomeEngine.route, Icons.Filled.Home, Icons.Outlined.Home)
    object Friends : BottomNavItem("FRIENDS", Screen.Friends.route, Icons.Filled.People, Icons.Outlined.People)
    object History : BottomNavItem("HISTORY", Screen.SessionHistory.route, Icons.Filled.History, Icons.Outlined.History)
    object Settings : BottomNavItem("SETTINGS", Screen.Settings.route, Icons.Filled.Settings, Icons.Outlined.Settings)
}

private val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Friends,
    BottomNavItem.History,
    BottomNavItem.Settings
)

@Composable
private fun BottomNav(route: String, nav: androidx.navigation.NavHostController) {
    val activeTab = activeNavTab(route)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Card,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            bottomNavItems.forEach { item ->
                val selected = activeTab == item.label
                val activeColor = if (item == BottomNavItem.Friends) Green else Blue
                val bgModifier = if (selected) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(activeColor.copy(alpha = 0.12f))
                } else {
                    Modifier.clip(RoundedCornerShape(12.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(bgModifier)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!selected) {
                                nav.navigate(item.route) {
                                    popUpTo(Screen.HomeEngine.route)
                                    launchSingleTop = true
                                }
                            }
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selected) item.iconFilled else item.iconOutlined,
                            contentDescription = item.label,
                            tint = if (selected) activeColor else TextSub,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.label,
                            color = if (selected) activeColor else TextSub,
                            fontSize = 10.sp,
                            fontFamily = JetBrainsMono,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
