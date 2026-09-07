package com.linkshare.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
private fun OnlinePresenceBadge(isOnline: Boolean, isSharing: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "presence")
    val pulse by transition.animateFloat(
        0.55f,
        1f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "presencePulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
            if (isOnline) {
                Box(Modifier.size(15.dp).clip(CircleShape).background(Green.copy(alpha = 0.16f * pulse)))
            }
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Green else TextMuted)
                    .border(1.dp, if (isOnline) Green.copy(alpha = 0.65f) else TextMuted.copy(alpha = 0.5f), CircleShape),
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            when {
                isSharing -> "ONLINE • SHARING"
                isOnline -> "ONLINE"
                else -> "OFFLINE"
            },
            color = if (isOnline) Green else TextMuted,
            fontSize = 9.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun FriendsScreen(onFindFriends: () -> Unit, onFriendTap: () -> Unit) {
    val api = LinkoFriendsApiHolder.api
    var friends by remember { mutableStateOf<List<FriendSearchResult>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var resolved by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh(showLoading: Boolean = true) {
        scope.launch {
            if (showLoading) loading = true
            try {
                val f = api.friends().optJSONArray("friends") ?: org.json.JSONArray()
                friends = buildList {
                    for (i in 0 until f.length()) {
                        val o = f.optJSONObject(i) ?: continue
                        add(
                            FriendSearchResult(
                                o.optString("user_id"),
                                o.optString("linko_id"),
                                o.optString("display_name").ifBlank { "LINKO User" },
                                isSharing = o.optBoolean("is_sharing", false),
                                isOnline = o.optBoolean("is_online", o.optBoolean("is_sharing", false)),
                                relationshipStatus = "friend",
                                username = o.optString("username").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }

                val r = api.requests().optJSONArray("requests") ?: org.json.JSONArray()
                incoming = buildList {
                    for (i in 0 until r.length()) {
                        val o = r.optJSONObject(i) ?: continue
                        if (o.optBoolean("incoming") && o.optString("status") == "pending") add(o)
                    }
                }
                outgoing = buildList {
                    for (i in 0 until r.length()) {
                        val o = r.optJSONObject(i) ?: continue
                        if (!o.optBoolean("incoming") && o.optString("status") == "pending") add(o)
                    }
                }
                resolved = buildList {
                    for (i in 0 until r.length()) {
                        val o = r.optJSONObject(i) ?: continue
                        val status = o.optString("status")
                        if (!o.optBoolean("incoming") && (status == "accepted" || status == "declined")) add(o)
                    }
                }
            } catch (e: Exception) {
                message = e.message ?: "Unable to load friends"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Realtime is the fast path; the API refresh is authoritative and carries the full profile/request data.
    LaunchedEffect(Unit) {
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.FriendRequestReceived -> {
                    message = "NEW FRIEND REQUEST RECEIVED"
                    refresh(showLoading = false)
                }
                is LinkoRealtimeEvent.FriendRequestSent -> {
                    message = "FRIEND REQUEST SENT"
                    refresh(showLoading = false)
                }
                is LinkoRealtimeEvent.FriendRequestAccepted -> {
                    message = "FRIEND REQUEST ACCEPTED • YOU ARE NOW FRIENDS"
                    refresh(showLoading = false)
                }
                is LinkoRealtimeEvent.FriendRequestDeclined -> {
                    message = "FRIEND REQUEST DECLINED"
                    refresh(showLoading = false)
                }
                is LinkoRealtimeEvent.FriendRemoved -> {
                    message = "FRIEND REMOVED"
                    refresh(showLoading = false)
                }
                else -> Unit
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Friends", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Your LINKO connection network", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(16.dp))

        message?.let {
            Text(it, color = if (it.contains("ACCEPTED") || it.contains("SENT") || it.contains("RECEIVED")) Green else TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        if (incoming.isNotEmpty()) {
            Text("FRIEND REQUESTS", color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            incoming.forEach { request ->
                val profile = request.optJSONObject("profile")
                var responding by remember(request.optString("id")) { mutableStateOf(false) }
                LinkoCard {
                    Text(profile?.optString("display_name", "LINKO User") ?: "LINKO User", color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(profile?.optString("linko_id", "") ?: "", color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    profile?.optString("username")?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(3.dp))
                        Text("@${it}", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        PrimaryButton(
                            if (responding) "..." else "ACCEPT",
                            {
                                if (!responding) {
                                    responding = true
                                    scope.launch {
                                        try {
                                            api.respond(request.optString("id"), true)
                                            message = "FRIEND REQUEST ACCEPTED"
                                            refresh(showLoading = false)
                                        } catch (e: Exception) {
                                            message = e.message ?: "Accept failed"
                                            responding = false
                                        }
                                    }
                                }
                            },
                            color = Green,
                        )
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(
                            if (responding) "" else "DECLINE",
                            {
                                if (!responding) {
                                    responding = true
                                    scope.launch {
                                        try {
                                            api.respond(request.optString("id"), false)
                                            message = "FRIEND REQUEST DECLINED"
                                            refresh(showLoading = false)
                                        } catch (e: Exception) {
                                            message = e.message ?: "Decline failed"
                                            responding = false
                                        }
                                    }
                                }
                            },
                            color = Red,
                            outline = true,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (outgoing.isNotEmpty()) {
            Text("REQUESTS SENT", color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            outgoing.take(10).forEach { request ->
                val profile = request.optJSONObject("profile")
                LinkoCard {
                    Text(profile?.optString("display_name", "LINKO User") ?: "LINKO User", color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(profile?.optString("linko_id", "") ?: "", color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    profile?.optString("username")?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(3.dp))
                        Text("@${it}", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("PENDING • WAITING FOR ACCEPTANCE", color = Yellow, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (resolved.isNotEmpty()) {
            Text("REQUEST HISTORY", color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            resolved.take(10).forEach { request ->
                val profile = request.optJSONObject("profile")
                val accepted = request.optString("status") == "accepted"
                LinkoCard {
                    Text(profile?.optString("display_name", "LINKO User") ?: "LINKO User", color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(profile?.optString("linko_id", "") ?: "", color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (accepted) "ACCEPTED • YOU ARE NOW FRIENDS" else "DECLINED • REQUEST NOT ACCEPTED",
                        color = if (accepted) Green else Red,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (friends.isEmpty() && !loading) {
            LinkoCard {
                Text("NO FRIENDS YET", color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Find a real LINKO user by their LINKO ID or username.", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono)
            }
        } else {
            friends.forEach { f ->
                LinkoCard {
                    Column(Modifier.fillMaxWidth().clickable { LinkoFriendsApiHolder.selected = f; onFriendTap() }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.displayName, color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            OnlinePresenceBadge(f.isOnline, f.isSharing)
                        }
                        Text(f.linkoId, color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (f.isSharing) "FRIEND • INTERNET SHARING ACTIVE" else if (f.isOnline) "FRIEND • READY TO CONNECT" else "FRIEND • OFFLINE",
                            color = if (f.isOnline) Green else TextMuted,
                            fontSize = 10.sp,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton("+ FIND FRIENDS", onFindFriends)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FindFriendsScreen(onSearch: () -> Unit) {
    val api = LinkoFriendsApiHolder.api
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FriendSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var acceptedFriend by remember { mutableStateOf<FriendSearchResult?>(null) }
    val scope = rememberCoroutineScope()
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rippleScale by infiniteTransition.animateFloat(.4f, 1f, infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "radarRipple")
    val rippleAlpha by infiniteTransition.animateFloat(.8f, 0f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), label = "radarAlpha")

    LaunchedEffect(Unit) {
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.FriendRequestAccepted -> {
                    acceptedFriend = results.firstOrNull() ?: LinkoFriendsApiHolder.selected
                }
                is LinkoRealtimeEvent.FriendRequestDeclined -> {
                    message = "FRIEND REQUEST DECLINED"
                }
                else -> Unit
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("Radar Discovery", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Discover online LINKO peers • Tap to invite instantly", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        if (acceptedFriend != null) {
            GlassCard(accentColor = Green, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlinkingDot(Green, 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("🎉 FRIEND REQUEST ACCEPTED!", color = Green, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("You and ${acceptedFriend?.displayName ?: "Friend"} are now connected. You can start sharing internet immediately!", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(14.dp))
                PrimaryButton("⚡ CONNECT & SHARE NOW", { acceptedFriend?.let { LinkoEngineBridge.connectToFriend(it.userId, it.displayName, it.linkoId) } }, color = Green)
            }
            Spacer(Modifier.height(14.dp))
        }

        Box(Modifier.size(200.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Blue.copy(alpha = .15f), Color.Transparent))).border(1.dp, Border, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp * rippleScale).clip(CircleShape).border(1.5.dp, Green.copy(alpha = rippleAlpha), CircleShape))
            Box(Modifier.size(130.dp).clip(CircleShape).border(1.dp, Blue.copy(alpha = .25f), CircleShape))
            Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Blue, Accent))).border(2.dp, Color.White.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
                Text("📡", fontSize = 22.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        LinkoInput("SEARCH ID OR USERNAME", query, { query = it }, "LNK-XXXXXXXX", "Enter LINKO ID or username")
        Spacer(Modifier.height(10.dp))
        message?.let {
            Text(it, color = if (it.contains("ACCEPTED")) Green else Red, fontSize = 11.sp, fontFamily = JetBrainsMono)
            Spacer(Modifier.height(6.dp))
        }

        results.forEach { f ->
            LinkoCard {
                Row(Modifier.fillMaxWidth().clickable { LinkoFriendsApiHolder.selected = f; onSearch() }, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(f.displayName.take(1).uppercase(), if (f.isOnline) Green else TextMuted, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.displayName, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(f.linkoId, color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                        OnlinePresenceBadge(f.isOnline, f.isSharing)
                    }
                    StatusChip(
                        when (f.relationshipStatus) {
                            "friend" -> "FRIENDS"
                            "outgoing_pending" -> "SENT"
                            "incoming_pending" -> "RECEIVED"
                            else -> "INVITE +"
                        },
                        when (f.relationshipStatus) {
                            "friend" -> Green
                            "outgoing_pending", "incoming_pending" -> Yellow
                            else -> Blue
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            if (searching) "SCANNING…" else "SCAN & SEARCH",
            {
                if (!searching) {
                    searching = true
                    message = null
                    scope.launch {
                        try {
                            if (query.trim().length < 2) throw IllegalArgumentException("Enter at least 2 characters")
                            results = api.search(query)
                            if (results.isEmpty()) message = "No online LINKO users found."
                        } catch (e: Exception) {
                            message = e.message ?: "Search failed"
                        } finally {
                            searching = false
                        }
                    }
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}
