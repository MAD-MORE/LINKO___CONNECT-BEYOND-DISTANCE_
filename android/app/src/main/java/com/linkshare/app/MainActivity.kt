package com.linkshare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.ui.screens.*
import com.linkshare.app.ui.theme.*

class MainActivity : ComponentActivity() {
    private lateinit var linkoRuntime: LinkoRuntime
    private lateinit var linkoAuth: LinkoAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        linkoAuth = LinkoAuth(this)
        linkoRuntime = LinkoRuntime(this)
        linkoRuntime.start()
        setContent { LinkoTheme { LinkoApp(linkoAuth, linkoRuntime) } }
    }

    override fun onDestroy() {
        linkoRuntime.stop()
        super.onDestroy()
    }
}

@Composable
fun LinkoApp(auth: LinkoAuth, runtime: LinkoRuntime) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
        ?: if (auth.isSignedIn()) Screen.HomeEngine.route else Screen.Welcome.route
    val onboarding = route in onboardingScreens

    val onDeleteAccount: () -> Unit = {
        auth.signOut()
        nav.navigate(Screen.Welcome.route) {
            popUpTo(Screen.Welcome.route) { inclusive = true }
        }
    }

    Column(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        if (!onboarding && route != Screen.HomeEngine.route) {
            AppBar(appBarTitle(route) ?: "LINKO") { nav.popBackStack() }
        }
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = nav,
                startDestination = if (auth.isSignedIn()) Screen.HomeEngine.route else Screen.Welcome.route,
            ) {
                composable(Screen.Welcome.route) {
                    WelcomeScreen(
                        { nav.navigate(Screen.SignUp.route) },
                        { nav.navigate(Screen.SignIn.route) },
                    )
                }
                composable(Screen.SignUp.route) {
                    LinkoSignUpScreen(auth) {
                        nav.navigate(Screen.SignIn.route) {
                            popUpTo(Screen.SignUp.route) { inclusive = true }
                        }
                    }
                }
                composable(Screen.SignIn.route) {
                    SignInScreen(
                        auth,
                        onSignedIn = {
                            runtime.start()
                            nav.navigate(Screen.HomeEngine.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
                        },
                        onCreateAccount = { nav.navigate(Screen.SignUp.route) },
                        onForgotPassword = { nav.navigate(Screen.ForgotPassword.route) },
                    )
                }
                composable(Screen.ForgotPassword.route) {
                    ForgotPasswordScreen { nav.navigate(Screen.SignIn.route) }
                }
                composable(Screen.CreateAccount.route) {
                    CreateAccountScreen { nav.navigate(Screen.Verify.route) }
                }
                composable(Screen.Verify.route) { VerifyScreen { nav.navigate(Screen.SignIn.route) } }
                composable(Screen.Profile.route) { ProfileScreen { nav.navigate(Screen.RegisterDevice.route) } }
                composable(Screen.RegisterDevice.route) { RegisterDeviceScreen { nav.navigate(Screen.Permissions.route) } }
                composable(Screen.Permissions.route) {
                    PermissionsScreen {
                        nav.navigate(Screen.HomeEngine.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                }
                composable(Screen.HomeEngine.route) {
                    HomeEngineScreen(
                        onReceiver = { nav.navigate(Screen.RxSelectFriend.route) },
                        onProvider = { nav.navigate(Screen.ProviderIncoming.route) },
                    )
                }
                composable(Screen.Friends.route) {
                    FriendsScreen(
                        { nav.navigate(Screen.FindFriends.route) },
                        { nav.navigate(Screen.FriendProfile.route) },
                    )
                }
                composable(Screen.FindFriends.route) { FindFriendsScreen { nav.navigate(Screen.FriendProfile.route) } }
                composable(Screen.FriendProfile.route) { FriendProfileScreen { nav.navigate(Screen.RequestSent.route) } }
                composable(Screen.RequestSent.route) { RequestSentScreen { nav.popBackStack() } }
                composable(Screen.IncomingRequest.route) {
                    IncomingRequestScreen({ nav.navigate(Screen.Friends.route) }, { nav.popBackStack() })
                }
                composable(Screen.BlockedRemoved.route) { BlockedRemovedScreen { nav.popBackStack() } }
                composable(Screen.RxSelectFriend.route) { RxSelectFriendScreen { nav.navigate(Screen.RxRequest.route) } }
                composable(Screen.RxRequest.route) { RxRequestScreen { nav.popBackStack() } }
                composable(Screen.RxWaiting.route) { RxWaitingScreen { nav.popBackStack() } }
                composable(Screen.RxApproved.route) { RxApprovedScreen { nav.navigate(Screen.RxConnecting.route) } }
                composable(Screen.RxConnecting.route) { RxConnectingScreen { nav.navigate(Screen.RxDirectPath.route) } }
                composable(Screen.RxDirectPath.route) { RxDirectPathScreen { nav.navigate(Screen.Connected.route) } }
                composable(Screen.RxRelayFallback.route) { RxRelayFallbackScreen { nav.navigate(Screen.Connected.route) } }
                composable(Screen.Connected.route) {
                    ConnectedScreen(
                        onDisconnect = {
                            nav.navigate(Screen.HomeEngine.route) {
                                popUpTo(Screen.HomeEngine.route) { inclusive = true }
                            }
                        },
                        onQuality = { nav.navigate(Screen.NetworkQuality.route) },
                    )
                }
                composable(Screen.NetworkQuality.route) { NetworkQualityScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.Usage.route) { UsageScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.SessionDetails.route) { SessionDetailsScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.SessionHistory.route) { SessionHistoryScreen() }
                composable(Screen.ProviderIncoming.route) {
                    ProviderIncomingScreen(
                        onReview = { nav.navigate(Screen.ProviderAuthorization.route) },
                        onReject = { nav.popBackStack() },
                    )
                }
                composable(Screen.ProviderAuthorization.route) {
                    ProviderAuthorizationScreen { nav.navigate(Screen.ProviderSharingSetup.route) }
                }
                composable(Screen.ProviderSharingSetup.route) {
                    ProviderSharingSetupScreen { nav.navigate(Screen.ProviderSharingActive.route) }
                }
                composable(Screen.ProviderSharingActive.route) {
                    ProviderSharingActiveScreen(
                        onLiveUsage = { nav.navigate(Screen.ProviderLiveUsage.route) },
                        onStop = { nav.navigate(Screen.HomeEngine.route) },
                    )
                }
                composable(Screen.ProviderLiveUsage.route) { ProviderLiveUsageScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.ConnectionLost.route) {
                    ConnectionLostScreen(
                        onReconnect = { nav.navigate(Screen.Reconnecting.route) },
                        onHome = { nav.navigate(Screen.HomeEngine.route) },
                    )
                }
                composable(Screen.Reconnecting.route) { ReconnectingScreen { nav.navigate(Screen.Connected.route) } }
                composable(Screen.NetworkSwitching.route) { NetworkSwitchingScreen { nav.navigate(Screen.Connected.route) } }
                composable(Screen.SessionExpired.route) {
                    SessionExpiredScreen(
                        onNewSession = { nav.navigate(Screen.RxSelectFriend.route) },
                        onHome = { nav.navigate(Screen.HomeEngine.route) },
                    )
                }
                composable(Screen.KeyRevoked.route) { KeyRevokedScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onProfile = { nav.navigate(Screen.Profile.route) },
                        onDevices = { nav.navigate(Screen.DeviceIdentity.route) },
                        onFriends = { nav.navigate(Screen.Friends.route) },
                        onBlocked = { nav.navigate(Screen.BlockedRemoved.route) },
                        onHistory = { nav.navigate(Screen.SessionHistory.route) },
                        onSecurity = { nav.navigate(Screen.SecurityEngine.route) },
                        onPrivacy = { nav.navigate(Screen.Privacy.route) },
                        onDataRetention = { nav.navigate(Screen.DataRetention.route) },
                        onDeleteAccount = { nav.navigate(Screen.DeleteAccount.route) },
                    )
                }
                composable(Screen.DeviceIdentity.route) { DeviceIdentityScreen { nav.popBackStack() } }
                composable(Screen.SecurityEngine.route) { SecurityEngineScreen { nav.navigate(Screen.HomeEngine.route) } }
                composable(Screen.Privacy.route) { PrivacyScreen { nav.navigate(Screen.DataRetention.route) } }
                composable(Screen.DataRetention.route) { DataRetentionScreen { nav.popBackStack() } }
                composable(Screen.DeleteAccount.route) { DeleteAccountScreen(onDeleteAccount, { nav.popBackStack() }) }
            }
        }
        if (!onboarding) BottomNav(route, nav)
    }
}

