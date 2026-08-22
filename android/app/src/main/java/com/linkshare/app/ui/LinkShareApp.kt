package com.linkshare.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.UsageStats
import com.linkshare.app.tunnel.TunnelCoordinator
import com.linkshare.app.viewmodel.LinkShareViewModel

private val Ink = Color(0xFF101417)
private val Parchment = Color(0xFFF7F2E8)
private val SeaGlass = Color(0xFF66BFB5)
private val Copper = Color(0xFFE2A15F)
private val Plum = Color(0xFF55415F)
private val Alert = Color(0xFFB85042)

@Composable
fun LinkShareApp(
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tunnelCoordinator = remember(context) { TunnelCoordinator(context) }

    LinkShareTheme {
        LinkShareScreen(
            state = state,
            onModeSelected = viewModel::setMode,
            onToggleSharing = viewModel::toggleHostSharing,
            onApproveRequest = viewModel::approveIncomingRequest,
            onDenyRequest = viewModel::denyIncomingRequest,
            onConnect = { friend ->
                if (!state.hasVpnPermission) {
                    onRequestVpnPermission()
                } else {
                    viewModel.connectToFriend(friend)
                    val ready = state.sessionId.isNotBlank() &&
                        state.peerId.isNotBlank() &&
                        state.sessionKey.size == 32 &&
                        state.relayEndpoint.startsWith("wss://") &&
                        state.relayToken.isNotBlank()
                    if (ready) {
                        tunnelCoordinator.startVpnTunnel(
                            sessionId = state.sessionId,
                            peerId = state.peerId.ifBlank { friend.id },
                            sessionKey = state.sessionKey,
                            relayEndpoint = state.relayEndpoint,
                            relayToken = state.relayToken
                        )
                    } else {
                        viewModel.reportTunnelUnavailable()
                    }
                }
            },
            onDisconnect = {
                viewModel.disconnect()
                tunnelCoordinator.stopVpnTunnel()
            },
            onRequestVpnPermission = onRequestVpnPermission
        )
    }
}

@Composable
private fun LinkShareTheme(content: @Composable () -> Unit) {
    Surface(color = Parchment, contentColor = Ink, modifier = Modifier.fillMaxSize()) {
        content()
    }
}

