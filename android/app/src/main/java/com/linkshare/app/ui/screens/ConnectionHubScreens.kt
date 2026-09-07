package com.linkshare.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.FriendSearchResult
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.BlueSoft
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.GreenSoft
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.Surface
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun hubPhaseLabel(phase: LinkoConnectionPhase, provider: Boolean): String = when (phase) {
    LinkoConnectionPhase.Idle -> if (provider) "READY TO SHARE" else "READY TO CONNECT"
    LinkoConnectionPhase.Connecting -> "STARTING"
    LinkoConnectionPhase.Authenticating -> "VERIFYING"
    LinkoConnectionPhase.Signaling -> "SIGNALING"
    LinkoConnectionPhase.Establishing -> "ESTABLISHING PATH"
    LinkoConnectionPhase.Securing -> "SECURING TUNNEL"
    LinkoConnectionPhase.Routing -> "ROUTING TRAFFIC"
    LinkoConnectionPhase.Connected -> if (provider) "SHARING LIVE" else "CONNECTED"
    LinkoConnectionPhase.Failed -> "CONNECTION FAILED"
}

private fun hubPhaseColor(phase: LinkoConnectionPhase): androidx.compose.ui.graphics.Color = when (phase) {
    LinkoConnectionPhase.Idle -> Blue
    LinkoConnectionPhase.Connecting,
    LinkoConnectionPhase.Authenticating,
    LinkoConnectionPhase.Signaling,
    LinkoConnectionPhase.Establishing,
    LinkoConnectionPhase.Securing,
    LinkoConnectionPhase.Routing -> Yellow
    LinkoConnectionPhase.Connected -> Green
    LinkoConnectionPhase.Failed -> Red
}

