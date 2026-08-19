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
                    tunnelCoordinator.startVpnTunnel()
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(Parchment, Color(0xFFEFF6F1), Color(0xFFDEE8E5))
                )
            )
    ) {
        ConnectionThreadBackdrop(state.connectionPhase, state.hostSharingEnabled)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Spacer(Modifier.height(22.dp)) }
            item { Header(state) }
            item { ModeSwitch(state.mode, onModeSelected) }
            item { PermissionNotice(state.hasVpnPermission, onRequestVpnPermission) }
            item {
                if (state.mode == AppMode.Host) {
                    HostPanel(state, onToggleSharing, onApproveRequest, onDenyRequest)
                } else {
                    ClientPanel(state, onConnect, onDisconnect)
                }
            }
            item { StatusMessage(state.eventMessage) }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun Header(state: ConnectionUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "LinkShare",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            color = Ink
        )
        Text(
            text = if (state.mode == AppMode.Host) {
                "Share internet with someone you trust."
            } else {
                "Connect through a friend's phone, wherever they are."
            },
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Ink.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun ModeSwitch(mode: AppMode, onModeSelected: (AppMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ink)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModePill("Host", "Share my data", mode == AppMode.Host, Modifier.weight(1f)) {
            onModeSelected(AppMode.Host)
        }
        ModePill("Client", "Use a friend's data", mode == AppMode.Client, Modifier.weight(1f)) {
            onModeSelected(AppMode.Client)
        }
    }
}