@Composable
private fun LinkShareScreen(
    state: ConnectionUiState,
    onModeSelected: (AppMode) -> Unit,
    onToggleSharing: () -> Unit,
    onApproveRequest: () -> Unit,
    onDenyRequest: () -> Unit,
    onConnect: (Friend) -> Unit,
    onDisconnect: () -> Unit,
    onRequestVpnPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Parchment, Color(0xFFEFF6F1), Color(0xFFDEE8E5))))
    ) {
        ConnectionThreadBackdrop(state.connectionPhase, state.hostSharingEnabled)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Spacer(Modifier.height(22.dp)) }
            item { Header(state) }
            item { ModeSwitch(state.mode, onModeSelected) }
            item { PermissionNotice(state.hasVpnPermission, onRequestVpnPermission) }
            item {
                if (state.mode == AppMode.Host) HostPanel(state, onToggleSharing, onApproveRequest, onDenyRequest)
                else ClientPanel(state, onConnect, onDisconnect)
            }
            item { StatusMessage(state.eventMessage) }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun Header(state: ConnectionUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LinkShare", fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif, color = Ink)
        Text(
            if (state.mode == AppMode.Host) "Share internet with someone you trust." else "Connect through a friend's phone, wherever they are.",
            fontSize = 16.sp, lineHeight = 22.sp, color = Ink.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun ModeSwitch(mode: AppMode, onModeSelected: (AppMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModePill("Host", "Share my data", mode == AppMode.Host, Modifier.weight(1f)) { onModeSelected(AppMode.Host) }
        ModePill("Client", "Use a friend's data", mode == AppMode.Client, Modifier.weight(1f)) { onModeSelected(AppMode.Client) }
    }
}

@Composable
private fun ModePill(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(if (selected) Parchment else Color.Transparent).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
        ).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, color = if (selected) Ink else Parchment, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = if (selected) Ink.copy(alpha = 0.62f) else Parchment.copy(alpha = 0.68f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PermissionNotice(hasPermission: Boolean, onRequestVpnPermission: () -> Unit) {
    val text = if (hasPermission) "VPN permission is ready for encrypted tunnels." else "Before connecting, Android will ask permission to create a private VPN tunnel."
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (hasPermission) SeaGlass.copy(alpha = 0.18f) else Copper.copy(alpha = 0.18f)).border(1.dp, if (hasPermission) SeaGlass.copy(alpha = 0.5f) else Copper.copy(alpha = 0.52f), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PulseDot(active = hasPermission, color = if (hasPermission) SeaGlass else Copper)
        Text(text, modifier = Modifier.weight(1f), color = Ink.copy(alpha = 0.76f), fontSize = 13.sp, lineHeight = 18.sp)
        if (!hasPermission) TextButton(onClick = onRequestVpnPermission) { Text("Review", color = Ink, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun HostPanel(state: ConnectionUiState, onToggleSharing: () -> Unit, onApproveRequest: () -> Unit, onDenyRequest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ShareToggle(state.hostSharingEnabled, onToggleSharing)
        AnimatedVisibility(visible = state.incomingRequest != null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            state.incomingRequest?.let { request -> ConsentMoment(request.initials, request.friendName, "${request.deviceName} - ${request.distanceLabel}", onApproveRequest, onDenyRequest) }
        }
        LiveStats(if (state.hostSharingEnabled) "Sharing live" else "Ready to share", state.usageStats, "${state.usageStats.connectedClients} connected")
    }
}

@Composable
private fun ShareToggle(enabled: Boolean, onToggleSharing: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "share-pulse")
    val glow by transition.animateFloat(0.18f, 0.44f, infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "share-glow")
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(if (enabled) Ink else Color.White.copy(alpha = 0.72f)).border(1.dp, if (enabled) SeaGlass.copy(alpha = glow) else Ink.copy(alpha = 0.12f), RoundedCornerShape(30.dp)).clickable(onClick = onToggleSharing).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Share My Data", fontSize = 25.sp, fontWeight = FontWeight.Black, color = if (enabled) Parchment else Ink)
                Text(if (enabled) "Friends can request a protected path through this phone." else "Tap once to become available.", fontSize = 14.sp, lineHeight = 20.sp, color = if (enabled) Parchment.copy(alpha = 0.7f) else Ink.copy(alpha = 0.62f))
            }
            ToggleOrb(enabled)
        }
        ThreadMeter(enabled)
    }
}

@Composable
private fun ConsentMoment(initials: String, name: String, detail: String, onApprove: () -> Unit, onDeny: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.78f)).border(1.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(26.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(initials, Copper)
            Column(modifier = Modifier.weight(1f)) {
                Text("$name wants to use your data", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(detail, color = Ink.copy(alpha = 0.64f), fontSize = 13.sp)
            }
        }
        Text("Approve only if this is your friend. You can stop sharing at any time.", color = Ink.copy(alpha = 0.72f), fontSize = 14.sp, lineHeight = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Deny", Ink.copy(alpha = 0.08f), Ink, Modifier.weight(1f), onDeny)
            ActionButton("Approve", SeaGlass, Ink, Modifier.weight(1f), onApprove)
        }
    }
}

@Composable
private fun ClientPanel(state: ConnectionUiState, onConnect: (Friend) -> Unit, onDisconnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ConnectionStatePanel(state, onDisconnect)
        Text("Friends sharing access", fontSize = 18.sp, fontWeight = FontWeight.Black)
        state.friends.forEach { friend -> FriendRow(friend, state.activeFriend?.id == friend.id, state.connectionPhase == ConnectionPhase.Connected && state.activeFriend?.id == friend.id, { onConnect(friend) }, onDisconnect) }
    }
}