@Composable
private fun HubHeader(title: String, subtitle: String, phase: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .30f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(phase, color = color, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SignalCard(title: String, value: String, detail: String, color: androidx.compose.ui.graphics.Color) {
    LinkoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(value, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(detail, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun RedesignedReceiverScreen(onStarted: () -> Unit) {
    val api = LinkoFriendsApiHolder.api
    val engine by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    var friends by remember { mutableStateOf<List<FriendSearchResult>>(emptyList()) }
    var selected by remember { mutableStateOf<FriendSearchResult?>(null) }
    var loadingFriends by remember { mutableStateOf(true) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadingFriends = true
        runCatching {
            val json = withContext(Dispatchers.IO) { api.friends() }
            val a = json.optJSONArray("friends") ?: org.json.JSONArray()
            friends = buildList {
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    add(
                        FriendSearchResult(
                            userId = o.optString("user_id"),
                            linkoId = o.optString("linko_id"),
                            displayName = o.optString("display_name").ifBlank { "LINKO Friend" },
                            deviceId = null,
                            deviceName = null,
                            isSharing = o.optBoolean("is_sharing", false),
                            relationshipStatus = "friend",
                            requestId = null,
                            username = o.optString("username").trim().removePrefix("@").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.onFailure { error = it.message ?: "Unable to load friends" }
        loadingFriends = false
    }

    val phaseColor = hubPhaseColor(engine.phase)
    val active = engine.phase != LinkoConnectionPhase.Idle
    val connected = engine.phase == LinkoConnectionPhase.Connected
    val selectedFriend = selected

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        HubHeader(
            "CONNECT",
            "Choose one trusted friend. LINKO handles the negotiation, path selection and tunnel state for you.",
            hubPhaseLabel(engine.phase, provider = false),
            phaseColor
        )
        Spacer(Modifier.height(18.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Ring(
                color = phaseColor,
                size = 210.dp,
                pulse = active || connected,
                label = when {
                    connected -> "ONLINE"
                    active -> "LINKING"
                    else -> "CONNECT"
                }
            )
        }
        Spacer(Modifier.height(10.dp))

        if (connected) {
            SignalCard(
                "CONNECTED TO",
                engine.peerDisplayName ?: selectedFriend?.displayName ?: "LINKO Friend",
                engine.peerLinkoId?.let { "@${it.removePrefix("@").takeLast(20)}" } ?: "Secure peer session active",
                Green
            )
        } else if (active) {
            SignalCard(
                "CONNECTION PROCESS",
                hubPhaseLabel(engine.phase, false),
                engine.detail.ifBlank { "LINKO is moving through the safest available path." },
                phaseColor
            )
        } else {
            LinkoCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Blue.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("YOUR PROVIDER", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(selectedFriend?.displayName ?: "Select a friend below", color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(selectedFriend?.linkoId ?: "Only friends who are ready to share can be connected", color = if (selectedFriend != null) Green else TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!active) {
            Text("TRUSTED FRIENDS", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(7.dp))
            when {
                loadingFriends -> LinkoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading friends…", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    }
                }
                friends.isEmpty() -> LinkoCard(Modifier.fillMaxWidth()) {
                    Text("NO CONNECTABLE FRIENDS", color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(error ?: "Add and accept a friend first, then return here to connect.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono, lineHeight = 16.sp)
                }
                else -> {
                    friends.take(6).forEach { friend ->
                        val selectedThis = selected?.userId == friend.userId
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selectedThis) BlueSoft else Surface).border(1.dp, if (selectedThis) Blue else Blue.copy(alpha = .10f), RoundedCornerShape(14.dp)).clickable { selected = friend }.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(if (friend.isSharing) Green.copy(alpha = .15f) else Blue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                                Text(friend.displayName.take(1).uppercase(), color = if (friend.isSharing) Green else Blue, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(friend.displayName, color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                                Text(friend.username?.let { "@$it" } ?: friend.linkoId, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (friend.isSharing) "READY" else "OFFLINE", color = if (friend.isSharing) Green else TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                                Text(if (selectedThis) "SELECTED" else "SELECT", color = if (selectedThis) Blue else TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono)
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }

        if (error != null && active) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Red, fontSize = 10.sp, fontFamily = JetBrainsMono, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(14.dp))
        when {
            connected -> {
                PrimaryButton("OPEN CONNECTED SESSION", onStarted, color = Green)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("DISCONNECT", { LinkoEngineBridge.disconnect() }, color = Red, outline = true)
            }
            active -> {
                PrimaryButton("CANCEL CONNECTION", { LinkoEngineBridge.disconnect(); starting = false }, color = Red, outline = true, enabled = !starting)
            }
            else -> {
                PrimaryButton(
                    label = if (starting) "STARTING LINKO…" else "CONNECT TO FRIEND",
                    onClick = {
                        val friend = selected ?: return@PrimaryButton
                        starting = true
                        error = null
                        LinkoEngineBridge.connectToFriend(friend.userId, friend.displayName, friend.linkoId) { state ->
                            if (state.contains("failed", ignoreCase = true) || state.contains("error", ignoreCase = true)) {
                                error = state
                                starting = false
                            }
                        }
                        onStarted()
                    },
                    color = Green,
                    enabled = selected != null && !starting,
                    loading = starting
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text("Only a trusted friend's approved connection is used", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RedesignedProviderScreen(onActive: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val engine by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }
    var username by remember { mutableStateOf(auth.currentUsername() ?: auth.currentDisplayName().orEmpty()) }
    var pendingRequest by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf(false) }
    var dataCapMb by remember { mutableStateOf(0L) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LinkoProviderService.start(context)
        runCatching {
            val profile = withContext(Dispatchers.IO) { com.linkshare.app.network.LinkoProfileApi(auth::currentAccessToken, auth::currentUserId).load() }
            username = profile.username ?: profile.displayName
            linkoId = profile.linkoId
            auth.saveProfile(profile.displayName, profile.linkoId, profile.username)
        }
        launch@{
            LinkoRealtimeManager.events.collect { event ->
                when (event) {
                    is LinkoRealtimeEvent.IncomingConnectionRequest -> pendingRequest = true
                    is LinkoRealtimeEvent.SessionStateChanged -> pendingRequest = event.state == "requested"
                    else -> Unit
                }
            }
        }
        while (true) {
            runCatching { pendingRequest = LinkoEngineBridge.getPendingProviderRequests().isNotEmpty() || pendingRequest }
            delay(2500)
        }
    }

    LaunchedEffect(engine.phase) {
        if (engine.phase == LinkoConnectionPhase.Connected) {
            pendingRequest = false
            approving = false
        }
        if (engine.phase == LinkoConnectionPhase.Failed) approving = false
    }

    fun copyId() {
        val clean = linkoId.removePrefix("@")
        if (clean.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("LINKO ID", clean))
        copied = true
        Toast.makeText(context, "LINKO ID copied", Toast.LENGTH_SHORT).show()
    }

    val phaseColor = hubPhaseColor(engine.phase)
    val live = engine.phase == LinkoConnectionPhase.Connected
    val busy = engine.phase != LinkoConnectionPhase.Idle && !live

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        HubHeader(
            "SHARE",
            "Your phone becomes the provider. Nothing starts until you approve a trusted friend.",
            hubPhaseLabel(engine.phase, provider = true),
            phaseColor
        )
        Spacer(Modifier.height(17.dp))
        Ring(color = phaseColor, size = 210.dp, pulse = live || busy || pendingRequest, label = when {
            live -> "SHARING"
            pendingRequest -> "REQUEST"
            busy -> "LINKING"
            else -> "READY"
        })
        Spacer(Modifier.height(12.dp))

        LinkoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR LINKO ID", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Text(if (linkoId.isBlank()) "Loading…" else linkoId, color = Green, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text("@${username.removePrefix("@").ifBlank { "LINKO User" }}", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                }
                androidx.compose.material3.IconButton(enabled = linkoId.isNotBlank(), onClick = { copyId() }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy LINKO ID", tint = if (linkoId.isBlank()) TextMuted else Green)
                }
            }
            if (copied) {
                Spacer(Modifier.height(5.dp))
                Text("ID COPIED", color = Green, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))
        SignalCard("SAFETY", if (live) "SHARING AUTHORIZED" else "APPROVAL REQUIRED", if (live) "You can stop this session at any time." else "A friend must be explicitly approved before traffic is routed.", if (live) Green else Blue)

        Spacer(Modifier.height(10.dp))
        Text("MAX DATA PER SESSION", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0L to "UNLIMITED", 500L to "500 MB", 1024L to "1 GB", 2048L to "2 GB").forEach { (mb, label) ->
                val selected = dataCapMb == mb
                Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (selected) Green.copy(alpha = .12f) else Surface).border(1.dp, if (selected) Green else Green.copy(alpha = .10f), RoundedCornerShape(9.dp)).clickable(enabled = !live) { dataCapMb = mb; LinkoProviderService.start(context, mb) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) Green else TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (pendingRequest && !live) {
            Spacer(Modifier.height(14.dp))
            LinkoCard(Modifier.fillMaxWidth().border(1.5.dp, Yellow, RoundedCornerShape(16.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Yellow.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Text("!", color = Yellow, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("FRIEND REQUESTING INTERNET", color = Yellow, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(engine.peerDisplayName ?: "A trusted friend wants to connect", color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Review the request, then approve only when you recognize the friend.", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, lineHeight = 15.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("APPROVE & SHARE", {
                        if (!approving) {
                            approving = true
                            LinkoEngineBridge.approvePendingProviderRequest { state ->
                                if (state == "approved" || state == "starting") pendingRequest = false
                                if (state.contains("failed", ignoreCase = true) || state.contains("error", ignoreCase = true)) approving = false
                            }
                        }
                    }, color = Green, enabled = !approving, loading = approving, modifier = Modifier.weight(1f))
                    PrimaryButton("DECLINE", {
                        if (!approving) {
                            approving = true
                            LinkoEngineBridge.denyPendingProviderRequest { pendingRequest = false; approving = false }
                        }
                    }, color = Red, outline = true, enabled = !approving, modifier = Modifier.weight(1f))
                }
            }
        }

        if (live) {
            Spacer(Modifier.height(14.dp))
            LinkoCard(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(GreenSoft, Surface)))) {
                Text("LIVE SHARING", color = Green, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(engine.peerDisplayName ?: "LINKO Friend", color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(engine.peerLinkoId?.let { "@${it.removePrefix("@").takeLast(20)}" } ?: "Trusted receiver", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Text("TRAFFIC", color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono); Text("${engine.bytesOut / 1024} KB", color = Green, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) { Text("LATENCY", color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono); Text(if (engine.latencyMs > 0) "${engine.latencyMs} ms" else "MEASURING", color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("VIEW LIVE USAGE", onActive, outline = true, color = Green)
            }
        }

        if (busy) {
            Spacer(Modifier.height(12.dp))
            Text(engine.detail.ifBlank { "LINKO is establishing the connection…" }, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(13.dp))
        if (live) {
            PrimaryButton("STOP SHARING", { LinkoEngineBridge.disconnect(); pendingRequest = false }, color = Red)
        } else if (!pendingRequest && !busy) {
            Text("WAITING FOR A TRUSTED FRIEND", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        } else if (busy) {
            PrimaryButton("CANCEL", { LinkoEngineBridge.disconnect(); approving = false }, color = Red, outline = true)
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text("You control who connects and when sharing ends", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.height(24.dp))
    }
}