@Composable
private fun ModePill(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Parchment else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Ink else Parchment,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Text(
            text = subtitle,
            color = if (selected) Ink.copy(alpha = 0.62f) else Parchment.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PermissionNotice(hasPermission: Boolean, onRequestVpnPermission: () -> Unit) {
    val text = if (hasPermission) {
        "VPN permission is ready for encrypted tunnels."
    } else {
        "Before connecting, Android will ask permission to create a private VPN tunnel."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (hasPermission) SeaGlass.copy(alpha = 0.18f) else Copper.copy(alpha = 0.18f))
            .border(
                1.dp,
                if (hasPermission) SeaGlass.copy(alpha = 0.5f) else Copper.copy(alpha = 0.52f),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PulseDot(active = hasPermission, color = if (hasPermission) SeaGlass else Copper)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = Ink.copy(alpha = 0.76f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        if (!hasPermission) {
            TextButton(onClick = onRequestVpnPermission) {
                Text("Review", color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HostPanel(
    state: ConnectionUiState,
    onToggleSharing: () -> Unit,
    onApproveRequest: () -> Unit,
    onDenyRequest: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ShareToggle(state.hostSharingEnabled, onToggleSharing)
        AnimatedVisibility(visible = state.incomingRequest != null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            state.incomingRequest?.let { request ->
                ConsentMoment(
                    initials = request.initials,
                    name = request.friendName,
                    detail = "${request.deviceName} - ${request.distanceLabel}",
                    onApprove = onApproveRequest,
                    onDeny = onDenyRequest
                )
            }
        }
        LiveStats(
            title = if (state.hostSharingEnabled) "Sharing live" else "Ready to share",
            usageStats = state.usageStats,
            connectedLabel = "${state.usageStats.connectedClients} connected"
        )
    }
}

@Composable
private fun ShareToggle(enabled: Boolean, onToggleSharing: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "share-pulse")
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.44f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "share-glow"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(if (enabled) Ink else Color(0xFFFFFFFF).copy(alpha = 0.72f))
            .border(1.dp, if (enabled) SeaGlass.copy(alpha = glow) else Ink.copy(alpha = 0.12f), RoundedCornerShape(30.dp))
            .clickable(onClick = onToggleSharing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Share My Data",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    color = if (enabled) Parchment else Ink
                )
                Text(
                    text = if (enabled) "Friends can request a protected path through this phone." else "Tap once to become available.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (enabled) Parchment.copy(alpha = 0.7f) else Ink.copy(alpha = 0.62f)
                )
            }
            ToggleOrb(enabled)
        }
        ThreadMeter(active = enabled)
    }
}

@Composable
private fun ConsentMoment(
    initials: String,
    name: String,
    detail: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(initials, Copper)
            Column(modifier = Modifier.weight(1f)) {
                Text("$name wants to use your data", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(detail, color = Ink.copy(alpha = 0.64f), fontSize = 13.sp)
            }
        }
        Text(
            text = "Approve only if this is your friend. You can stop sharing at any time.",
            color = Ink.copy(alpha = 0.72f),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Deny", Ink.copy(alpha = 0.08f), Ink, Modifier.weight(1f), onDeny)
            ActionButton("Approve", SeaGlass, Ink, Modifier.weight(1f), onApprove)
        }
    }
}

@Composable
private fun ClientPanel(
    state: ConnectionUiState,
    onConnect: (Friend) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ConnectionStatePanel(state, onDisconnect)
        Text("Friends sharing access", fontSize = 18.sp, fontWeight = FontWeight.Black)
        state.friends.forEach { friend ->
            FriendRow(
                friend = friend,
                active = state.activeFriend?.id == friend.id,
                connected = state.connectionPhase == ConnectionPhase.Connected && state.activeFriend?.id == friend.id,
                onConnect = { onConnect(friend) },
                onDisconnect = onDisconnect
            )
        }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ink)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StateGlyph(phase, tint)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Parchment, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(detail, color = Parchment.copy(alpha = 0.68f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        if (phase == ConnectionPhase.Connected || phase == ConnectionPhase.Retrying || phase == ConnectionPhase.Handshaking) {
            LiveStats(
                title = "Tunnel counters",
                usageStats = state.usageStats,
                connectedLabel = state.activeFriend?.name ?: "Friend",
                dark = true
            )
        }
        if (phase == ConnectionPhase.Connected) {
            ActionButton("Disconnect", Alert, Color.White, Modifier.fillMaxWidth(), onDisconnect)
        }
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    active: Boolean,
    connected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val accent = Color(friend.accentHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) SeaGlass.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.68f))
            .border(1.dp, if (active) SeaGlass.copy(alpha = 0.55f) else Ink.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Avatar(friend.initials, accent)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(friend.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(friend.cityHint, fontSize = 12.sp, color = Ink.copy(alpha = 0.58f), maxLines = 1)
            Text(friend.trustNote, fontSize = 12.sp, color = Ink.copy(alpha = 0.58f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ActionButton(
            text = if (connected) "Stop" else "Connect",
            background = if (friend.isSharing) Ink else Ink.copy(alpha = 0.12f),
            content = if (friend.isSharing) Parchment else Ink.copy(alpha = 0.5f),
            modifier = Modifier.width(96.dp),
            onClick = if (connected) onDisconnect else onConnect
        )
    }
}

@Composable
private fun LiveStats(
    title: String,
    usageStats: UsageStats,
    connectedLabel: String,
    dark: Boolean = false
) {
    val bg = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.64f)
    val fg = if (dark) Parchment else Ink
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("People", connectedLabel, fg, Modifier.weight(1f))
            StatCell("Time", usageStats.sessionSeconds.toClock(), fg, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("Sent", usageStats.bytesSent.toDataLabel(), fg, Modifier.weight(1f))
            StatCell("Received", usageStats.bytesReceived.toDataLabel(), fg, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        Text(label.uppercase(), color = color.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusMessage(message: String?) {
    AnimatedContent(
        targetState = message,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "event-message"
    ) { text ->
        if (text != null) {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Plum.copy(alpha = 0.1f))
                    .padding(14.dp),
                color = Plum,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun Avatar(initials: String, color: Color) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.26f))
            .border(1.dp, color.copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Ink, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ToggleOrb(enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 70.dp, height = 42.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) SeaGlass else Ink.copy(alpha = 0.12f))
            .padding(5.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (enabled) Ink else Color.White)
        )
    }
}

@Composable
private fun PulseDot(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse-dot")
    val alpha by transition.animateFloat(
        initialValue = if (active) 0.35f else 0.18f,
        targetValue = if (active) 1f else 0.48f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot-alpha"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun ThreadMeter(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "thread-meter")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (active) 1_900 else 3_200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "thread-progress"
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val y = size.height / 2f
        drawLine(
            color = if (active) SeaGlass.copy(alpha = 0.28f) else Ink.copy(alpha = 0.16f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        if (active) {
            drawCircle(
                color = Copper,
                radius = 6.dp.toPx(),
                center = Offset(size.width * progress, y)
            )
        }
    }
}

@Composable
private fun StateGlyph(phase: ConnectionPhase, tint: Color) {
    val transition = rememberInfiniteTransition(label = "state-glyph")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "state-pulse"
    )
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            when (phase) {
                ConnectionPhase.Idle -> drawCircle(tint.copy(alpha = 0.7f), radius = size.minDimension / 3f, style = stroke)
                ConnectionPhase.Requesting -> {
                    drawLine(tint, Offset(4.dp.toPx(), size.height / 2), Offset(size.width - 4.dp.toPx(), size.height / 2), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(tint, radius = 4.dp.toPx(), center = Offset(size.width * pulse, size.height / 2))
                }
                ConnectionPhase.Handshaking -> {
                    drawCircle(tint.copy(alpha = pulse), radius = size.minDimension / 2.6f, style = stroke)
                    drawCircle(Copper, radius = 5.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                }
                ConnectionPhase.Connected -> {
                    val path = Path().apply {
                        moveTo(6.dp.toPx(), size.height / 2)
                        lineTo(size.width * 0.42f, size.height - 8.dp.toPx())
                        lineTo(size.width - 6.dp.toPx(), 8.dp.toPx())
                    }
                    drawPath(path, tint, style = stroke)
                }
                ConnectionPhase.Retrying -> {
                    drawArc(tint.copy(alpha = pulse), 30f, 290f, false, style = stroke)
                    drawCircle(Copper, radius = 4.dp.toPx(), center = Offset(size.width - 5.dp.toPx(), size.height / 2))
                }
                ConnectionPhase.Failed -> {
                    drawLine(tint, Offset(8.dp.toPx(), 8.dp.toPx()), Offset(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(tint, Offset(size.width - 8.dp.toPx(), 8.dp.toPx()), Offset(8.dp.toPx(), size.height - 8.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun ConnectionThreadBackdrop(phase: ConnectionPhase, hostSharing: Boolean) {
    val transition = rememberInfiniteTransition(label = "backdrop-thread")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thread-drift"
    )
    val active = hostSharing || phase == ConnectionPhase.Connected || phase == ConnectionPhase.Handshaking || phase == ConnectionPhase.Retrying
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.16f)
            cubicTo(
                size.width * 0.26f,
                size.height * (0.05f + drift * 0.04f),
                size.width * 0.62f,
                size.height * (0.31f - drift * 0.03f),
                size.width * 0.92f,
                size.height * 0.18f
            )
            cubicTo(
                size.width * 0.72f,
                size.height * 0.42f,
                size.width * 0.32f,
                size.height * 0.44f,
                size.width * 0.16f,
                size.height * 0.72f
            )
        }
        drawPath(
            path = path,
            color = (if (active) SeaGlass else Ink).copy(alpha = if (active) 0.16f else 0.06f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(Copper.copy(alpha = 0.22f), radius = 7.dp.toPx(), center = Offset(size.width * 0.08f, size.height * 0.16f))
        drawCircle(SeaGlass.copy(alpha = 0.2f), radius = 9.dp.toPx(), center = Offset(size.width * 0.92f, size.height * 0.18f))
    }
}

private fun Long.toDataLabel(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

private fun Long.toClock(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "%d:%02d".format(minutes, seconds)
}