@Composable
private fun AppBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Text("←", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono)
        }
        Text(title, color = TextPrimary, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold)
    }
}

private val titles = mapOf(
    "sign_in" to "Sign In", "sign_up" to "Create Account", "forgot_password" to "Forgot Password", "create_account" to "Create Account",
    "verify" to "Verify Account", "profile" to "Profile", "register_device" to "Register Device",
    "permissions" to "Permissions", "friends" to "Friends", "find_friends" to "Find Friends",
    "friend_profile" to "Friend Profile", "request_sent" to "Request Sent", "incoming_request" to "Incoming Request",
    "blocked_removed" to "Trust Boundaries", "rx_select_friend" to "Choose Friend", "rx_request" to "Connection Request",
    "rx_waiting" to "Waiting", "rx_approved" to "Approved", "rx_connecting" to "Connecting",
    "rx_direct_path" to "Direct Path", "rx_relay_fallback" to "Relay Fallback", "connected" to "Connected",
    "network_quality" to "Network Quality", "usage" to "Usage", "session_details" to "Session",
    "session_history" to "Session History", "provider_incoming" to "Incoming Request", "provider_authorization" to "Authorize",
    "provider_sharing_setup" to "Sharing Setup", "provider_sharing_active" to "Sharing Active", "provider_live_usage" to "Live Usage",
    "connection_lost" to "Connection Lost", "reconnecting" to "Reconnecting", "network_switching" to "Network Switch",
    "session_expired" to "Session Expired", "key_revoked" to "Key Revoked", "device_identity" to "Device Identity",
    "security_engine" to "Security Engine", "privacy" to "Privacy", "data_retention" to "Data Retention", "delete_account" to "Delete Account"
)

private fun appBarTitle(route: String) = titles[route]
private data class NavItem(val label: String, val tab: String, val route: String, val icon: String)
private val items = listOf(
    NavItem("HOME", "HOME", Screen.HomeEngine.route, "⌂"),
    NavItem("FRIENDS", "FRIENDS", Screen.Friends.route, "◎"),
    NavItem("HISTORY", "HISTORY", Screen.SessionHistory.route, "◷"),
    NavItem("SETTINGS", "SETTINGS", Screen.Settings.route, "⚙")
)

@Composable
private fun BottomNav(route: String, nav: NavController) {
    val active = activeNavTab(route)
    Row(Modifier.fillMaxWidth().background(Surface).border(1.dp, Border)) {
        items.forEach { item ->
            val selected = active == item.tab
            Column(Modifier.weight(1f).clickable { nav.navigate(item.route) { launchSingleTop = true } }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(40.dp, 28.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Blue.copy(alpha = .12f) else Color.Transparent), contentAlignment = Alignment.Center) {
                    Text(item.icon, color = if (selected) Blue else TextMuted, fontSize = 18.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(item.label, color = if (selected) Blue else TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
        }
    }
}
