package com.linkshare.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.FriendSearchResult
import com.linkshare.app.network.LinkoConnectionLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoEngineConnectionState
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoNetworkHealthMonitor
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.BlueSoft
import com.linkshare.app.ui.theme.Card
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.GreenSoft
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import kotlinx.coroutines.launch
import java.util.Locale

private enum class LinkoDashboardMode {
    Ready,
    Connecting,
    Connected,
    Recovering,
    Failed,
    Sharing,
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val health by LinkoNetworkHealthMonitor.snapshot.collectAsStateWithLifecycle()
    val selectedFriend = LinkoFriendsApiHolder.selected
    val providerActive = LinkoProviderService.isRunning
    var easyMode by remember { mutableStateOf(false) }
    var showPowerSheet by remember { mutableStateOf(false) }
    var showFriendSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { LinkoNetworkHealthMonitor.start(context) }

    val phase = state.phase
    val connected = phase == LinkoConnectionPhase.Connected && !providerActive
    val failed = phase == LinkoConnectionPhase.Failed
    val recovering = !failed && (
        state.detail.contains("recover", ignoreCase = true) ||
            state.detail.contains("reconnect", ignoreCase = true) ||
            state.error?.contains("recover", ignoreCase = true) == true ||
            state.error?.contains("reconnect", ignoreCase = true) == true
        )
    val activeConnect = !connected && !failed && !recovering && phase != LinkoConnectionPhase.Idle

    val mode = when {
        providerActive -> LinkoDashboardMode.Sharing
        connected -> LinkoDashboardMode.Connected
        recovering -> LinkoDashboardMode.Recovering
        failed -> LinkoDashboardMode.Failed
        activeConnect -> LinkoDashboardMode.Connecting
        else -> LinkoDashboardMode.Ready
    }

