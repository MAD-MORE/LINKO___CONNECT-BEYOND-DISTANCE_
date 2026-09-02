package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.FriendSearchResult
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.network.LinkoStateMachine
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LiveFriendsScreen(onFindFriends: () -> Unit, onFriendTap: () -> Unit) {
    val api = LinkoFriendsApiHolder.api
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendSearchResult>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var history by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var presence by remember { mutableStateOf<Map<String, LinkoStateMachine.Availability>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun applyPresenceSnapshot() {
        presence = LinkoRealtimeManager.currentPresenceSnapshot().mapValues { (_, value) ->
            LinkoStateMachine.availabilityFromPresence(value.state, value.online)
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            runCatching {
                val f = api.friends().optJSONArray("friends") ?: JSONArray()
                friends = buildList {
                    for (i in 0 until f.length()) {
                        val o = f.optJSONObject(i) ?: continue
                        add(
                            FriendSearchResult(
                                userId = o.optString("user_id"),
                                linkoId = o.optString("linko_id"),
                                displayName = o.optString("display_name", "LINKO User"),
                                deviceId = o.optString("device_id").takeIf { it.isNotBlank() },
                                deviceName = o.optString("device_name").takeIf { it.isNotBlank() },
                                isSharing = o.optBoolean("is_sharing", false),
                                relationshipStatus = "friend",
                            ),
                        )
                    }
                }
                applyPresenceSnapshot()
                val r = api.requests().optJSONArray("requests") ?: JSONArray()
                incoming = buildList { for (i in 0 until r.length()) r.optJSONObject(i)?.let { if (it.optBoolean("incoming") && it.optString("status") == "pending") add(it) } }
                outgoing = buildList { for (i in 0 until r.length()) r.optJSONObject(i)?.let { if (!it.optBoolean("incoming") && it.optString("status") == "pending") add(it) } }
                history = buildList { for (i in 0 until r.length()) r.optJSONObject(i)?.let { if (!it.optBoolean("incoming") && it.optString("status") in setOf("accepted", "declined")) add(it) } }
                message = null
            }.onFailure { message = it.message ?: "Unable to load friends" }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        applyPresenceSnapshot()
        reload()
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.IncomingConnectionRequest,
                is LinkoRealtimeEvent.FriendRequestReceived,
                is LinkoRealtimeEvent.FriendRequestSent,
                is LinkoRealtimeEvent.FriendRequestAccepted,
                is LinkoRealtimeEvent.FriendRequestDeclined,
                is LinkoRealtimeEvent.FriendRemoved,
                is LinkoRealtimeEvent.SessionStateChanged -> reload()
                is LinkoRealtimeEvent.PresenceChanged -> {
                    val userPresence = event.presence
                    presence = presence + (userPresence.userId to LinkoStateMachine.availabilityFromPresence(userPresence.state, userPresence.online))
                }
                is LinkoRealtimeEvent.TransportError -> message = "Realtime connection unavailable"
                else -> Unit
            }
        }
    }

    fun statusFor(friend: FriendSearchResult): Pair<String, Color> {
        val state = presence[friend.userId]
        return when (state) {
            LinkoStateMachine.Availability.SHARING -> "SHARING NOW" to Green
            LinkoStateMachine.Availability.CONNECTED -> "CONNECTED" to Green
            LinkoStateMachine.Availability.CONNECTING -> "CONNECTING" to Yellow
            LinkoStateMachine.Availability.READY -> "READY" to Green
            LinkoStateMachine.Availability.ONLINE -> "ONLINE" to Green
            LinkoStateMachine.Availability.OFFLINE -> "OFFLINE" to TextMuted
            null -> if (friend.isSharing) "SHARING" to Green else "AVAILABLE" to Blue
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Friends Network", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("REAL-TIME LINKO PEERS", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, letterSpacing = 0.15.sp)
            }
            StatusChip(if (friends.isNotEmpty()) "${friends.size} TRUSTED" else "0 PEERS", if (friends.isNotEmpty()) Green else TextMuted)
        }

        Spacer(Modifier.height(16.dp))

        message?.let {
            LinkoCard {
                Text(it, color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (incoming.isNotEmpty()) {
            Text("INCOMING REQUESTS", color = Yellow, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp)
            Spacer(Modifier.height(8.dp))
            incoming.forEach { request ->
                val profile = request.optJSONObject("profile")
                val name = profile?.optString("display_name", "LINKO User") ?: "LINKO User"
                val id = profile?.optString("linko_id", "") ?: ""
                var responding by remember(request.optString("id")) { mutableStateOf(false) }

                GlassCard(accentColor = Yellow) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name.take(1).uppercase(), Yellow, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            if (id.isNotBlank()) Text(id, color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        PrimaryButton(
                            if (responding) "…" else "ACCEPT",
                            onClick = {
                                if (!responding) {
                                    responding = true
                                    scope.launch {
                                        runCatching { api.respond(request.optString("id"), true); reload() }
                                        responding = false
                                    }
                                }
                            },
                            color = Green,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(
                            "DECLINE",
                            onClick = {
                                if (!responding) {
                                    responding = true
                                    scope.launch {
                                        runCatching { api.respond(request.optString("id"), false); reload() }
                                        responding = false
                                    }
                                }
                            },
                            color = Red,
                            outline = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (outgoing.isNotEmpty()) {
            Text("REQUESTS PENDING", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp)
            Spacer(Modifier.height(8.dp))
            outgoing.take(10).forEach { request ->
                val profile = request.optJSONObject("profile")
                val name = profile?.optString("display_name", "LINKO User") ?: "LINKO User"
                val id = profile?.optString("linko_id", "") ?: ""
                LinkoCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name.take(1).uppercase(), Blue, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            if (id.isNotBlank()) Text(id, color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                        }
                        StatusChip("PENDING", Yellow)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (friends.isEmpty() && !loading) {
            GlassCard(accentColor = Blue) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("👥", fontSize = 32.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("NO FRIENDS ADDED YET", color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Connect with trusted friends by searching their LINKO ID or username.", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            if (friends.isNotEmpty()) {
                Text("TRUSTED FRIENDS", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp)
                Spacer(Modifier.height(8.dp))
            }
            friends.forEach { friend ->
                val (status, statusColor) = statusFor(friend)
                val isOnline = statusColor == Green
                LinkoCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { LinkoFriendsApiHolder.selected = friend; onFriendTap() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(friend.displayName.take(1).uppercase(), if (isOnline) Green else Blue, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(friend.displayName, color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            Text(friend.linkoId, color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isOnline) BlinkingDot(Green, 6.dp)
                                else Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                                Spacer(Modifier.width(6.dp))
                                Text(status, color = statusColor, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("›", color = TextMuted, fontSize = 22.sp, fontFamily = JetBrainsMono)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton("+ FIND FRIENDS", onFindFriends)
        Spacer(Modifier.height(24.dp))
    }
}
