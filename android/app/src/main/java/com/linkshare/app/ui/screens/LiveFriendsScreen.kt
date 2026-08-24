package com.linkshare.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.FriendSearchResult
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
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
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

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
        reload()
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.FriendRequestReceived,
                is LinkoRealtimeEvent.FriendRequestAccepted,
                is LinkoRealtimeEvent.FriendRequestDeclined,
                is LinkoRealtimeEvent.FriendRemoved,
                is LinkoRealtimeEvent.PresenceChanged -> reload()
                else -> Unit
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Friends", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Text("LIVE LINKO CONNECTION NETWORK", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(14.dp))

        message?.let { Text(it, color = Red, fontSize = 11.sp, fontFamily = JetBrainsMono) }

        if (incoming.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("INCOMING REQUESTS", color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            incoming.forEach { request ->
                val profile = request.optJSONObject("profile")
                LinkoCard {
                    Text(profile?.optString("display_name", "LINKO User") ?: "LINKO User", color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Text(profile?.optString("linko_id", "") ?: "", color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        PrimaryButton("ACCEPT", { scope.launch { runCatching { api.respond(request.optString("id"), true) }.onFailure { message = it.message ?: "Accept failed" } } }, color = Green)
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton("DECLINE", { scope.launch { runCatching { api.respond(request.optString("id"), false) }.onFailure { message = it.message ?: "Decline failed" } } }, color = Red, outline = true)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (outgoing.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("OUTGOING", color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            outgoing.take(10).forEach { request ->
                val profile = request.optJSONObject("profile")
                LinkoCard {
                    Text(profile?.optString("display_name", "LINKO User") ?: "LINKO User", color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Text("PENDING • WAITING FOR ACCEPTANCE", color = Yellow, fontSize = 10.sp, fontFamily = JetBrainsMono)
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("LATEST RESULTS", color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            history.take(10).forEach { request ->
                val profile = request.optJSONObject("profile")
                val accepted = request.optString("status") == "accepted"
                LinkoCard {
                    Text(profile?.optString("display_name", "LINKO User") ?: "LINKO User", color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Text(if (accepted) "ACCEPTED • YOU ARE NOW FRIENDS" else "DECLINED • REQUEST NOT ACCEPTED", color = if (accepted) Green else Red, fontSize = 10.sp, fontFamily = JetBrainsMono)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (friends.isEmpty() && !loading) {
            LinkoCard {
                Text("NO FRIENDS YET", color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text("Find friends by LINKO ID or username.", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono)
            }
        } else {
            friends.forEach { friend ->
                LinkoCard {
                    Column(Modifier.fillMaxWidth().clickable { LinkoFriendsApiHolder.selected = friend; onFriendTap() }) {
                        Text(friend.displayName, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(friend.linkoId, color = Blue, fontSize = 11.sp, fontFamily = JetBrainsMono)
                        Text(if (friend.isSharing) "ONLINE • SHARING NOW" else "ONLINE • PROVIDER READY", color = Green, fontSize = 10.sp, fontFamily = JetBrainsMono)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("+ FIND FRIENDS", onFindFriends)
        Spacer(Modifier.height(24.dp))
    }
}
