package com.linkshare.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.PrototypeScreen
import com.linkshare.app.viewmodel.LinkShareViewModel

private val BG = Color(0xFF080808)
private val SURFACE = Color(0xFF111111)
private val CARD = Color(0xFF181818)
private val CARD2 = Color(0xFF1E1E1E)
private val BORDER = Color(0xFF242424)
private val BLUE = Color(0xFF3B7EF6)
private val GREEN = Color(0xFF22C55E)
private val YELLOW = Color(0xFFF59E0B)
private val RED = Color(0xFFEF4444)
private val TEXT = Color(0xFFF2F2F2)
private val SUB = Color(0xFFA0A0A0)
private val MUTED = Color(0xFF505050)

private val friends = listOf(
    Friend("kwame", "Kwame Mensah", "KM", "Accra", "Trusted peer", true, 0xFF22C55E, "Pixel", "Remote"),
    Friend("ama", "Ama Owusu", "AO", "Kumasi", "Trusted peer", true, 0xFF22C55E, "Galaxy", "Remote"),
    Friend("kofi", "Kofi Asante", "KA", "Cape Coast", "Trusted peer", false, 0xFFF59E0B, "Android", "Remote"),
    Friend("abena", "Abena Poku", "AP", "Takoradi", "Trusted peer", false, 0xFF505050, "Android", "Remote")
)

@Composable
fun FrozenPrototypeApp(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    val screen = state.screen
    val onboarding = setOf(PrototypeScreen.Welcome, PrototypeScreen.CreateAccount, PrototypeScreen.Verify, PrototypeScreen.Profile, PrototypeScreen.RegisterDevice, PrototypeScreen.Permissions)
    val showNav = screen !in onboarding
    val appTitle = when (screen) {
        PrototypeScreen.CreateAccount -> "Create Account"
        PrototypeScreen.Verify -> "Verify"
        PrototypeScreen.Profile -> "Profile"
        PrototypeScreen.RegisterDevice -> "Register Device"
        PrototypeScreen.Permissions -> "Permissions"
        PrototypeScreen.FindFriends -> "Find Friends"
        PrototypeScreen.FriendProfile -> "Kwame Mensah"
        PrototypeScreen.RequestSent -> "Request Sent"
        PrototypeScreen.IncomingRequest -> "Incoming Request"
        PrototypeScreen.BlockedRemoved -> "Trust Boundaries"
        PrototypeScreen.RxSelectFriend -> "Select Provider"
        PrototypeScreen.RxRequest -> "Requesting"
        PrototypeScreen.RxWaiting -> "Waiting"
        PrototypeScreen.RxApproved -> "Approved"
        PrototypeScreen.RxConnecting -> "Connecting"
        PrototypeScreen.RxDirectPath -> "Direct Path"
        PrototypeScreen.RxRelayFallback -> "Relay Fallback"
        PrototypeScreen.NetworkQuality -> "Network Quality"
        PrototypeScreen.Usage -> "Usage"
        PrototypeScreen.ProviderIncoming -> "Incoming Request"
        PrototypeScreen.ProviderAuthorization -> "Authorize"
        PrototypeScreen.ProviderSharingSetup -> "Sharing Setup"
        PrototypeScreen.ProviderSharingActive -> "Sharing Active"
        PrototypeScreen.ProviderLiveUsage -> "Live Usage"
        PrototypeScreen.SessionDetails -> "Session"
        PrototypeScreen.SessionHistory -> "Session History"
        PrototypeScreen.ConnectionLost -> "Connection Lost"
        PrototypeScreen.Reconnecting -> "Reconnecting"
        PrototypeScreen.NetworkSwitching -> "Network Switch"
        PrototypeScreen.SessionExpired -> "Session Expired"
        PrototypeScreen.DeviceIdentity -> "Device Identity"
        PrototypeScreen.SecurityEngine -> "Security Engine"
        PrototypeScreen.KeyRevoked -> "Key Revoked"
        PrototypeScreen.Privacy -> "Privacy"
        PrototypeScreen.DataRetention -> "Data Retention"
        PrototypeScreen.DeleteAccount -> "Delete Account"
        else -> null
    }
    val canBack = screen !in setOf(PrototypeScreen.Welcome, PrototypeScreen.HomeEngine, PrototypeScreen.Connected, PrototypeScreen.Settings, PrototypeScreen.Friends, PrototypeScreen.SessionHistory)

    Column(Modifier.fillMaxSize().background(BG)) {
        if (appTitle != null && !onboarding.contains(screen)) {
            TopBar(appTitle, canBack) { viewModel.goBack() }
        } else if (onboarding.contains(screen) && screen != PrototypeScreen.Welcome) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canBack) Text("← BACK", color = BLUE, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { viewModel.goBack() })
                Spacer(Modifier.width(12.dp))
                Text("LINKO", color = BLUE, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { PrototypeScreenContent(state, viewModel, onRequestVpnPermission) }
        if (showNav) BottomNav(screen, viewModel)
    }
}

