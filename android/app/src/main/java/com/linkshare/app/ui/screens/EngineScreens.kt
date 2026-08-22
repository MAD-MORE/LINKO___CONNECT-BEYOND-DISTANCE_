package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*

private fun title(text: String, sub: String): @Composable () -> Unit = { Column(Modifier.fillMaxWidth()) { Text(text, color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(sub, color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono) } }

@Composable
fun HomeEngineScreen(onReceiver: () -> Unit, onProvider: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp)); Text("LINKO ENGINE", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(4.dp)); Text("Choose how this device participates", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(28.dp)); Ring(Blue, 190.dp, idle = true, label = "READY")
        Spacer(Modifier.height(30.dp)); LinkoCard { InfoRow("RECEIVER", "Use a friend's connection", "Request permission and route traffic through a trusted peer", Blue); Spacer(Modifier.height(14.dp)); PrimaryButton("CONNECT TO A FRIEND", onReceiver) }
        Spacer(Modifier.height(12.dp)); LinkoCard { InfoRow("PROVIDER", "Share your connection", "Approve trusted requests before sharing", Green); Spacer(Modifier.height(14.dp)); PrimaryButton("SHARE MY CONNECTION", onProvider, color = Green) }
    }
}

@Composable
fun RxSelectFriendScreen(onRequest: () -> Unit) {
    var selectedFriendId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectableFriends = sampleFriends.filter { it.status != "OFFLINE" }
    val selectedFriend = selectableFriends.firstOrNull { it.id == selectedFriendId }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        title("Choose a Friend", "Select a trusted peer to request a connection")()
        Spacer(Modifier.height(20.dp))
        LinkoCard {
            selectableFriends.forEachIndexed { i, friend ->
                val selected = friend.id == selectedFriendId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Blue.copy(alpha = 0.10f) else Color.Transparent)
                        .border(if (selected) 1.5.dp else 0.dp, if (selected) Blue else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { selectedFriendId = friend.id },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
                        FriendRow(friend)
                    }
                }
                if (i < selectableFriends.lastIndex) RowDivider()
            }
        }
        Spacer(Modifier.height(12.dp))
        if (selectedFriend != null) {
            Text("SELECTED", color = Blue, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("${selectedFriend.name} • ${selectedFriend.id}", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
        } else {
            Text("Select a friend to continue", color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("REQUEST CONNECTION", { if (selectedFriend != null) onRequest() }, color = if (selectedFriend != null) Blue else TextMuted, outline = selectedFriend == null)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun RxRequestScreen(onCancel: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.weight(1f)); Ring(Yellow, 180.dp, pulse = true, label = "REQUEST"); Spacer(Modifier.height(24.dp)); Text("Connection Requested", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Waiting for your friend to approve", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.weight(1f)); PrimaryButton("CANCEL", onCancel, color = Red, outline = true); Spacer(Modifier.height(24.dp)) } }
@Composable fun RxWaitingScreen(onCancel: () -> Unit) = RxRequestScreen(onCancel)
@Composable fun RxApprovedScreen(onConnect: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(20.dp)); title("Request Approved", "Your friend accepted the connection")(); Spacer(Modifier.height(28.dp)); Ring(Green, 180.dp, label = "APPROVED"); Spacer(Modifier.weight(1f)); PrimaryButton("START SECURE CONNECTION", onConnect, color = Green); Spacer(Modifier.height(24.dp)) } }
@Composable fun RxConnectingScreen(onSkip: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.weight(1f)); Ring(Blue, 190.dp, pulse = true, label = "LINKING"); Spacer(Modifier.height(24.dp)); Text("Securing connection", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text("Negotiating the safest available path", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.weight(1f)); PrimaryButton("CONTINUE", onSkip); Spacer(Modifier.height(24.dp)) } }
@Composable fun RxDirectPathScreen(onContinue: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Direct Path", "A direct peer path is available")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("PATH", "DIRECT", "Lowest relay overhead", Green, true); Spacer(Modifier.height(16.dp)); InfoRow("STATUS", "SECURE", "Session keys exchanged", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("ENTER CONNECTED SESSION", onContinue, color = Green); Spacer(Modifier.height(24.dp)) } }
@Composable fun RxRelayFallbackScreen(onContinue: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Relay Fallback", "Direct path unavailable; relay is ready")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("PATH", "RELAY", "Traffic will use the secure relay", Yellow, true); Spacer(Modifier.height(16.dp)); InfoRow("STATUS", "PROTECTED", "Tunnel remains encrypted", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("CONTINUE", onContinue, color = Yellow); Spacer(Modifier.height(24.dp)) } }

@Composable fun ConnectedScreen(onDisconnect: () -> Unit, onQuality: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(8.dp)); title("Connected", "Internet is moving through your trusted friend")(); Spacer(Modifier.height(24.dp)); Ring(Green, 190.dp, label = "ONLINE"); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("SESSION", "ACTIVE", "Encrypted tunnel established", Green, true); Spacer(Modifier.height(12.dp)); InfoRow("PATH", "DIRECT", "Connection quality is stable", Blue) }; Spacer(Modifier.weight(1f)); PrimaryButton("NETWORK QUALITY", onQuality, outline = true); Spacer(Modifier.height(8.dp)); PrimaryButton("DISCONNECT", onDisconnect, color = Red); Spacer(Modifier.height(24.dp)) } }
@Composable fun NetworkQualityScreen(onDisconnect: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Network Quality", "Live connection health")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("QUALITY", "EXCELLENT", "Low latency and stable path", Green, true); Spacer(Modifier.height(16.dp)); InfoRow("LATENCY", "42 ms", "Measured tunnel round trip", Blue); Spacer(Modifier.height(16.dp)); InfoRow("PATH", "DIRECT", "No relay fallback", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("DISCONNECT", onDisconnect, color = Red); Spacer(Modifier.height(24.dp)) } }
@Composable fun UsageScreen(onDisconnect: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Usage", "Traffic counters for this session")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("DATA USED", "1.84 GB", "Current session", Blue, true); Spacer(Modifier.height(16.dp)); InfoRow("DURATION", "28 min", "Since connection started", TextPrimary); Spacer(Modifier.height(16.dp)); LinkoProgressBar(0.62f, 1f, Blue) }; Spacer(Modifier.weight(1f)); PrimaryButton("DISCONNECT", onDisconnect, color = Red); Spacer(Modifier.height(24.dp)) } }
@Composable fun SessionDetailsScreen(onDisconnect: () -> Unit) = UsageScreen(onDisconnect)
@Composable fun SessionHistoryScreen() { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Session History", "Recent trusted connections")(); Spacer(Modifier.height(20.dp)); LinkoCard { listOf("Kwame Mensah" to "28 min • 1.84 GB", "Ama Owusu" to "12 min • 640 MB", "Kofi Asante" to "8 min • 210 MB").forEachIndexed { i, item -> InfoRow("SESSION ${i + 1}", item.first, item.second, if (i == 0) Green else TextPrimary); if (i < 2) { Spacer(Modifier.height(14.dp)); RowDivider(); Spacer(Modifier.height(14.dp)) } } } } }

@Composable fun ProviderIncomingScreen(onReview: () -> Unit, onReject: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { title("Incoming Request", "A trusted friend wants to use your connection")(); Spacer(Modifier.height(28.dp)); Avatar("KM", Blue, 80.dp); Spacer(Modifier.height(12.dp)); Text("Kwame Mensah", color = TextPrimary, fontSize = 19.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("REQUEST", "Connection access", "You remain in control", Yellow) }; Spacer(Modifier.weight(1f)); PrimaryButton("REVIEW REQUEST", onReview); Spacer(Modifier.height(8.dp)); PrimaryButton("REJECT", onReject, color = Red, outline = true); Spacer(Modifier.height(24.dp)) } }
@Composable fun ProviderAuthorizationScreen(onAuthorize: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Authorize Access", "Approve this friend before sharing data")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("PEER", "Kwame Mensah", "Verified trusted peer", Green); Spacer(Modifier.height(16.dp)); InfoRow("SCOPE", "Internet access", "You can stop sharing anytime", Blue) }; Spacer(Modifier.weight(1f)); PrimaryButton("AUTHORIZE", onAuthorize, color = Green); Spacer(Modifier.height(24.dp)) } }
@Composable fun ProviderSharingSetupScreen(onStart: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Sharing Setup", "Prepare this device to share its connection")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("SECURITY", "READY", "Protected tunnel credentials", Green); Spacer(Modifier.height(14.dp)); InfoRow("LIMIT", "UNLIMITED", "Session can be stopped manually", TextPrimary) }; Spacer(Modifier.weight(1f)); PrimaryButton("START SHARING", onStart, color = Green); Spacer(Modifier.height(24.dp)) } }
@Composable fun ProviderSharingActiveScreen(onLiveUsage: () -> Unit, onStop: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(8.dp)); title("Sharing Active", "Your connection is being shared securely")(); Spacer(Modifier.height(24.dp)); Ring(Green, 180.dp, pulse = true, label = "SHARING"); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("CONNECTED PEER", "Kwame Mensah", "1 active connection", Green); Spacer(Modifier.height(14.dp)); InfoRow("DATA SHARED", "640 MB", "Current session", Blue) }; Spacer(Modifier.weight(1f)); PrimaryButton("LIVE USAGE", onLiveUsage, outline = true); Spacer(Modifier.height(8.dp)); PrimaryButton("STOP SHARING", onStop, color = Red); Spacer(Modifier.height(24.dp)) } }
@Composable fun ProviderLiveUsageScreen(onKill: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Live Usage", "Current provider traffic")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("PEER", "Kwame Mensah", "Verified trusted connection", Green); Spacer(Modifier.height(14.dp)); InfoRow("UPLINK", "2.4 Mbps", "Provider traffic", Blue); Spacer(Modifier.height(14.dp)); InfoRow("DATA", "640 MB", "This session", TextPrimary, true) }; Spacer(Modifier.weight(1f)); PrimaryButton("STOP CONNECTION", onKill, color = Red); Spacer(Modifier.height(24.dp)) } }

@Composable fun ConnectionLostScreen(onReconnect: () -> Unit, onHome: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.weight(1f)); Ring(Red, 170.dp, pulse = true, label = "LOST"); Spacer(Modifier.height(20.dp)); Text("Connection Lost", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text("The secure path was interrupted", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.weight(1f)); PrimaryButton("RECONNECT", onReconnect, color = Blue); Spacer(Modifier.height(8.dp)); PrimaryButton("HOME", onHome, outline = true); Spacer(Modifier.height(24.dp)) } }
@Composable fun ReconnectingScreen(onSkip: () -> Unit) = RxConnectingScreen(onSkip)
@Composable fun NetworkSwitchingScreen(onContinue: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.weight(1f)); Ring(Yellow, 170.dp, pulse = true, label = "SWITCH"); Spacer(Modifier.height(20.dp)); Text("Switching Network", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text("Finding the safest available path", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.weight(1f)); PrimaryButton("CONTINUE", onContinue); Spacer(Modifier.height(24.dp)) } }
@Composable fun SessionExpiredScreen(onNewSession: () -> Unit, onHome: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Session Expired", "This connection credential is no longer valid")(); Spacer(Modifier.weight(1f)); PrimaryButton("START NEW SESSION", onNewSession); Spacer(Modifier.height(8.dp)); PrimaryButton("HOME", onHome, outline = true); Spacer(Modifier.height(24.dp)) } }
@Composable fun KeyRevokedScreen(onHome: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { title("Key Revoked", "This device credential must be re-established")(); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("SECURITY", "BLOCKED", "The old session cannot continue", Red, true) }; Spacer(Modifier.weight(1f)); PrimaryButton("RETURN HOME", onHome); Spacer(Modifier.height(24.dp)) } }
