package com.linkshare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import kotlinx.coroutines.launch

@Composable
fun LinkoRealtimeOverlay() {
    val scope = rememberCoroutineScope()
    var friendRequestId by remember { mutableStateOf<String?>(null) }
    var friendSenderName by remember { mutableStateOf<String?>(null) }
    var connectionSessionId by remember { mutableStateOf<String?>(null) }
    var requesterPeerName by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isCelebration by remember { mutableStateOf(false) }
    var processingConnection by remember { mutableStateOf(false) }
    var processingFriend by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.IncomingConnectionRequest -> {
                    connectionSessionId = event.sessionId
                    requesterPeerName = event.peerName
                    statusText = null
                    processingConnection = false
                }
                is LinkoRealtimeEvent.SessionStateChanged -> {
                    if (event.state == "requested" && event.sessionId != null) {
                        connectionSessionId = event.sessionId
                        statusText = null
                        processingConnection = false
                    } else if (event.state == "approved" || event.state == "connected") {
                        connectionSessionId = null
                        requesterPeerName = null
                        processingConnection = false
                    } else if (event.state == "denied" || event.state == "revoked" || event.state == "expired") {
                        connectionSessionId = null
                        requesterPeerName = null
                        processingConnection = false
                    }
                }
                is LinkoRealtimeEvent.FriendRequestReceived -> {
                    friendRequestId = event.requestId
                    friendSenderName = event.senderName
                    statusText = null
                    isCelebration = false
                    processingFriend = false
                }
                is LinkoRealtimeEvent.FriendRequestAccepted -> {
                    friendRequestId = null
                    friendSenderName = null
                    processingFriend = false
                    isCelebration = true
                    statusText = "🎉 FRIEND REQUEST ACCEPTED • YOU CAN NOW CONNECT & SHARE"
                }
                is LinkoRealtimeEvent.FriendRequestDeclined -> {
                    friendRequestId = null
                    friendSenderName = null
                    processingFriend = false
                    isCelebration = false
                    statusText = "DECLINED • REQUEST NOT ACCEPTED"
                }
                is LinkoRealtimeEvent.FriendRemoved -> {
                    friendRequestId = null
                    friendSenderName = null
                    processingFriend = false
                    statusText = "FRIEND REMOVED"
                }
                else -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // Status & Celebration Notification Banner
        if (statusText != null) {
            GlassCard(accentColor = if (isCelebration) Green else Blue, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCelebration) BlinkingDot(Green, 8.dp)
                    else Box(Modifier.size(6.dp).clip(CircleShape).background(Blue))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText ?: "",
                        color = if (isCelebration) Green else TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { statusText = null; isCelebration = false }, contentPadding = PaddingValues(0.dp)) {
                        Text("✕", color = TextMuted, fontSize = 12.sp, fontFamily = JetBrainsMono)
                    }
                }
            }
        }

        // 1. Prominent Incoming Connection (Session) Request Banner with Anti-Double-Tap Loading
        if (connectionSessionId != null) {
            GlassCard(accentColor = Yellow, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlinkingDot(Yellow, 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("⚡ INCOMING CONNECTION REQUEST", color = Yellow, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                SpacerSmall()
                Text("${requesterPeerName ?: "A verified friend"} wants to connect and use your shared internet.", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMono)
                SpacerSmall()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        label = if (processingConnection) "APPROVING…" else "APPROVE & SHARE",
                        onClick = {
                            if (!processingConnection) {
                                processingConnection = true
                                scope.launch {
                                    LinkoEngineBridge.approvePendingProviderRequest(peerName = requesterPeerName) { state ->
                                        if (state == "approved") {
                                            LinkoEngineBridge.startApprovedProviderSession()
                                            connectionSessionId = null
                                            requesterPeerName = null
                                            processingConnection = false
                                            isCelebration = true
                                            statusText = "SHARING ACTIVE • FRIEND CONNECTED"
                                        } else if (state.contains("failed") || state.contains("error")) {
                                            processingConnection = false
                                            statusText = state
                                        }
                                    }
                                }
                            }
                        },
                        color = Green,
                        enabled = !processingConnection,
                        loading = processingConnection,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        label = "DECLINE",
                        onClick = {
                            if (!processingConnection) {
                                processingConnection = true
                                scope.launch {
                                    LinkoEngineBridge.denyPendingProviderRequest {
                                        connectionSessionId = null
                                        requesterPeerName = null
                                        processingConnection = false
                                        statusText = "DECLINED • CONNECTION DISMISSED"
                                    }
                                }
                            }
                        },
                        color = Red,
                        outline = true,
                        enabled = !processingConnection,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Incoming Friend Invitation Banner with Anti-Double-Tap Loading (Xender Style)
        if (friendRequestId != null) {
            GlassCard(accentColor = Green, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar((friendSenderName ?: "U").take(1).uppercase(), Green, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("⚡ NEW FRIEND INVITATION", color = Green, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Text(friendSenderName ?: "A LINKO User wants to add you", color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium)
                    }
                }
                SpacerSmall()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        label = if (processingFriend) "ACCEPTING…" else "ACCEPT",
                        onClick = {
                            val id = friendRequestId ?: return@PrimaryButton
                            if (!processingFriend) {
                                processingFriend = true
                                scope.launch {
                                    runCatching { LinkoFriendsApiHolder.api.respond(id, true) }
                                        .onSuccess {
                                            friendRequestId = null
                                            friendSenderName = null
                                            processingFriend = false
                                            isCelebration = true
                                            statusText = "🎉 ACCEPTED • YOU ARE NOW FRIENDS"
                                        }
                                        .onFailure {
                                            processingFriend = false
                                            statusText = it.message ?: "Accept failed"
                                        }
                                }
                            }
                        },
                        color = Green,
                        enabled = !processingFriend,
                        loading = processingFriend,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        label = "DECLINE",
                        onClick = {
                            val id = friendRequestId ?: return@PrimaryButton
                            if (!processingFriend) {
                                processingFriend = true
                                scope.launch {
                                    runCatching { LinkoFriendsApiHolder.api.respond(id, false) }
                                        .onSuccess {
                                            friendRequestId = null
                                            friendSenderName = null
                                            processingFriend = false
                                            statusText = "DECLINED • REQUEST DISMISSED"
                                        }
                                        .onFailure {
                                            processingFriend = false
                                            statusText = it.message ?: "Decline failed"
                                        }
                                }
                            }
                        },
                        color = Red,
                        outline = true,
                        enabled = !processingFriend,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable private fun SpacerSmall() { androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp)) }