@Composable private fun TopBar(title: String, back: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back) Text("‹", color = TEXT, fontSize = 36.sp, modifier = Modifier.padding(start = 8.dp).clickable(onClick = onBack))
        Text(title, color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = if (back) 4.dp else 16.dp))
    }
}

@Composable private fun BottomNav(screen: PrototypeScreen, vm: LinkShareViewModel) {
    val active = when (screen) {
        PrototypeScreen.Friends, PrototypeScreen.FindFriends, PrototypeScreen.FriendProfile, PrototypeScreen.RequestSent, PrototypeScreen.IncomingRequest, PrototypeScreen.BlockedRemoved -> "FRIENDS"
        PrototypeScreen.SessionHistory -> "HISTORY"
        PrototypeScreen.Settings, PrototypeScreen.Privacy, PrototypeScreen.DataRetention, PrototypeScreen.DeviceIdentity, PrototypeScreen.SecurityEngine, PrototypeScreen.KeyRevoked, PrototypeScreen.DeleteAccount -> "SETTINGS"
        else -> "HOME"
    }
    Row(Modifier.fillMaxWidth().background(SURFACE).border(1.dp, BORDER), horizontalArrangement = Arrangement.SpaceEvenly) {
        NavItem("⌂", "HOME", active == "HOME") { vm.navigateTo(PrototypeScreen.HomeEngine) }
        NavItem("◎", "FRIENDS", active == "FRIENDS") { vm.openFriends() }
        NavItem("≡", "HISTORY", active == "HISTORY") { vm.openHistory() }
        NavItem("⚙", "SETTINGS", active == "SETTINGS") { vm.openSettings() }
    }
}