@Composable
private fun ConnectionStatePanel(state: ConnectionUiState, onDisconnect: () -> Unit) {
    val phase = state.connectionPhase
    val (title, detail, tint) = when (phase) {
        ConnectionPhase.Idle -> Triple("Choose a friend", "No traffic is being routed.", Ink.copy(alpha = 0.55f))
        ConnectionPhase.Requesting -> Triple("Request sent", "Waiting for your friend to say yes.", Copper)
        ConnectionPhase.Handshaking -> Triple("Securing the link", "Exchanging short-lived tunnel keys.", SeaGlass)
        ConnectionPhase.Retrying -> Triple("Retrying on weak connection", "Signal is thin, but LinkShare is trying again.", Copper)
        ConnectionPhase.Connected -> Triple("Connected", "Internet is moving through ${state.activeFriend?.name ?: "your friend"}.", SeaGlass)
        ConnectionPhase.Failed -> Triple("Could not connect", "Try again when your friend is available or signal improves.", Alert)
    }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StateGlyph(phase, tint)
            Column(modifier = Modifier.weight(1f)) { Text(title, color = Parchment, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(detail, color = Parchment.copy(alpha = 0.68f), fontSize = 13.sp, lineHeight = 18.sp) }
        }
        if (phase == ConnectionPhase.Connected || phase == ConnectionPhase.Retrying || phase == ConnectionPhase.Handshaking) LiveStats("Tunnel counters", state.usageStats, state.activeFriend?.name ?: "Friend", true)
        if (phase == ConnectionPhase.Connected) ActionButton("Disconnect", Alert, Color.White, Modifier.fillMaxWidth(), onDisconnect)
    }
}

@Composable
private fun FriendRow(friend: Friend, active: Boolean, connected: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.78f)).border(1.dp, if (active) SeaGlass.copy(alpha = 0.7f) else Ink.copy(alpha = 0.1f), RoundedCornerShape(22.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(friend.initials, Copper)
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text("${friend.deviceName} · ${friend.distanceLabel}", color = Ink.copy(alpha = 0.62f), fontSize = 12.sp)
            Text(friend.trustNote, color = Ink.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        ActionButton(if (connected) "Disconnect" else if (active) "Retry" else "Connect", if (connected) Alert else if (active) Copper else SeaGlass, if (connected) Color.White else Ink, Modifier.width(112.dp), if (connected) onDisconnect else onConnect)
    }
}

@Composable
private fun LiveStats(title: String, usageStats: UsageStats, connectedLabel: String, dark: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (dark) Color.White.copy(alpha = 0.06f) else Ink.copy(alpha = 0.05f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = if (dark) Parchment else Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("${usageStats.sessionSeconds}s · $connectedLabel", color = if (dark) Parchment.copy(alpha = 0.65f) else Ink.copy(alpha = 0.65f), fontSize = 12.sp)
    }
}

@Composable private fun StatusMessage(message: String?) { message?.let { Text(it, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Ink.copy(alpha = 0.62f), fontSize = 12.sp) } }
@Composable private fun Avatar(initials: String, color: Color) { Box(Modifier.size(46.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(initials, color = Ink, fontWeight = FontWeight.Black) } }
@Composable private fun PulseDot(active: Boolean, color: Color) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) color else Ink.copy(alpha = 0.25f))) }
@Composable private fun ToggleOrb(enabled: Boolean) { Box(Modifier.size(52.dp).clip(CircleShape).background(if (enabled) SeaGlass else Ink.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Text(if (enabled) "ON" else "OFF", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (enabled) Ink else Ink.copy(alpha = 0.55f)) } }
@Composable private fun ThreadMeter(active: Boolean) { Canvas(Modifier.fillMaxWidth().height(12.dp)) { drawLine(if (active) SeaGlass else Ink.copy(alpha = 0.18f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3f, cap = StrokeCap.Round) } }
@Composable private fun StateGlyph(phase: ConnectionPhase, tint: Color) { Box(Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text(phase.name.take(1), color = tint, fontWeight = FontWeight.Black) } }
@Composable private fun ActionButton(label: String, background: Color, contentColor: Color, modifier: Modifier, onClick: () -> Unit) { androidx.compose.material3.Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = contentColor)) { Text(label, fontWeight = FontWeight.Bold) } }
@Composable private fun ConnectionThreadBackdrop(phase: ConnectionPhase, sharing: Boolean) { Canvas(Modifier.fillMaxSize()) { val alpha = if (phase != ConnectionPhase.Idle || sharing) 0.18f else 0.08f; drawLine(Brush.verticalGradient(listOf(SeaGlass.copy(alpha = alpha), Copper.copy(alpha = alpha))), Offset(size.width * .82f, 0f), Offset(size.width * .18f, size.height), strokeWidth = 3f) } }