    val qualityText = when {
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
    val modeColor = when (mode) {
        LinkoDashboardMode.Ready -> Blue
        LinkoDashboardMode.Connecting -> Yellow
        LinkoDashboardMode.Connected -> Green
        LinkoDashboardMode.Recovering -> Blue
        LinkoDashboardMode.Failed -> Red
        LinkoDashboardMode.Sharing -> Green
    }
    val modeTitle = when (mode) {
        LinkoDashboardMode.Ready -> "READY"
        LinkoDashboardMode.Connecting -> "CONNECTING"
        LinkoDashboardMode.Connected -> "CONNECTED"
        LinkoDashboardMode.Recovering -> "RECOVERING"
        LinkoDashboardMode.Failed -> "CONNECTION FAILED"
        LinkoDashboardMode.Sharing -> "SHARING INTERNET"
    }
    val modeSubtitle = when (mode) {
        LinkoDashboardMode.Ready -> selectedFriend?.let { "Ready to connect to ${it.displayName}." } ?: "Choose a trusted friend or share your internet."
        LinkoDashboardMode.Connecting -> state.detail.ifBlank { "LINKO is handling the connection automatically." }
        LinkoDashboardMode.Connected -> "Internet is coming from ${state.peerDisplayName ?: selectedFriend?.displayName ?: "your friend"}."
        LinkoDashboardMode.Recovering -> "LINKO is restoring the connection automatically."
        LinkoDashboardMode.Failed -> state.error?.replace('_', ' ') ?: state.detail.ifBlank { "The connection could not be completed." }
        LinkoDashboardMode.Sharing -> "Your internet is available to trusted friends."
    }
    val ringLabel = when (mode) {
        LinkoDashboardMode.Ready -> "READY"
        LinkoDashboardMode.Connecting -> "LINKING"
        LinkoDashboardMode.Connected -> "ONLINE"
        LinkoDashboardMode.Recovering -> "RECOVER"
        LinkoDashboardMode.Failed -> "RETRY"
        LinkoDashboardMode.Sharing -> "SHARING"
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LINKO", color = Blue, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(auth.currentDisplayName().orEmpty().ifBlank { "YOUR CONNECTION" }.uppercase(), color = TextPrimary, fontSize = if (easyMode) 18.sp else 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TinyAction(Icons.Filled.Notifications, "Notifications", onNotifications)
                TinyAction(Icons.Filled.Settings, "Settings", onSettings)
            }
        }

        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = .96f)).togetherWith(fadeOut() + scaleOut(targetScale = 1.02f)).using(SizeTransform(clip = false)) },
            label = "dashboard-state",
        ) { currentMode -> DashboardHeader(currentMode, modeColor, modeTitle, modeSubtitle) }

        Spacer(Modifier.height(if (easyMode) 8.dp else 12.dp))
        Ring(
            color = modeColor,
            size = if (easyMode) 242.dp else 218.dp,
            idle = mode == LinkoDashboardMode.Ready,
            pulse = mode != LinkoDashboardMode.Ready,
            fast = mode == LinkoDashboardMode.Connected || mode == LinkoDashboardMode.Sharing,
            incomingFlow = mode == LinkoDashboardMode.Connecting || mode == LinkoDashboardMode.Connected || mode == LinkoDashboardMode.Sharing,
            label = ringLabel,
            onClick = { if (mode == LinkoDashboardMode.Ready || mode == LinkoDashboardMode.Failed) showPowerSheet = true },
        )

        Spacer(Modifier.height(10.dp))
        PrimaryControl(mode, busy, {
            when (mode) {
                LinkoDashboardMode.Ready, LinkoDashboardMode.Failed -> showPowerSheet = true
                LinkoDashboardMode.Connected, LinkoDashboardMode.Sharing, LinkoDashboardMode.Connecting, LinkoDashboardMode.Recovering -> {
                    if (!busy) {
                        busy = true
                        LinkoConnectionLifecycle.stop(context)
                        LinkoProviderService.stop(context)
                        scope.launch { kotlinx.coroutines.delay(250); busy = false }
                    }
                }
            }
        }, modeColor)

        Spacer(Modifier.height(10.dp))
        AnimatedVisibility(visible = mode == LinkoDashboardMode.Ready && selectedFriend != null, enter = fadeIn(), exit = fadeOut()) {
            FriendSummaryCard(selectedFriend?.displayName.orEmpty(), selectedFriend?.isOnline == true, selectedFriend?.isSharing == true) { showFriendSheet = true }
        }
        AnimatedVisibility(visible = mode == LinkoDashboardMode.Ready && selectedFriend == null, enter = fadeIn(), exit = fadeOut()) { EmptyFriendCard(onFriends) }
        AnimatedVisibility(visible = mode == LinkoDashboardMode.Connected, enter = fadeIn(), exit = fadeOut()) { LiveCard(state, qualityText, qualityColor, onHistory) }
        AnimatedVisibility(visible = mode == LinkoDashboardMode.Connecting, enter = fadeIn(), exit = fadeOut()) { ProgressCard(phase, state.detail, modeColor) }
        AnimatedVisibility(visible = mode == LinkoDashboardMode.Recovering, enter = fadeIn(), exit = fadeOut()) { RecoveringCard() }
        AnimatedVisibility(visible = mode == LinkoDashboardMode.Failed, enter = fadeIn(), exit = fadeOut()) {
            FailureCard(state.error ?: state.detail) {
                if (selectedFriend != null && !busy) {
                    busy = true
                    LinkoEngineBridge.reconnect { result -> if (result == "connected" || result.startsWith("failed")) busy = false }
                } else showPowerSheet = true
            }
        }
        AnimatedVisibility(visible = mode == LinkoDashboardMode.Sharing, enter = fadeIn(), exit = fadeOut()) { SharingCard { LinkoProviderService.stop(context) } }

        Spacer(Modifier.height(10.dp))
        SecurityStrip(mode)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(Icons.Filled.People, "FRIENDS", onFriends, Modifier.weight(1f))
            QuickAction(Icons.Filled.Share, "SEND", { showPowerSheet = true }, Modifier.weight(1f))
            QuickAction(Icons.Filled.History, "HISTORY", onHistory, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { SecondaryAction(Icons.Filled.Autorenew, "RECOVERY", if (recovering) "Running" else "Automatic", if (recovering) Blue else TextSub) { if (failed) showPowerSheet = true } }
            Box(Modifier.weight(1f)) { SecondaryAction(Icons.Filled.Speed, "QUALITY", qualityText, qualityColor, onHistory) }
        }
        Spacer(Modifier.height(8.dp))
        EasyModeRow(easyMode) { easyMode = !easyMode }
        Spacer(Modifier.height(22.dp))
    }

    if (showPowerSheet) {
        ModalBottomSheet(onDismissRequest = { showPowerSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            PowerSheet(mode, selectedFriend, busy, { showPowerSheet = false }, {
                if (selectedFriend == null) { showPowerSheet = false; onFriends() }
                else if (!busy) {
                    busy = true
                    showPowerSheet = false
                    LinkoEngineBridge.connectToFriend(selectedFriend.userId, selectedFriend.displayName, selectedFriend.linkoId) { result -> if (result == "connected" || result.startsWith("failed")) busy = false }
                }
            }, {
                showPowerSheet = false
                LinkoProviderService.start(context)
            }, {
                showPowerSheet = false
                LinkoConnectionLifecycle.stop(context)
                LinkoProviderService.stop(context)
            })
        }
    }

    if (showFriendSheet) {
        ModalBottomSheet(onDismissRequest = { showFriendSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("CHOOSE FRIEND", color = TextPrimary, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Pick who you want to connect to. LINKO handles the rest.", color = TextSub, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(16.dp))
                ActionSheetRow(Icons.Filled.People, "Open friends", "See trusted people who are online", Blue) { showFriendSheet = false; onFriends() }
                Spacer(Modifier.height(10.dp))
                ActionSheetRow(Icons.Filled.Close, "Keep current friend", selectedFriend?.displayName ?: "No friend selected", TextSub) { showFriendSheet = false }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DashboardHeader(mode: LinkoDashboardMode, color: Color, title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = .10f)).border(1.dp, color.copy(alpha = .22f), RoundedCornerShape(20.dp)).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (mode) {
                LinkoDashboardMode.Ready -> Icons.Filled.PowerSettingsNew
                LinkoDashboardMode.Connecting -> Icons.Filled.Link
                LinkoDashboardMode.Connected -> Icons.Filled.CheckCircle
                LinkoDashboardMode.Recovering -> Icons.Filled.Autorenew
                LinkoDashboardMode.Failed -> Icons.Filled.LinkOff
                LinkoDashboardMode.Sharing -> Icons.Filled.Share
            }
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = color, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = TextSub, fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PrimaryControl(mode: LinkoDashboardMode, busy: Boolean, onClick: () -> Unit, color: Color) {
    val (icon, label) = when (mode) {
        LinkoDashboardMode.Ready -> Icons.Filled.PowerSettingsNew to "POWER ON"
        LinkoDashboardMode.Connecting -> Icons.Filled.Close to "CANCEL"
        LinkoDashboardMode.Connected -> Icons.Filled.LinkOff to "DISCONNECT"
        LinkoDashboardMode.Recovering -> Icons.Filled.StopCircle to "STOP"
        LinkoDashboardMode.Failed -> Icons.Filled.Autorenew to "TRY AGAIN"
        LinkoDashboardMode.Sharing -> Icons.Filled.StopCircle to "STOP SHARING"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .28f), RoundedCornerShape(18.dp)).clickable(enabled = !busy, indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }.padding(vertical = 14.dp, horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (busy) "WORKING…" else label, color = color, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FriendSummaryCard(name: String, online: Boolean, sharing: Boolean, onChange: () -> Unit) {
    LinkoCard(Modifier.fillMaxWidth().clickable { onChange() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(if (sharing) GreenSoft else BlueSoft), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = if (sharing) Green else Blue, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("READY TO CONNECT", color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(name, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(if (sharing) "Sharing internet" else if (online) "Online" else "Offline", color = if (sharing) Green else if (online) Blue else TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
            }
            Icon(Icons.Filled.People, contentDescription = "Change friend", tint = Blue, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyFriendCard(onOpen: () -> Unit) {
    LinkoCard(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Blue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.People, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("NO FRIEND SELECTED", color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text("Choose someone to connect to", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Filled.ArrowForward, contentDescription = "Open friends", tint = Blue)
        }
    }
}

@Composable
private fun LiveCard(state: LinkoEngineConnectionState, quality: String, qualityColor: Color, onHistory: () -> Unit) {
    LinkoCard(Modifier.fillMaxWidth().background(GreenSoft.copy(alpha = .35f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("LIVE CONNECTION", color = Green, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(quality, color = qualityColor, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveMetric("DOWN", formatBytes(state.bytesIn), Blue, Modifier.weight(1f))
            LiveMetric("UP", formatBytes(state.bytesOut), Green, Modifier.weight(1f))
            LiveMetric("PING", if (state.latencyMs > 0) "${state.latencyMs} ms" else "—", qualityColor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("Secure direct connection • automatic recovery", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(6.dp))
        Text("VIEW HISTORY ›", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onHistory() })
    }
}

@Composable
private fun SharingCard(onStop: () -> Unit) {
    LinkoCard(Modifier.fillMaxWidth().background(GreenSoft.copy(alpha = .35f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Share, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("PROVIDER MODE", color = Green, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("ACTIVE", color = Green, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("Your internet is being shared through LINKO.", color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.StopCircle, contentDescription = "Stop sharing", tint = Red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("STOP SHARING", color = Red, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onStop() })
        }
    }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("LINKO IS WORKING", color = color, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { index, (step, _) ->
                val currentIndex = steps.indexOfFirst { it.first == phase }
                val passed = currentIndex >= index && currentIndex >= 0
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)).background(if (passed) color else TextMuted.copy(alpha = .10f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(detail.ifBlank { "Finding the best direct path…" }, color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun RecoveringCard() {
    LinkoCard(Modifier.fillMaxWidth().border(1.dp, Blue.copy(alpha = .22f), RoundedCornerShape(18.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Autorenew, contentDescription = "Recovering connection", tint = Blue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("AUTOMATIC RECOVERY", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("LINKO is restoring the connection. No new setup is required.", color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun FailureCard(detail: String?, onRetry: () -> Unit) {
    LinkoCard(Modifier.fillMaxWidth().border(1.dp, Red.copy(alpha = .25f), RoundedCornerShape(18.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LinkOff, contentDescription = "Connection failed", tint = Red, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("CONNECTION FAILED", color = Red, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Text(detail?.replace('_', ' ')?.ifBlank { "LINKO could not complete the connection." } ?: "LINKO could not complete the connection.", color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("TRY AGAIN", onRetry, color = Blue)
    }
}

@Composable
private fun SecurityStrip(mode: LinkoDashboardMode) {
    val (color, text) = when (mode) {
        LinkoDashboardMode.Connected -> Green to "Session authenticated • secure connection active"
        LinkoDashboardMode.Sharing -> Green to "Provider mode active • trusted session requests"
        LinkoDashboardMode.Failed -> Red to "Connection ended • no active tunnel"
        LinkoDashboardMode.Recovering -> Blue to "LINKO is restoring the connection"
        else -> Blue to "Trusted devices • authenticated sessions • protected transport"
    }
    LinkoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Security, contentDescription = "Security status", tint = color, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("LINKO SECURE", color = color, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(text, color = TextSub, fontSize = 9.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, title: String, value: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).border(1.dp, TextMuted.copy(alpha = .11f), RoundedCornerShape(14.dp)).clickable { onClick() }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextSub, fontSize = 7.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Text(value, color = color, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Card).clickable { onClick() }.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = TextSub, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSub, fontSize = 7.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EasyModeRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Card).border(1.dp, if (enabled) Green.copy(alpha = .25f) else TextMuted.copy(alpha = .10f), RoundedCornerShape(15.dp)).clickable { onToggle() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Speed, contentDescription = "Easy mode", tint = if (enabled) Green else Blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("EASY MODE", color = if (enabled) Green else Blue, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Text(if (enabled) "Larger controls • simpler language" else "Use a simpler LINKO dashboard", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
        Text(if (enabled) "ON" else "OFF", color = if (enabled) Green else TextMuted, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TinyAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp).clip(CircleShape).background(Blue.copy(alpha = .08f))) { Icon(icon, contentDescription = description, tint = Blue, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun ActionSheetRow(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = .08f)).border(1.dp, color.copy(alpha = .16f), RoundedCornerShape(15.dp)).clickable { onClick() }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun PowerSheet(
    mode: LinkoDashboardMode,
    selectedFriend: FriendSearchResult?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onShare: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("LINKO CONTROL", color = TextPrimary, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text("Choose one real action. LINKO handles the networking after that.", color = TextSub, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(16.dp))
        when (mode) {
            LinkoDashboardMode.Ready, LinkoDashboardMode.Failed -> {
                ActionSheetRow(Icons.Filled.Link, if (selectedFriend == null) "Choose a friend" else "Connect to ${selectedFriend.displayName}", if (selectedFriend == null) "Pick a trusted friend first" else "Use their internet on this phone", Blue, onConnect)
                Spacer(Modifier.height(10.dp))
                ActionSheetRow(Icons.Filled.Share, "Send Internet", "Share this phone's internet with a trusted friend", Green, onShare)
            }
            LinkoDashboardMode.Connected, LinkoDashboardMode.Connecting, LinkoDashboardMode.Recovering -> ActionSheetRow(Icons.Filled.LinkOff, "Disconnect", "Stop the current connection", Red, onStop)
            LinkoDashboardMode.Sharing -> ActionSheetRow(Icons.Filled.StopCircle, "Stop sharing", "Turn off provider mode", Red, onStop)
        }
        if (busy) {
            Spacer(Modifier.height(10.dp))
            Text("LINKO is processing the action…", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.height(18.dp))
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

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