@Composable private fun NavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = if (active) BLUE else MUTED, fontSize = 20.sp)
        Text(label, color = if (active) BLUE else MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable private fun PrototypeScreenContent(state: ConnectionUiState, vm: LinkShareViewModel, vpn: () -> Unit) {
    when (state.screen) {
        PrototypeScreen.Welcome -> Welcome(vm)
        PrototypeScreen.CreateAccount -> FormScreen("Create Account", "Create your Linko identity", listOf("IDENTITY" to "Phone or email", "DISPLAY NAME" to "Your display name"), "CONTINUE") { vm.navigateTo(PrototypeScreen.Verify) }
        PrototypeScreen.Verify -> Verify(vm)
        PrototypeScreen.Profile -> Profile(vm)
        PrototypeScreen.RegisterDevice -> RegisterDevice(vm)
        PrototypeScreen.Permissions -> Permissions(vm)
        PrototypeScreen.Friends -> Friends(vm)
        PrototypeScreen.FindFriends -> FormScreen("Find Friends", "Search for someone to connect with", listOf("SEARCH" to "Name or Linko ID"), "SEARCH") { vm.navigateTo(PrototypeScreen.FriendProfile) }
        PrototypeScreen.FriendProfile -> FriendProfile(vm)
        PrototypeScreen.RequestSent -> RequestSent(vm)
        PrototypeScreen.IncomingRequest -> IncomingTrustRequest(vm)
        PrototypeScreen.BlockedRemoved -> SimpleInfo(vm, "Trust Boundaries", "Blocked and removed connection boundaries", listOf("BLOCKED" to "0 peers"), "RETURN HOME") { vm.navigateTo(PrototypeScreen.HomeEngine) }
        PrototypeScreen.HomeEngine -> HomeEngine(vm)
        PrototypeScreen.RxSelectFriend -> SelectProvider(state, vm, vpn)
        PrototypeScreen.RxRequest -> ProgressState("REQUESTING", BLUE, "Sending Request", "Asking Kwame Mensah to share connection", "CANCEL REQUEST", RED) { vm.navigateTo(PrototypeScreen.RxSelectFriend) }
        PrototypeScreen.RxWaiting -> ProgressState("WAITING", YELLOW, "Awaiting Approval", "Provider controls authorization", "CANCEL", RED) { vm.navigateTo(PrototypeScreen.RxSelectFriend) }
        PrototypeScreen.RxApproved -> ProgressState("APPROVED", GREEN, "Session Approved", "Provider authorized this connection", "CONNECT NOW", GREEN) { vm.navigateTo(PrototypeScreen.RxConnecting) }
        PrototypeScreen.RxConnecting -> Connecting()
        PrototypeScreen.RxDirectPath -> RouteScreen("Direct Path", "Fastest secure route selected", "DIRECT", "NAT traversal successful", GREEN, "ENCRYPTED", "Session authorization valid", BLUE) { vm.navigateTo(PrototypeScreen.Connected) }
        PrototypeScreen.RxRelayFallback -> RouteScreen("Relay Fallback", "Direct path unavailable", "RELAY", "Authorized fallback route", YELLOW, "LIMITED", "Usage limits remain active", YELLOW) { vm.navigateTo(PrototypeScreen.Connected) }
        PrototypeScreen.Connected -> Connected(vm)
        PrototypeScreen.NetworkQuality -> NetworkQuality(vm)
        PrototypeScreen.Usage -> Usage(vm)
        PrototypeScreen.ProviderIncoming -> ProviderIncoming(vm)
        PrototypeScreen.ProviderAuthorization -> ProviderAuthorization(vm)
        PrototypeScreen.ProviderSharingSetup -> ProviderSetup(vm)
        PrototypeScreen.ProviderSharingActive -> ProviderActive(vm)
        PrototypeScreen.ProviderLiveUsage -> ProviderLiveUsage(vm)
        PrototypeScreen.SessionDetails -> SimpleInfo(vm, "Session Details", "Current secure link info", listOf("STATUS" to "Connected", "ROUTE" to "Direct", "AUTHORIZATION" to "Valid"), "DISCONNECT", RED) { vm.disconnect() }
        PrototypeScreen.SessionHistory -> History(vm)
        PrototypeScreen.ConnectionLost -> ProgressState("OFFLINE", RED, "Connection Lost", "The secure path was interrupted", "RECONNECT", RED) { vm.navigateTo(PrototypeScreen.Reconnecting) }
        PrototypeScreen.Reconnecting -> ProgressState("RECONNECTING", BLUE, "Restoring Tunnel", "Trying direct • Falling back to relay", "", BLUE) { vm.navigateTo(PrototypeScreen.Connected) }
        PrototypeScreen.NetworkSwitching -> SimpleInfo(vm, "Network Switch", "Network changed during session", listOf("NETWORK" to "Mobile → Wi-Fi", "SESSION" to "Protected"), "CONTINUE") { vm.navigateTo(PrototypeScreen.Connected) }
        PrototypeScreen.SessionExpired -> ProgressState("EXPIRED", RED, "Session Expired", "Authorization has ended", "REQUEST NEW SESSION", RED) { vm.navigateTo(PrototypeScreen.RxSelectFriend) }
        PrototypeScreen.DeviceIdentity -> SimpleInfo(vm, "Device Identity", "Trusted devices and keys", listOf("THIS DEVICE" to "Android • Trusted", "KEYS" to "Protected"), "MANAGE DEVICE") { vm.navigateTo(PrototypeScreen.Settings) }
        PrototypeScreen.SecurityEngine -> Security(vm)
        PrototypeScreen.KeyRevoked -> ProgressState("REVOKED", RED, "Key Revoked", "Session credentials were revoked", "RETURN HOME", RED) { vm.navigateTo(PrototypeScreen.HomeEngine) }
        PrototypeScreen.Privacy -> SimpleInfo(vm, "Privacy", "Connection privacy controls", listOf("BROWSING CONTENTS" to "Not retained", "SESSION METADATA" to "Limited retention"), "MANAGE DATA") { vm.navigateTo(PrototypeScreen.DataRetention) }
        PrototypeScreen.DataRetention -> SimpleInfo(vm, "Data Retention", "Control stored session information", listOf("HISTORY" to "Your sessions", "ACCOUNT DATA" to "Your identity"), "DONE") { vm.navigateTo(PrototypeScreen.Settings) }
        PrototypeScreen.Settings -> Settings(vm)
        PrototypeScreen.DeleteAccount -> DeleteAccount(vm)
    }
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
@Composable private fun Header(title: String, detail: String) { Column(Modifier.padding(top = 4.dp, bottom = 10.dp)) { Text(title, color = TEXT, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(detail, color = SUB, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) } }

@Composable private fun Welcome(vm: LinkShareViewModel) { ScreenColumn { Spacer(Modifier.weight(1f)); Text("LINKO", color = BLUE, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("SECURE CONNECTION ENGINE", color = MUTED, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(26.dp)); Ring(BLUE, 200.dp, "READY", idle = true) { vm.navigateTo(PrototypeScreen.CreateAccount) }; Spacer(Modifier.height(26.dp)); Text("Welcome", color = TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Secure connection beyond distance", color = SUB, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("LINKO ENGINE READY", color = MUTED, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)); Spacer(Modifier.weight(1f)); Primary("CREATE ACCOUNT") { vm.navigateTo(PrototypeScreen.CreateAccount) }; TextButton(onClick = { vm.navigateTo(PrototypeScreen.HomeEngine) }, modifier = Modifier.fillMaxWidth()) { Text("Already registered? SIGN IN", color = SUB, fontSize = 12.sp) } } }

@Composable private fun FormScreen(title: String, detail: String, fields: List<Pair<String,String>>, button: String, onNext: () -> Unit) { var values by remember { mutableStateOf(fields.associate { it.first to "" }) }; ScreenColumn { Header(title, detail); fields.forEach { (label, hint) -> OutlinedTextField(value = values[label].orEmpty(), onValueChange = { values = values + (label to it) }, label = { Text(label) }, placeholder = { Text(hint) }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors()) }; Spacer(Modifier.weight(1f)); Primary(button, onClick = onNext) } }

@Composable private fun Verify(vm: LinkShareViewModel) { ScreenColumn { Header("Verify Account", "Confirm your identity securely"); CardBox { Label("VERIFICATION CODE"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) { repeat(6) { Box(Modifier.size(40.dp, 50.dp).background(CARD2, RoundedCornerShape(10.dp)).border(1.dp, BORDER, RoundedCornerShape(10.dp)), Alignment.Center) { Text(if (it == 0) "·" else "", color = TEXT, fontSize = 22.sp) } } }; Text("Short-lived verification code", color = SUB, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) }; TextButton(onClick = {}) { Text("Resend code", color = SUB) }; Spacer(Modifier.weight(1f)); Primary("VERIFY") { vm.navigateTo(PrototypeScreen.Profile) } } }

@Composable private fun Profile(vm: LinkShareViewModel) { ScreenColumn { Header("Set Up Profile", "Your Linko identity"); Avatar("P", BLUE, 80.dp, Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(12.dp)); OutlinedTextField(value = "Padmore", onValueChange = {}, label = { Text("DISPLAY NAME") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors()); OutlinedTextField(value = "@padmore", onValueChange = {}, label = { Text("LINKO ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors()); Spacer(Modifier.weight(1f)); Primary("SAVE PROFILE") { vm.navigateTo(PrototypeScreen.RegisterDevice) } } }

@Composable private fun RegisterDevice(vm: LinkShareViewModel) { ScreenColumn { Header("Register Device", "Bind this device to your identity"); CardBox { Setting("▣", "Android Device", "Trusted device identity", BLUE) }; CardBox { Setting("▣", "Protected Keys", "Stored securely on device", GREEN) }; Spacer(Modifier.weight(1f)); Primary("REGISTER DEVICE") { vm.navigateTo(PrototypeScreen.Permissions) } } }
@Composable private fun Permissions(vm: LinkShareViewModel) { ScreenColumn { Header("Permissions", "Allow Linko to operate the connection engine"); CardBox { Setting("◉", "Network", "Connection and tunnel access", BLUE) }; CardBox { Setting("◌", "Notifications", "Session and request updates", YELLOW) }; Spacer(Modifier.weight(1f)); Primary("ALLOW & CONTINUE") { vm.navigateTo(PrototypeScreen.HomeEngine) } } }
@Composable private fun Friends(vm: LinkShareViewModel) { ScreenColumn { Header("Friends", "Your trusted connection network"); Label("4 TRUSTED • VERIFIED PEERS"); CardBox { friends.forEachIndexed { i, f -> FriendItem(f) { vm.navigateTo(PrototypeScreen.FriendProfile) }; if (i < friends.lastIndex) HorizontalDivider(color = BORDER) } }; Spacer(Modifier.weight(1f)); Primary("+ FIND FRIENDS") { vm.navigateTo(PrototypeScreen.FindFriends) } } }
@Composable private fun FriendProfile(vm: LinkShareViewModel) { ScreenColumn { Spacer(Modifier.height(10.dp)); Avatar("KM", GREEN, 80.dp, Modifier.align(Alignment.CenterHorizontally)); Text("Kwame Mensah", color = TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("@kwame", color = SUB, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); CardBox { Info("TRUST", "Pending", "Connection access is not active", YELLOW) }; Spacer(Modifier.weight(1f)); Primary("SEND REQUEST") { vm.navigateTo(PrototypeScreen.RequestSent) } } }
@Composable private fun RequestSent(vm: LinkShareViewModel) { ScreenColumn { Spacer(Modifier.weight(1f)); Ring(YELLOW, 180.dp, "PENDING", pulse = true) { vm.navigateTo(PrototypeScreen.FindFriends) }; Text("Request Sent", color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Waiting for trust approval from", color = SUB, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Kwame Mensah", color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.weight(1f)); Primary("CANCEL REQUEST", RED, true) { vm.navigateTo(PrototypeScreen.FindFriends) } } }
@Composable private fun IncomingTrustRequest(vm: LinkShareViewModel) { ScreenColumn { Header("Incoming Request", "A trusted connection request arrived"); Avatar("KM", BLUE, 72.dp, Modifier.align(Alignment.CenterHorizontally)); Text("Kwame Mensah", color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("@kwame", color = SUB, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); CardBox { Info("PERMISSION REQUESTED", "Connection access", "You control every session", null) }; Spacer(Modifier.weight(1f)); Primary("APPROVE") { vm.navigateTo(PrototypeScreen.ProviderAuthorization) }; Primary("DENY", RED, true) { vm.navigateTo(PrototypeScreen.HomeEngine) } } }

@Composable private fun HomeEngine(vm: LinkShareViewModel) { ScreenColumn { Header("Home Engine", "Choose how LINKO operates"); Spacer(Modifier.weight(1f)); Ring(BLUE, 200.dp, "READY", idle = true) { vm.navigateTo(PrototypeScreen.RxSelectFriend) }; Text("ENCRYPTED • AUTHORIZED • PRIVATE", color = MUTED, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.weight(1f)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Primary("RECEIVER", modifier = Modifier.weight(1f)) { vm.setMode(AppMode.Client); vm.navigateTo(PrototypeScreen.RxSelectFriend) }; Primary("PROVIDER", MUTED, modifier = Modifier.weight(1f)) { vm.setMode(AppMode.Host); vm.navigateTo(PrototypeScreen.ProviderIncoming) } } } }
@Composable private fun SelectProvider(state: ConnectionUiState, vm: LinkShareViewModel, vpn: () -> Unit) { ScreenColumn { Header("Select Provider", "Choose a trusted peer to connect through"); CardBox { friends.filter { it.isSharing }.forEach { FriendItem(it) { if (!state.hasVpnPermission) vpn() else vm.connectToFriend(it) } } }; CardBox { Info("USAGE LIMIT", "Provider controlled", "You will see limits before connecting", null) }; Spacer(Modifier.weight(1f)); Primary("REQUEST CONNECTION") { friends.firstOrNull { it.isSharing }?.let { if (!state.hasVpnPermission) vpn() else vm.connectToFriend(it) } } } }
@Composable private fun ProgressState(label: String, color: Color, title: String, detail: String, button: String, buttonColor: Color, onButton: () -> Unit) { ScreenColumn { Spacer(Modifier.weight(1f)); Ring(color, 180.dp, label, pulse = label !in setOf("OFFLINE", "EXPIRED", "REVOKED")); Text(title, color = if (label == "OFFLINE" || label == "EXPIRED" || label == "REVOKED") color else TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text(detail, color = SUB, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.weight(1f)); if (button.isNotBlank()) Primary(button, buttonColor, label == "OFFLINE" || label == "EXPIRED" || label == "REVOKED", onClick = onButton) } }
@Composable private fun Connecting() { ScreenColumn { Spacer(Modifier.weight(1f)); Ring(BLUE, 180.dp, "CONNECTING", pulse = true); Text("Establishing Tunnel", color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Negotiating secure path", color = SUB, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Chip("ENCRYPTED", BLUE); Spacer(Modifier.weight(1f)) } }
@Composable private fun RouteScreen(title: String, detail: String, route: String, routeSub: String, routeColor: Color, tunnel: String, tunnelSub: String, tunnelColor: Color, onNext: () -> Unit) { ScreenColumn { Header(title, detail); CardBox { Info("ROUTE", route, routeSub, routeColor) }; CardBox { Info("TUNNEL", tunnel, tunnelSub, tunnelColor) }; Spacer(Modifier.weight(1f)); Primary(if (route == "DIRECT") "CONTINUE" else "CONTINUE ANYWAY", onClick = onNext) } }
@Composable private fun Connected(vm: LinkShareViewModel) { ScreenColumn { Spacer(Modifier.weight(1f)); Ring(GREEN, 200.dp, "CONNECTED", idle = true) { vm.navigateTo(PrototypeScreen.NetworkQuality) }; CardBox { Info("NETWORK", "12.8 Mbps", "24 ms • DIRECT", GREEN, true) }; Spacer(Modifier.weight(1f)); Primary("DISCONNECT", RED, true) { vm.disconnect() } } }
@Composable private fun NetworkQuality(vm: LinkShareViewModel) { ScreenColumn { Header("Network Quality", "Live tunnel telemetry"); CardBox { Info("SPEED", "12.8 Mbps", "Stable throughput", GREEN, true) }; CardBox { Info("LATENCY", "24 ms", "Good quality", GREEN, true) }; CardBox { Info("ROUTE", "DIRECT", "Encrypted path", BLUE) }; Spacer(Modifier.weight(1f)); Primary("DISCONNECT", RED, true) { vm.disconnect() } } }
@Composable private fun Usage(vm: LinkShareViewModel) { ScreenColumn { Header("Session Usage", "Live usage tracking"); CardBox { Info("DATA USED", "340 MB", "Provider limit: 2.0 GB", null, true); Progress(17, GREEN) }; CardBox { Info("DURATION", "24 min", "Provider limit: 60 min", null, true); Progress(40, YELLOW) }; CardBox { Info("REMAINING", "1.66 GB • 36 min", "Session active", GREEN) }; Spacer(Modifier.weight(1f)); Primary("DISCONNECT", RED, true) { vm.disconnect() } } }
@Composable private fun ProviderIncoming(vm: LinkShareViewModel) { ScreenColumn { Spacer(Modifier.weight(1f)); Text("Incoming Request", color = TEXT, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Review before sharing", color = SUB, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Avatar("P", BLUE, 72.dp, Modifier.align(Alignment.CenterHorizontally)); Text("Padmore", color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("@padmore", color = SUB, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) { Chip("VERIFIED", BLUE); Chip("TRUSTED", GREEN) }; CardBox { Info("REQUEST", "Connection sharing", "No access until you approve", null) }; Spacer(Modifier.weight(1f)); Primary("REVIEW & AUTHORIZE") { vm.navigateTo(PrototypeScreen.ProviderAuthorization) }; Primary("REJECT", RED, true) { vm.navigateTo(PrototypeScreen.HomeEngine) } } }
@Composable private fun ProviderAuthorization(vm: LinkShareViewModel) { ScreenColumn { Header("Authorize Session", "Verify and authorize this connection"); CardBox { Info("IDENTITY", "Padmore", "Device verified", null) }; CardBox { Info("CREDENTIALS", "Short-lived", "Issued only after approval", BLUE) }; Spacer(Modifier.weight(1f)); Primary("AUTHORIZE & SET LIMITS") { vm.navigateTo(PrototypeScreen.ProviderSharingSetup) } } }
@Composable private fun ProviderSetup(vm: LinkShareViewModel) { ScreenColumn { Header("Sharing Setup", "Configure what you will share"); CardBox { Info("DATA LIMIT", "2.0 GB", "Hard session limit", null) }; CardBox { Info("TIME LIMIT", "60 min", "Hard session limit", null) }; Spacer(Modifier.weight(1f)); Primary("START SHARING", GREEN) { vm.toggleHostSharing() } } }
@Composable private fun ProviderActive(vm: LinkShareViewModel) { ScreenColumn { Spacer(Modifier.weight(1f)); Ring(GREEN, 190.dp, "SHARING", idle = true) { vm.navigateTo(PrototypeScreen.ProviderLiveUsage) }; Text("Sharing Active", color = GREEN, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Your connection is being shared with", color = SUB, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Padmore", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("CONNECTED • TRUSTED", color = MUTED, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.weight(1f)); Primary("LIVE USAGE", GREEN, true) { vm.navigateTo(PrototypeScreen.ProviderLiveUsage) }; Primary("STOP SHARING", RED) { vm.disconnect() } } }
@Composable private fun ProviderLiveUsage(vm: LinkShareViewModel) { ScreenColumn { Header("Live Usage", "Padmore • Current session"); CardBox { Info("DATA USED", "340 MB", "of 2.0 GB limit", null, true); Progress(17, BLUE) }; CardBox { Info("DURATION", "24 min", "of 60 min limit", null, true); Progress(40, YELLOW) }; Spacer(Modifier.weight(1f)); Primary("KILL SESSION", RED) { vm.disconnect() } } }
@Composable private fun History(vm: LinkShareViewModel) { ScreenColumn { Header("Session History", "Previous LINKO sessions"); listOf("Kwame Mensah" to "DIRECT • 24 min • 340 MB", "Ama Owusu" to "RELAY • 18 min • 220 MB", "Kwame Mensah" to "DIRECT • 41 min • 890 MB").forEach { (n,d) -> CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(n.split(" ").map { it.first() }.joinToString(""), BLUE, 44.dp); Column(Modifier.padding(start = 12.dp)) { Text(n, color = TEXT, fontSize = 14.sp); Text(d, color = SUB, fontSize = 11.sp) } } } } } }
@Composable private fun Security(vm: LinkShareViewModel) { ScreenColumn { Header("Security Engine", "How LINKO protects sessions"); Ring(GREEN, 160.dp, "SECURE") { vm.navigateTo(PrototypeScreen.HomeEngine) }; CardBox { Info("TUNNEL", "Encrypted", "Short-lived credentials", GREEN) }; CardBox { Info("KEYS", "Device-bound", "Never leave this device", BLUE) } } }
@Composable private fun Settings(vm: LinkShareViewModel) { LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) { item { Row(Modifier.fillMaxWidth().background(SURFACE).padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Avatar("P", BLUE, 56.dp); Column(Modifier.padding(start = 16.dp)) { Text("Padmore", color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("@padmore", color = SUB, fontSize = 13.sp); Chip("VERIFIED", GREEN) } } }; item { SettingSection("ACCOUNT") { Setting("◉", "Profile", "Padmore • @padmore", BLUE) { vm.navigateTo(PrototypeScreen.Profile) }; Setting("▣", "Devices", "1 trusted device", BLUE) { vm.navigateTo(PrototypeScreen.DeviceIdentity) } } }; item { SettingSection("CONNECTIONS") { Setting("◎", "Friends", "4 trusted peers", GREEN) { vm.openFriends() }; Setting("⊘", "Blocked & Removed", "0 blocked", RED) { vm.navigateTo(PrototypeScreen.BlockedRemoved) }; Setting("◷", "Session History", "3 recent sessions", YELLOW) { vm.openHistory() } } }; item { SettingSection("SECURITY & PRIVACY") { Setting("▣", "Security Engine", null, GREEN) { vm.navigateTo(PrototypeScreen.SecurityEngine) }; Setting("◇", "Privacy", null, SUB) { vm.navigateTo(PrototypeScreen.Privacy) }; Setting("⌫", "Data Retention", null, SUB) { vm.navigateTo(PrototypeScreen.DataRetention) } } }; item { Primary("DELETE ACCOUNT", RED, true, Modifier.padding(horizontal = 16.dp)) { vm.navigateTo(PrototypeScreen.DeleteAccount) } } } }
@Composable private fun DeleteAccount(vm: LinkShareViewModel) { ScreenColumn { Header("Delete Account", "This action is permanent and irreversible"); CardBox { Label("⚠ WARNING", RED); listOf("Your Linko identity will be removed", "All session history will be deleted", "Trusted connections will be lost", "This cannot be undone").forEach { Text("• $it", color = TEXT, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) } }; Spacer(Modifier.weight(1f)); Primary("DELETE ACCOUNT", RED) { vm.navigateTo(PrototypeScreen.Welcome) }; Primary("CANCEL", SUB, true) { vm.navigateTo(PrototypeScreen.Settings) } } }
@Composable private fun SimpleInfo(vm: LinkShareViewModel, title: String, detail: String, rows: List<Pair<String,String>>, button: String, color: Color = BLUE, onClick: () -> Unit) { ScreenColumn { Header(title, detail); rows.forEach { CardBox { Info(it.first, it.second, null, color) } }; Spacer(Modifier.weight(1f)); Primary(button, color = color, onClick = onClick) } }
@Composable private fun CardBox(content: @Composable () -> Unit) { Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(16.dp)).border(1.dp, BORDER, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
@Composable private fun SettingSection(title: String, content: @Composable () -> Unit) { Column(Modifier.padding(horizontal = 16.dp)) { Label(title); CardBox(content) } }
@Composable private fun Setting(icon: String, title: String, detail: String? = null, color: Color = BLUE, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).background(CARD2, RoundedCornerShape(12.dp)), Alignment.Center) { Text(icon, color = color) }; Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(title, color = TEXT, fontSize = 14.sp); detail?.let { Text(it, color = SUB, fontSize = 11.sp) } }; if (onClick != null) Text("›", color = MUTED, fontSize = 22.sp) } }
@Composable private fun FriendItem(friend: Friend, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(friend.initials, if (friend.isSharing) GREEN else YELLOW, 42.dp); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(friend.name, color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("${friend.id} • ${if (friend.isSharing) "ONLINE" else "AWAY"}", color = SUB, fontSize = 11.sp) }; Chip(if (friend.isSharing) "ONLINE" else "AWAY", if (friend.isSharing) GREEN else YELLOW) } }
@Composable private fun Avatar(text: String, color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) { Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = .13f)).border(2.dp, color.copy(alpha = .35f), CircleShape), Alignment.Center) { Text(text, color = color, fontSize = (size.value / 3).sp, fontWeight = FontWeight.Bold) } }
@Composable private fun Label(text: String, color: Color = MUTED) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp) }
@Composable private fun Info(label: String, value: String, sub: String? = null, accent: Color? = null, mono: Boolean = false) { Column { Label(label); Text(value, color = accent ?: TEXT, fontSize = if (mono) 22.sp else 14.sp, fontWeight = if (mono) FontWeight.Bold else FontWeight.Medium); sub?.let { Text(it, color = SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) } } }
@Composable private fun Chip(text: String, color: Color) { Row(Modifier.background(color.copy(alpha = .09f), RoundedCornerShape(20.dp)).border(1.dp, color.copy(alpha = .2f), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(5.dp).background(color, CircleShape)); Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, modifier = Modifier.padding(start = 5.dp)) } }
@Composable private fun Progress(value: Int, color: Color) { Box(Modifier.fillMaxWidth().height(5.dp).background(BORDER, RoundedCornerShape(4.dp))) { Box(Modifier.fillMaxWidth(value / 100f).fillMaxHeight().background(color, RoundedCornerShape(4.dp))) } }
@Composable private fun Primary(text: String, color: Color = BLUE, outline: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) { if (outline) OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, color), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) } else Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) } }
@Composable private fun fieldColors() = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = TEXT, unfocusedTextColor = TEXT, focusedLabelColor = BLUE, unfocusedLabelColor = MUTED, focusedIndicatorColor = BLUE, unfocusedIndicatorColor = BORDER)

@Composable private fun Ring(color: Color, size: androidx.compose.ui.unit.Dp, label: String, pulse: Boolean = false, idle: Boolean = false, onClick: (() -> Unit)? = null) {
    val transition = rememberInfiniteTransition(label = "ring-$label")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (idle) 3200 else if (pulse) 1100 else 1800, easing = LinearEasing), RepeatMode.Restart), label = "rotation")
    Box(Modifier.size(size).clip(CircleShape).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f - 10.dp.toPx()
            val c = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(BORDER, r, style = Stroke(1.5.dp.toPx()))
            rotate(rotation) {
                drawArc(color.copy(alpha = .13f), 0f, 220f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color, 0f, 220f, false, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(c.x, c.y - r))
            }
            for (i in 1..3) {
                val rr = r * (0.30f + i * .14f)
                rotate(rotation * if (i % 2 == 0) -1f else 1f) { drawArc(color.copy(alpha = .45f / i), 0f, 150f, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - rr, c.y - rr), size = androidx.compose.ui.geometry.Size(rr * 2, rr * 2), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)) }
            }
        }
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
    }
}
