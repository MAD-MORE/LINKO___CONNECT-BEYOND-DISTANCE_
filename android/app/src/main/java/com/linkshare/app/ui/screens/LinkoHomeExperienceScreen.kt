package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoConnectionLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.BlueSoft
import com.linkshare.app.ui.theme.Card
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.GreenSoft
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.Surface
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import kotlinx.coroutines.launch

@Composable
fun LinkoHomeExperienceScreen(
    onFriends: () -> Unit,
    onNotifications: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onProvider: () -> Unit,
    onReceiver: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    val health by com.linkshare.app.ui.components.LinkoNetworkHealthMonitor.snapshot.collectAsStateWithLifecycle()
    val friend = LinkoFriendsApiHolder.selected
    var simpleMode by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val peerName = state.peerDisplayName ?: friend?.displayName
    val phase = state.phase
    val connected = phase == LinkoConnectionPhase.Connected
    val failed = phase == LinkoConnectionPhase.Failed
    val active = phase != LinkoConnectionPhase.Idle
    val quality = when {
        !health.available -> "CHECKING"
        health.score >= 80 -> "EXCELLENT"
        health.score >= 60 -> "GOOD"
        health.score >= 40 -> "WEAK"
        else -> "POOR"
    }
    val qualityColor = when {
        !health.available -> Blue
        health.score >= 80 -> Green
        health.score >= 60 -> Blue
        health.score >= 40 -> Yellow
        else -> Red
    }
    val ringColor = when {
        connected -> Green
        failed -> Red
        active -> Yellow
        else -> Blue
    }
    val headline = when {
        connected -> "YOU ARE CONNECTED"
        failed -> "LINKO NEEDS ATTENTION"
        phase == LinkoConnectionPhase.Signaling -> "WAITING FOR PEER"
        phase == LinkoConnectionPhase.Establishing || phase == LinkoConnectionPhase.Securing || phase == LinkoConnectionPhase.Routing -> "BUILDING YOUR PATH"
        active -> "LINKO IS CONNECTING"
        else -> "CONNECT BEYOND DISTANCE"
    }
    val subhead = when {
        connected -> "${peerName ?: "Your provider"} is routing internet to this phone."
        failed -> state.detail.ifBlank { state.error ?: "The last connection did not complete." }
        active -> state.detail.ifBlank { "LINKO is handling verification, signaling and path selection automatically." }
        friend != null -> "Ready to connect through ${friend.displayName}."
        else -> "Choose a trusted friend and LINKO handles the hard parts."
    }

    LaunchedEffect(Unit) { com.linkshare.app.ui.components.LinkoNetworkHealthMonitor.start(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("LINKO", color = Blue, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(
                    auth.currentDisplayName().orEmpty().ifBlank { "YOUR CONNECTION" }.uppercase(),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TinyHomeAction(Icons.Filled.Notifications, "Notifications", onNotifications)
                TinyHomeAction(Icons.Filled.Settings, "Settings", onSettings)
            }
        }

        Spacer(Modifier.height(16.dp))
        LinkoStatusPill(headline, ringColor)
        Spacer(Modifier.height(12.dp))

        Ring(
            color = ringColor,
            size = if (simpleMode) 232.dp else 210.dp,
            idle = !active,
            pulse = active,
            fast = connected,
            incomingFlow = active,
            label = when {
                connected -> "ONLINE"
                failed -> "RETRY"
                active -> "LINKING"
                friend != null -> "CONNECT"
                else -> "READY"
            },
            onClick = {
                if (connected || active) return@Ring
                if (friend == null) onFriends() else onReceiver()
            },
        )

        Spacer(Modifier.height(12.dp))
        Text(headline, color = TextPrimary, fontSize = if (simpleMode) 20.sp else 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(subhead, color = if (failed) Red else TextSub, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(14.dp))
        friend?.let { selectedFriend ->
            LinkoCard(Modifier.fillMaxWidth().border(1.dp, if (connected) Green.copy(alpha = .4f) else Blue.copy(alpha = .18f), RoundedCornerShape(18.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (connected) GreenSoft else BlueSoft), contentAlignment = Alignment.Center) {
                        Text(selectedFriend.displayName.take(1).uppercase(), color = if (connected) Green else Blue, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (connected) "INTERNET SOURCE" else "SELECTED PROVIDER", color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(selectedFriend.displayName, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(selectedFriend.username?.let { "@$it" } ?: selectedFriend.linkoId, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                    }
                    Text(if (selectedFriend.isSharing || connected) "READY" else if (selectedFriend.isOnline) "ONLINE" else "OFFLINE", color = if (selectedFriend.isSharing || connected) Green else if (selectedFriend.isOnline) Blue else TextMuted, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        when {
            connected -> LiveSessionCard(state, quality, qualityColor, onHistory)
            failed -> RecoveryCard(
                detail = state.error ?: state.detail,
                onRetry = {
                    if (!connecting) {
                        connecting = true
                        scope.launch {
                            LinkoEngineBridge.reconnect()
                            connecting = false
                        }
                    }
                },
                onStop = { LinkoConnectionLifecycle.stop(context) },
            )
            active -> ProgressCard(phase, state.detail, ringColor)
            else -> {
                PrimaryButton(
                    if (friend == null) "CHOOSE A FRIEND" else if (connecting) "CONNECTING…" else "CONNECT",
                    {
                        if (friend == null) onFriends()
                        else if (!connecting) {
                            connecting = true
                            LinkoEngineBridge.connectToFriend(friend.userId, friend.displayName, friend.linkoId) { status ->
                                if (status == "connected" || status.startsWith("failed")) connecting = false
                            }
                        }
                    },
                    color = Blue,
                    loading = connecting,
                    enabled = !connecting,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        if (!connected) {
            SecondaryHomeAction(Icons.Filled.Share, "SHARE MY INTERNET", "Become the provider for a trusted friend", Green) { onProvider() }
        } else {
            SecondaryHomeAction(Icons.Filled.Speed, "NETWORK QUALITY", "$quality • ${if (state.latencyMs > 0) "${state.latencyMs} ms" else "measuring"}", qualityColor) { onHistory() }
        }

        Spacer(Modifier.height(10.dp))
        SecurityStrip(active = active, connected = connected, failed = failed)

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickNav(Icons.Filled.People, "FRIENDS", onFriends, Modifier.weight(1f))
            QuickNav(Icons.Filled.History, "HISTORY", onHistory, Modifier.weight(1f))
            QuickNav(Icons.Filled.Settings, "SETTINGS", onSettings, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Card).border(1.dp, TextMuted.copy(alpha = .12f), RoundedCornerShape(15.dp)).clickable { simpleMode = !simpleMode }.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EASY MODE", color = if (simpleMode) Green else Blue, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(if (simpleMode) "ON • larger controls and simpler language" else "OFF • full technical view", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun TinyHomeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp).clip(CircleShape).background(Blue.copy(alpha = .08f))) {
        Icon(icon, contentDescription = description, tint = Blue, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LinkoStatusPill(text: String, color: Color) {
    Text(text, color = color, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = .11f)).border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun ProgressCard(phase: LinkoConnectionPhase, detail: String, color: Color) {
    val steps = listOf(
        LinkoConnectionPhase.Connecting to "START",
        LinkoConnectionPhase.Authenticating to "VERIFY",
        LinkoConnectionPhase.Signaling to "SIGNAL",
        LinkoConnectionPhase.Establishing to "PATH",
        LinkoConnectionPhase.Securing to "SECURE",
        LinkoConnectionPhase.Routing to "ROUTE",
    )
    LinkoCard {
        Text("CONNECTION PROGRESS", color = color, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEach { (step, label) ->
                val active = phase == step
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(4.dp)).background(if (active) color else TextMuted.copy(alpha = .11f)))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(detail.ifBlank { "LINKO is finding the best available path." }, color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun LiveSessionCard(state: com.linkshare.app.network.LinkoEngineConnectionState, quality: String, qualityColor: Color, onHistory: () -> Unit) {
    LinkoCard(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(GreenSoft, Surface)))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("LIVE SESSION", color = Green, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(quality, color = qualityColor, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveMetric("DOWN", formatExperienceBytes(state.bytesIn), Blue, Modifier.weight(1f))
            LiveMetric("UP", formatExperienceBytes(state.bytesOut), Green, Modifier.weight(1f))
            LiveMetric("PING", if (state.latencyMs > 0) "${state.latencyMs} ms" else "—", qualityColor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("Protected tunnel • provider internet • automatic path recovery", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(8.dp))
        Text("VIEW SESSION HISTORY ›", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onHistory() })
    }
}

@Composable
private fun LiveMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .07f)).padding(9.dp)) {
        Text(label, color = TextSub, fontSize = 7.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecoveryCard(detail: String?, onRetry: () -> Unit, onStop: () -> Unit) {
    LinkoCard(Modifier.fillMaxWidth().border(1.dp, Red.copy(alpha = .25f), RoundedCornerShape(18.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Red, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("CONNECTION INTERRUPTED", color = Red, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Text("LINKO could not keep the last path alive. You can try again or stop the session.", color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(detail.replace('_', ' '), color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("TRY AGAIN", onRetry, color = Blue)
            PrimaryButton("STOP", onStop, color = Red, outline = true)
        }
    }
}

@Composable
private fun SecurityStrip(active: Boolean, connected: Boolean, failed: Boolean) {
    val color = when {
        failed -> Red
        connected -> Green
        active -> Yellow
        else -> Blue
    }
    LinkoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("LINKO SECURE", color = color, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        connected -> "Identity verified • session authenticated • protected tunnel active"
                        active -> "Verifying identity and securing the connection"
                        else -> "Trusted peers, authenticated sessions and protected transport"
                    },
                    color = TextSub,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun SecondaryHomeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = .08f)).border(1.dp, color.copy(alpha = .18f), RoundedCornerShape(15.dp)).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
        Text("›", color = color, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickNav(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(Card).clickable { onClick() }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = TextSub, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextSub, fontSize = 7.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

private fun formatExperienceBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
