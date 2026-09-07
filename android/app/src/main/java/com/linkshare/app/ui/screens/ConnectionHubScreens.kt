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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun hubLabel(phase: LinkoConnectionPhase, provider: Boolean): String = when (phase) {
    LinkoConnectionPhase.Idle -> if (provider) "READY TO SHARE" else "READY TO CONNECT"
    LinkoConnectionPhase.Connecting -> "STARTING"
    LinkoConnectionPhase.Authenticating -> "VERIFYING"
    LinkoConnectionPhase.Signaling -> "SIGNALING"
    LinkoConnectionPhase.Establishing -> "ESTABLISHING"
    LinkoConnectionPhase.Securing -> "SECURING"
    LinkoConnectionPhase.Routing -> "ROUTING"
    LinkoConnectionPhase.Connected -> if (provider) "SHARING LIVE" else "CONNECTED"
    LinkoConnectionPhase.Failed -> "FAILED"
}

private fun hubColor(phase: LinkoConnectionPhase): Color = when (phase) {
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
private fun HubHeader(title: String, subtitle: String, status: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(status, color = color, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 7.dp))
    }
}

@Composable
private fun HubInfo(title: String, value: String, detail: String, color: Color) {
    LinkoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(value, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(detail, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, lineHeight = 15.sp)
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
    var loading by remember { mutableStateOf(true) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            val json = withContext(Dispatchers.IO) { api.friends() }
            val array = json.optJSONArray("friends") ?: org.json.JSONArray()
            friends = buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(FriendSearchResult(o.optString("user_id"), o.optString("linko_id"), o.optString("display_name").ifBlank { "LINKO Friend" }, null, null, o.optBoolean("is_sharing", false), "friend", null, o.optString("username").trim().removePrefix("@").takeIf { it.isNotBlank() }))
                }
            }
            error = null
        }.onFailure { error = it.message ?: "Unable to load friends" }
        loading = false
    }

    val active = engine.phase != LinkoConnectionPhase.Idle
    val connected = engine.phase == LinkoConnectionPhase.Connected
    val color = hubColor(engine.phase)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        HubHeader("CONNECT", "One page for choosing a provider and watching the connection recover itself.", hubLabel(engine.phase, false), color)
        Spacer(Modifier.height(16.dp))
        Ring(color = color, size = 210.dp, pulse = active, label = if (connected) "ONLINE" else if (active) "LINKING" else "CONNECT")
        Spacer(Modifier.height(12.dp))

        when {
            connected -> HubInfo("PROVIDER", engine.peerDisplayName ?: selected?.displayName ?: "LINKO Friend", engine.peerLinkoId?.let { "@${it.removePrefix("@").takeLast(20)}" } ?: "Secure session active", Green)
            active -> HubInfo("LIVE PROCESS", hubLabel(engine.phase, false), engine.detail.ifBlank { "LINKO is negotiating the safest available path." }, color)
            else -> HubInfo("PROVIDER", selected?.displayName ?: "Choose a trusted friend", selected?.linkoId ?: "Only an approved provider can route traffic to you.", if (selected != null) Green else Blue)
        }

        if (!active) {
            Spacer(Modifier.height(14.dp))
            Text("TRUSTED FRIENDS", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(7.dp))
            when {
                loading -> LinkoCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Loading friends…", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) } }
                friends.isEmpty() -> LinkoCard(Modifier.fillMaxWidth()) { Text("NO CONNECTABLE FRIENDS", color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(error ?: "Add and accept a friend first.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) }
                else -> friends.take(6).forEach { friend ->
                    val picked = selected?.userId == friend.userId
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (picked) BlueSoft else Surface).border(1.dp, if (picked) Blue else Blue.copy(alpha = .10f), RoundedCornerShape(14.dp)).clickable { selected = friend }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(if (friend.isSharing) Green.copy(alpha = .14f) else Blue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Text(friend.displayName.take(1).uppercase(), color = if (friend.isSharing) Green else Blue, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(friend.displayName, color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text(friend.username?.let { "@$it" } ?: friend.linkoId, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono) }
                        Text(if (friend.isSharing) "READY" else "OFFLINE", color = if (friend.isSharing) Green else TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        when {
            connected -> { PrimaryButton("OPEN CONNECTED SESSION", onStarted, color = Green); Spacer(Modifier.height(8.dp)); PrimaryButton("DISCONNECT", { LinkoEngineBridge.disconnect() }, color = Red, outline = true) }
            active -> PrimaryButton("CANCEL CONNECTION", { LinkoEngineBridge.disconnect(); starting = false }, color = Red, outline = true, enabled = !starting)
            else -> PrimaryButton("CONNECT TO FRIEND", {
                val friend = selected ?: return@PrimaryButton
                starting = true
                error = null
                LinkoEngineBridge.connectToFriend(friend.userId, friend.displayName, friend.linkoId) { state ->
                    if (state.contains("failed", true) || state.contains("error", true)) { error = state; starting = false }
                }
                onStarted()
            }, color = Green, enabled = selected != null && !starting, loading = starting)
        }
        if (error != null && !active) { Spacer(Modifier.height(7.dp)); Text(error!!, color = Red, fontSize = 10.sp, fontFamily = JetBrainsMono, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Lock, null, tint = TextMuted, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)); Text("The receiver never approves access on behalf of the provider.", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono) }
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
    var pending by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var dataCap by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        LinkoProviderService.start(context)
        runCatching {
            val profile = withContext(Dispatchers.IO) { com.linkshare.app.network.LinkoProfileApi(auth::currentAccessToken, auth::currentUserId).load() }
            username = profile.username ?: profile.displayName
            linkoId = profile.linkoId
            auth.saveProfile(profile.displayName, profile.linkoId, profile.username)
        }
        launch {
            LinkoRealtimeManager.events.collect { event ->
                when (event) {
                    is LinkoRealtimeEvent.IncomingConnectionRequest -> pending = true
                    is LinkoRealtimeEvent.SessionStateChanged -> pending = event.state == "requested"
                    else -> Unit
                }
            }
        }
        while (true) {
            runCatching { pending = pending || LinkoEngineBridge.getPendingProviderRequests().isNotEmpty() }
            delay(2500)
        }
    }

    LaunchedEffect(engine.phase) {
        if (engine.phase == LinkoConnectionPhase.Connected || engine.phase == LinkoConnectionPhase.Failed) approving = false
        if (engine.phase == LinkoConnectionPhase.Connected) pending = false
    }

    fun copyId() {
        val clean = linkoId.removePrefix("@")
        if (clean.isBlank()) return
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("LINKO ID", clean))
        copied = true
        Toast.makeText(context, "LINKO ID copied", Toast.LENGTH_SHORT).show()
    }

    val color = hubColor(engine.phase)
    val live = engine.phase == LinkoConnectionPhase.Connected
    val active = engine.phase != LinkoConnectionPhase.Idle && !live

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        HubHeader("SHARE", "Your phone provides the route. You decide exactly when another trusted device may use it.", hubLabel(engine.phase, true), color)
        Spacer(Modifier.height(16.dp))
        Ring(color = color, size = 210.dp, pulse = live || active || pending, label = when { live -> "SHARING"; pending -> "REQUEST"; active -> "LINKING"; else -> "READY" })
        Spacer(Modifier.height(12.dp))

        LinkoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("YOUR LINKO ID", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text(if (linkoId.isBlank()) "Loading…" else linkoId, color = Green, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text("@${username.removePrefix("@").ifBlank { "LINKO User" }}", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono) }
                IconButton(enabled = linkoId.isNotBlank(), onClick = { copyId() }) { Icon(Icons.Filled.ContentCopy, "Copy LINKO ID", tint = if (linkoId.isBlank()) TextMuted else Green) }
            }
            if (copied) Text("ID COPIED", color = Green, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        HubInfo("ACCESS CONTROL", if (live) "SHARING AUTHORIZED" else "APPROVAL REQUIRED", if (live) "This device is actively providing internet." else "No receiver gets access until you approve the request.", if (live) Green else Blue)

        Spacer(Modifier.height(10.dp))
        Text("SESSION DATA CAP", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0L to "UNLIMITED", 500L to "500 MB", 1024L to "1 GB", 2048L to "2 GB").forEach { (mb, label) ->
                val picked = dataCap == mb
                Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (picked) Green.copy(alpha = .12f) else Surface).border(1.dp, if (picked) Green else Green.copy(alpha = .10f), RoundedCornerShape(9.dp)).clickable(enabled = !live) { dataCap = mb; LinkoProviderService.start(context, mb) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) { Text(label, color = if (picked) Green else TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal) }
            }
        }

        if (pending && !live) {
            Spacer(Modifier.height(14.dp))
            LinkoCard(Modifier.fillMaxWidth().border(1.5.dp, Yellow, RoundedCornerShape(16.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Yellow.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Text("!", color = Yellow, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("INCOMING REQUEST", color = Yellow, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text(engine.peerDisplayName ?: "A trusted friend wants your connection", color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Approve only when you recognize the receiver. The session can be stopped at any time.", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, lineHeight = 15.sp)
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("APPROVE & SHARE", {
                        if (!approving) { approving = true; LinkoEngineBridge.approvePendingProviderRequest { state -> if (state == "approved" || state == "starting") pending = false; if (state.contains("failed", true) || state.contains("error", true)) approving = false } }
                    }, color = Green, enabled = !approving, loading = approving, modifier = Modifier.weight(1f))
                    PrimaryButton("DECLINE", {
                        if (!approving) { approving = true; LinkoEngineBridge.denyPendingProviderRequest { pending = false; approving = false } }
                    }, color = Red, outline = true, enabled = !approving, modifier = Modifier.weight(1f))
                }
            }
        }

        if (live) {
            Spacer(Modifier.height(14.dp))
            LinkoCard(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(GreenSoft, Surface)))) {
                Text("LIVE SHARING", color = Green, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(engine.peerDisplayName ?: "LINKO Friend", color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(engine.peerLinkoId?.let { "@${it.removePrefix("@").takeLast(20)}" } ?: "Trusted receiver", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Text("TRAFFIC", color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono); Text("${engine.bytesOut / 1024} KB", color = Green, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) { Text("LATENCY", color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono); Text(if (engine.latencyMs > 0) "${engine.latencyMs} ms" else "MEASURING", color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(11.dp))
                PrimaryButton("VIEW LIVE USAGE", onActive, outline = true, color = Green)
            }
        }

        Spacer(Modifier.height(14.dp))
        if (live) PrimaryButton("STOP SHARING", { LinkoEngineBridge.disconnect(); pending = false }, color = Red)
        else if (active) PrimaryButton("CANCEL", { LinkoEngineBridge.disconnect(); approving = false }, color = Red, outline = true)
        else if (!pending) Text("WAITING FOR A TRUSTED FRIEND", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Security, null, tint = TextMuted, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)); Text("You control approval, data limits and termination.", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono) }
        Spacer(Modifier.height(24.dp))
    }
}
