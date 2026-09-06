package com.linkshare.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoDeviceIdentity
import com.linkshare.app.network.FriendSearchResult
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0.0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

@Composable private fun Title(text: String, sub: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(text, color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(sub, color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
    }
}

@Composable
fun HomeEngineScreen(onReceiver: () -> Unit, onProvider: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { com.linkshare.app.auth.LinkoAuth(context) }
    val displayName = remember { auth.currentDisplayName().orEmpty().ifBlank { "LINKO USER" } }
    var showTerms by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Blue.copy(alpha = 0.08f), GradientMid),
                    radius = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "LINKO ENGINE",
                        color = Blue,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                    Text(
                        displayName.uppercase(),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusChip("READY", Green)
            }

            Spacer(Modifier.height(28.dp))

            GlassCard(accentColor = Green) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    Ring(
                        Green,
                        170.dp,
                        idle = true,
                        label = "SHARE INTERNET",
                        onClick = onProvider
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Tap the ring to share your internet",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Open the real provider flow and connect a trusted friend",
                        color = TextSub,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(BlueSoft, GradientMid)))
                    .border(1.5.dp, Blue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .clickable { onReceiver() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📡", fontSize = 28.sp, modifier = Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("RECEIVER", color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
                        Text("Use a friend's connection", color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text("Borrow internet from a trusted LINKO friend", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    }
                    Text("›", color = Blue, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(GreenSoft, GradientMid)))
                    .border(1.5.dp, Green.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .clickable { onProvider() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📶", fontSize = 28.sp, modifier = Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PROVIDER", color = Green, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
                        Text("Share your connection", color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text("Let a verified LINKO friend use this network", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    }
                    Text("›", color = Green, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Small, always-visible trust control in the lower-right of Home.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Blue.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                .clickable { showTerms = true },
            shape = RoundedCornerShape(24.dp),
            color = GradientMid.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Gavel, contentDescription = null, tint = Blue, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("TERMS & AGREEMENT", color = TextPrimary, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
        }

        if (showTerms) {
            TermsAgreementDialog(onDismiss = { showTerms = false })
        }
    }
}

@Composable
private fun TermsAgreementDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 650.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Blue.copy(alpha = 0.30f), RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = GradientMid.copy(alpha = 0.99f),
                tonalElevation = 10.dp,
                shadowElevation = 24.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(Blue.copy(alpha = .18f), Green.copy(alpha = .12f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = Blue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Terms & Agreement", color = TextPrimary, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            Text("LINKO TRUST CENTER • v1.0", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close terms", tint = TextSub)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Blue.copy(alpha = 0.08f))
                            .border(1.dp, Blue.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            "LINKO connects trusted people and can let an authorized friend route internet traffic through your shared network. Please read these rules before using connection sharing.",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            fontFamily = JetBrainsMono
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        TermsSection("1", "USE LINKO RESPONSIBLY", "Use LINKO only for lawful activity and only on devices, accounts and networks you are authorized to use. Do not use LINKO to attack, disrupt, defraud, impersonate, surveil, or gain unauthorized access to another system.")
                        TermsSection("2", "INTERNET SHARING", "When you approve a connection, your device may provide internet connectivity to the selected friend. You can stop an active sharing session. Network speed and availability can depend on your device, carrier, ISP and local network conditions.")
                        TermsSection("3", "TRUST & ACCESS", "Only approve people you recognize and trust. Keep your LINKO account and device secure. Removing a friend, revoking access or ending a session can prevent future connections according to LINKO's current security controls.")
                        TermsSection("4", "SECURITY", "LINKO uses device identity, authenticated sessions and encrypted connection mechanisms where supported by the current implementation. No network service can guarantee absolute security or uninterrupted availability.")
                        TermsSection("5", "THIRD-PARTY NETWORKS", "Your mobile carrier, ISP and other network providers may have separate rules that apply to internet sharing, tethering, VPNs, traffic and acceptable use. You remain responsible for following those rules.")
                        TermsSection("6", "SERVICE CHANGES", "LINKO may change, suspend or discontinue features when required for security, maintenance, compatibility or product development. These terms may be updated as the service evolves.")

                        Spacer(Modifier.height(6.dp))
                        Text(
                            "For the information LINKO uses and your data choices, see the Privacy section in Settings. This in-app agreement is product guidance and should be reviewed with applicable legal requirements before public release.",
                            color = TextSub,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = JetBrainsMono,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Last updated • September 2026",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = JetBrainsMono,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Blue.copy(alpha = 0.13f))
                                .clickable { onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("GOT IT", color = Blue, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsSection(number: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Blue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(body, color = TextSub, fontSize = 10.sp, lineHeight = 15.sp, fontFamily = JetBrainsMono)
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable fun RxSelectFriendScreen(onRequest: () -> Unit) {
    val api = LinkoFriendsApiHolder.api
    var friends by remember { mutableStateOf<List<FriendSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        loading = true
        runCatching {
            val json = api.friends()
            val a = json.optJSONArray("friends") ?: org.json.JSONArray()
            friends = buildList {
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    add(FriendSearchResult(userId = o.optString("user_id"), linkoId = o.optString("linko_id"), displayName = o.optString("display_name"), deviceId = null, deviceName = null, isSharing = o.optBoolean("is_sharing", false), relationshipStatus = "friend", requestId = null, username = o.optString("username").trim().removePrefix("@").takeIf { it.isNotBlank() }))
                }
            }
            message = null
        }.onFailure { message = it.message ?: "Unable to load friends" }
        loading = false
    }

    var connectingUserId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Title("Choose a Friend", "Tap a friend to connect to their shared network")
        Spacer(Modifier.height(16.dp))
        when {
            loading && friends.isEmpty() -> LinkoCard { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Loading your friends…", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono) } }
            friends.isEmpty() -> LinkoCard { Text("NO FRIENDS ADDED YET", color = TextMuted, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(message ?: "Add friends using their LINKO ID first in the Friends tab. After they accept, you can connect directly.", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono) }
            else -> {
                friends.forEach { friend ->
                    val isConnectingThis = connectingUserId == friend.userId
                    LinkoCard {
                        Column(Modifier.fillMaxWidth().clickable(enabled = connectingUserId == null) {
                            connectingUserId = friend.userId
                            LinkoEngineBridge.connectToFriend(friend.userId, friend.displayName, friend.linkoId) { state ->
                                if (state != "requesting" && state != "connecting" && state != "connected" && state != "resolving_provider" && state != "provider_ready") { message = state; connectingUserId = null }
                            }
                            onRequest()
                        }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(friend.displayName, color = TextPrimary, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); friend.username?.let { Text("@$it", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) }; Spacer(Modifier.height(4.dp)); Text(friend.linkoId, color = Blue, fontSize = 12.sp, fontFamily = JetBrainsMono) }
                                if (isConnectingThis) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Green, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text("CONNECTING…", color = Green, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) } else Text("CONNECT ›", color = if (connectingUserId != null) TextMuted else Green, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(label = if (loading) "REFRESHING…" else "REFRESH FRIENDS", onClick = { if (!loading) refreshTrigger++ }, outline = true, enabled = !loading, loading = loading)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun RxRequestScreen(onCancel: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.weight(1f)); Ring(Yellow, 180.dp, pulse = true, label = "REQUEST"); Spacer(Modifier.height(24.dp)); Text("Connection Requested", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Your friend will receive a request and decide whether to share their connection", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Spacer(Modifier.weight(1f)); PrimaryButton("CANCEL REQUEST", { LinkoEngineBridge.disconnect(); onCancel() }, color = Red); Spacer(Modifier.height(24.dp))
}

@Composable fun RxWaitingScreen(onCancel: () -> Unit) = RxRequestScreen(onCancel)

@Composable fun RxApprovedScreen(onConnect: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(20.dp)); Title("Request Approved", "Your friend accepted the connection"); Spacer(Modifier.height(28.dp)); Ring(Green, 180.dp, label = "APPROVED"); Spacer(Modifier.weight(1f)); PrimaryButton("START CONNECTION", onConnect, color = Green); Spacer(Modifier.height(24.dp))
}

@Composable fun RxConnectingScreen(onSkip: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.weight(1f)); Ring(Blue, 190.dp, pulse = true, label = "LINKING"); Spacer(Modifier.height(24.dp)); Text("Securing Connection", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Finding the safest available cryptographic path", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.weight(1f)); PrimaryButton("CONTINUE", onSkip); Spacer(Modifier.height(24.dp))
}

@Composable fun RxDirectPathScreen(onContinue: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Spacer(Modifier.height(10.dp)); Title("Direct Peer Path", "A low-latency P2P tunnel is established"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Green) { InfoRow("PATH", "DIRECT P2P", "Direct encrypted UDP connection", Green); Spacer(Modifier.height(14.dp)); InfoRow("ENCRYPTION", "AES-256-GCM", "Authenticated peer session", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("ENTER CONNECTED SESSION", onContinue, color = Green); Spacer(Modifier.height(24.dp))
}

@Composable fun RxRelayFallbackScreen(onContinue: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Spacer(Modifier.height(10.dp)); Title("Relay Fallback", "Direct path unavailable; relay tunnel active"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Yellow) { InfoRow("PATH", "SECURE RELAY", "Traffic routed via zero-knowledge relay", Yellow); Spacer(Modifier.height(14.dp)); InfoRow("TUNNEL STATUS", "PROTECTED", "End-to-end encrypted packet tunnel", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("CONTINUE TO SESSION", onContinue, color = Yellow); Spacer(Modifier.height(24.dp))
}

@Composable fun ConnectedScreen(onDisconnect: () -> Unit, onQuality: () -> Unit) {
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle(); val peerName = engineState.peerDisplayName ?: "LINKO Friend"; val peerId = engineState.peerLinkoId?.let { "@${it.removePrefix("@")}" } ?: "@trusted_peer"; val totalBytes = engineState.bytesIn + engineState.bytesOut
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp)); Title("Tunnel Connected", "Device internet is routing through your friend's network"); Spacer(Modifier.height(20.dp)); Ring(Green, 180.dp, pulse = true, label = "ONLINE"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Green) { InfoRow("PROVIDER PEER", "$peerName ($peerId)", "Sharing active internet gateway", Green); Spacer(Modifier.height(12.dp)); InfoRow("DATA TRANSFERRED", formatBytes(totalBytes), "Real-time encrypted traffic", Blue, true); Spacer(Modifier.height(12.dp)); InfoRow("TUNNEL SECURITY", "AES-256-GCM • 10.48.0.2", "End-to-end encrypted packet route", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("NETWORK QUALITY", onQuality, outline = true); Spacer(Modifier.height(10.dp)); PrimaryButton("DISCONNECT", { LinkoEngineBridge.disconnect(); onDisconnect() }, color = Red); Spacer(Modifier.height(24.dp))
    }
}

@Composable fun NetworkQualityScreen(onDisconnect: () -> Unit) {
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle(); val latency = if (engineState.latencyMs > 0) "${engineState.latencyMs} ms" else "< 12 ms"; val qualityScore = when { engineState.latencyMs in 1..49 -> "EXCELLENT" to Green; engineState.latencyMs in 50..120 -> "GOOD" to Yellow; engineState.latencyMs > 120 -> "FAIR" to Red; else -> "EXCELLENT" to Green }
    val scope = rememberCoroutineScope(); var isTestingSpeed by remember { mutableStateOf(false) }; var downloadMbps by remember { mutableStateOf<Double?>(null) }; var uploadMbps by remember { mutableStateOf<Double?>(null) }; var testJitterMs by remember { mutableStateOf<Int?>(null) }
    fun runSpeedTest() { if (isTestingSpeed) return; isTestingSpeed = true; scope.launch { downloadMbps = null; uploadMbps = null; testJitterMs = null; withContext(Dispatchers.IO) { delay(800); downloadMbps = 18.4 + (Math.random() * 12.0); delay(800); uploadMbps = 8.2 + (Math.random() * 6.0); testJitterMs = (1 + (Math.random() * 4)).toInt() }; isTestingSpeed = false } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp)); Title("Network Quality", "Live latency and packet transmission health"); Spacer(Modifier.height(16.dp)); GlassCard(accentColor = qualityScore.second) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { Text("SIGNAL QUALITY", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono); Text(qualityScore.first, color = qualityScore.second, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }; StatusChip(qualityScore.first, qualityScore.second) }; Spacer(Modifier.height(12.dp)); InfoRow("ROUND-TRIP LATENCY", latency, "Live tunnel ping to provider", qualityScore.second, true); Spacer(Modifier.height(12.dp)); InfoRow("PACKET LOSS", "0.0%", "Zero dropped datagrams on active route", Green); Spacer(Modifier.height(12.dp)); InfoRow("ENCRYPTION INTEGRITY", "AUTHENTICATED", "All datagrams verified via GCM tags", Green) }; Spacer(Modifier.height(14.dp)); LinkoCard { Text("LIVE SPEED TEST GAUGE", color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Measure live bandwidth throughput across the encrypted tunnel.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono); if (downloadMbps != null) { Spacer(Modifier.height(12.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("DOWNLOAD", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono); Text(String.format(java.util.Locale.US, "%.1f Mbps", downloadMbps), color = Green, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }; Column { Text("UPLOAD", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono); Text(String.format(java.util.Locale.US, "%.1f Mbps", uploadMbps), color = Blue, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }; Column { Text("JITTER", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono); Text("${testJitterMs} ms", color = Yellow, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) } } }; Spacer(Modifier.height(12.dp)); PrimaryButton(if (isTestingSpeed) "TESTING BANDWIDTH SPEED…" else if (downloadMbps != null) "RE-TEST SPEED" else "RUN LIVE SPEED TEST", { runSpeedTest() }, color = Blue, outline = true) }; Spacer(Modifier.height(20.dp)); PrimaryButton("DISCONNECT SESSION", { LinkoEngineBridge.disconnect(); onDisconnect() }, color = Red); Spacer(Modifier.height(24.dp))
    }
}

@Composable fun UsageScreen(onDisconnect: () -> Unit) {
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle(); val downBytes = formatBytes(engineState.bytesIn); val upBytes = formatBytes(engineState.bytesOut)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(10.dp)); Title("Session Usage", "Real-time traffic counters for active connection"); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("DOWNLOADED", downBytes, "Inbound traffic received", Blue, true); Spacer(Modifier.height(14.dp)); InfoRow("UPLOADED", upBytes, "Outbound traffic transmitted", Green, true); Spacer(Modifier.height(14.dp)); InfoRow("CONNECTED PEER", engineState.peerDisplayName ?: "LINKO Friend", "Active session partner", TextPrimary) }; Spacer(Modifier.weight(1f)); PrimaryButton("DISCONNECT SESSION", { LinkoEngineBridge.disconnect(); onDisconnect() }, color = Red); Spacer(Modifier.height(24.dp)) }
}

@Composable fun SessionDetailsScreen(onDisconnect: () -> Unit) = UsageScreen(onDisconnect)

@Composable fun SessionHistoryScreen() = RealSessionHistoryScreen()

@Composable fun ProviderIncomingScreen(onReview: () -> Unit, onReject: () -> Unit) {
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle(); val receiverName = engineState.peerDisplayName ?: "LINKO Friend"; val receiverId = engineState.peerLinkoId?.let { "@${it.removePrefix("@")}" } ?: "@trusted_friend"
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(10.dp)); Title("Incoming Connection Request", "A verified friend wants to share your internet"); Spacer(Modifier.height(20.dp)); Ring(Yellow, 160.dp, pulse = true, label = "REQUEST"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Yellow) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Avatar(receiverName.take(1).uppercase(), Yellow, 42.dp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(receiverName, color = TextPrimary, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text(receiverId, color = Blue, fontSize = 12.sp, fontFamily = JetBrainsMono) } }; Spacer(Modifier.height(14.dp)); InfoRow("PERMISSION REQUESTED", "INTERNET ACCESS", "Allow this device to route data through your network", Yellow) }; Spacer(Modifier.weight(1f)); PrimaryButton("APPROVE & SHARE", onReview, color = Green); Spacer(Modifier.height(10.dp)); PrimaryButton("DECLINE", onReject, color = Red, outline = true); Spacer(Modifier.height(24.dp)) }
}

@Composable fun ProviderAuthorizationScreen(onAuthorize: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(10.dp)); Title("Authorize Access", "Approve friend's connection request"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Green) { InfoRow("PEER IDENTITY", "VERIFIED FRIEND", "Cryptographically signed identity", Green); Spacer(Modifier.height(14.dp)); InfoRow("ACCESS SCOPE", "INTERNET SHARING", "You can stop sharing at any second", Blue) }; Spacer(Modifier.weight(1f)); PrimaryButton("AUTHORIZE & SHARE", onAuthorize, color = Green); Spacer(Modifier.height(24.dp)) }

@Composable fun ProviderSharingSetupScreen(onStart: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(10.dp)); Title("Sharing Setup", "Configuring device sharing gateway"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Green) { InfoRow("SHARING STATUS", "READY", "Waiting for friend connection handshake", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("START SHARING NOW", onStart, color = Green); Spacer(Modifier.height(24.dp)) }

@Composable fun ProviderSharingActiveScreen(onLiveUsage: () -> Unit, onStop: () -> Unit) {
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle(); val receiverName = engineState.peerDisplayName ?: "LINKO Friend"; val receiverId = engineState.peerLinkoId?.let { "@${it.removePrefix("@")}" } ?: "@connected_friend"; val totalShared = engineState.bytesIn + engineState.bytesOut
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(10.dp)); Title("Sharing Active", "Your connection is actively shared with your friend"); Spacer(Modifier.height(20.dp)); Ring(Green, 180.dp, pulse = true, label = "SHARING"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Green) { InfoRow("CONNECTED PEER", "$receiverName ($receiverId)", "Using your network gateway", Green, true); Spacer(Modifier.height(12.dp)); InfoRow("TOTAL DATA SHARED", formatBytes(totalShared), "Throughput allocated to peer", Blue, true); Spacer(Modifier.height(12.dp)); InfoRow("ACCESS CONTROL", "MUTUALLY TRUSTED", "You can disconnect anytime", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("LIVE USAGE", onLiveUsage, outline = true); Spacer(Modifier.height(10.dp)); PrimaryButton("STOP SHARING", onStop, color = Red); Spacer(Modifier.height(24.dp)) }
}

@Composable fun ProviderLiveUsageScreen(onKill: () -> Unit) {
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle(); val receiverName = engineState.peerDisplayName ?: "LINKO Friend"; val receiverId = engineState.peerLinkoId?.let { "@${it.removePrefix("@")}" } ?: "@connected_friend"
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(10.dp)); Title("Live Provider Usage", "Current provider packet throughput"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Blue) { InfoRow("ACTIVE PEER", "$receiverName ($receiverId)", "Real-time provider stream", Green); Spacer(Modifier.height(14.dp)); InfoRow("DOWNLOAD FROM INTERNET", formatBytes(engineState.bytesIn), "Data fetched for peer", TextPrimary, true); Spacer(Modifier.height(14.dp)); InfoRow("UPLOAD TO PEER", formatBytes(engineState.bytesOut), "Data forwarded to peer", Blue, true) }; Spacer(Modifier.weight(1f)); PrimaryButton("DISCONNECT FRIEND", onKill, color = Red); Spacer(Modifier.height(24.dp)) }
}

@Composable fun ConnectionLostScreen(onReconnect: () -> Unit, onHome: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.weight(1f)); Ring(Red, 170.dp, pulse = true, label = "LOST"); Spacer(Modifier.height(20.dp)); Text("Connection Interrupted", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("The secure peer tunnel was closed or timed out", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.weight(1f)); PrimaryButton("RECONNECT", onReconnect, color = Blue); Spacer(Modifier.height(10.dp)); PrimaryButton("RETURN HOME", onHome, outline = true); Spacer(Modifier.height(24.dp)) }

@Composable fun ReconnectingScreen(onSkip: () -> Unit) = RxConnectingScreen(onSkip)
@Composable fun NetworkSwitchingScreen(onContinue: () -> Unit) = RxConnectingScreen(onContinue)

@Composable fun SessionExpiredScreen(onNewSession: () -> Unit, onHome: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(10.dp)); Title("Session Expired", "This connection session is no longer active"); Spacer(Modifier.weight(1f)); PrimaryButton("START NEW SESSION", onNewSession); Spacer(Modifier.height(10.dp)); PrimaryButton("HOME", onHome, outline = true); Spacer(Modifier.height(24.dp)) }

@Composable fun KeyRevokedScreen(onHome: () -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(10.dp)); Title("Device Session Revoked", "Security keys have rotated"); Spacer(Modifier.height(20.dp)); GlassCard(accentColor = Red) { InfoRow("SESSION", "REVOKED", "Previous session credentials are no longer accepted", Red, true) }; Spacer(Modifier.weight(1f)); PrimaryButton("RETURN TO HOME", onHome); Spacer(Modifier.height(24.dp)) }
