package com.linkshare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import kotlinx.coroutines.launch

@Composable
fun LinkoRealtimeOverlay() {
    val scope = rememberCoroutineScope()
    var friendRequestId by remember { mutableStateOf<String?>(null) }
    var connectionSessionId by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.IncomingConnectionRequest -> {
                    connectionSessionId = event.sessionId
                    statusText = null
                }
                is LinkoRealtimeEvent.SessionStateChanged -> {
                    if (event.state == "requested" && event.sessionId != null) {
                        connectionSessionId = event.sessionId
                        statusText = null
                    } else if (event.state == "approved" || event.state == "connected") {
                        connectionSessionId = null
                    } else if (event.state == "denied" || event.state == "revoked" || event.state == "expired") {
                        connectionSessionId = null
                    }
                }
                is LinkoRealtimeEvent.FriendRequestReceived -> {
                    friendRequestId = event.requestId
                    statusText = null
                }
                is LinkoRealtimeEvent.FriendRequestAccepted -> {
                    friendRequestId = null
                    statusText = "ACCEPTED • YOU ARE NOW FRIENDS"
                }
                is LinkoRealtimeEvent.FriendRequestDeclined -> {
                    friendRequestId = null
                    statusText = "DECLINED • REQUEST NOT ACCEPTED"
                }
                is LinkoRealtimeEvent.FriendRemoved -> {
                    friendRequestId = null
                    statusText = "FRIEND REMOVED"
                }
                else -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // 1. Prominent Incoming Connection (Session) Request Banner
        if (connectionSessionId != null) {
            LinkoCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Green, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("⚡ INCOMING CONNECTION REQUEST", color = Green, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                SpacerSmall()
                Text("A friend is requesting to connect and share your internet.", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMono)
                SpacerSmall()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("APPROVE & SHARE", {
                        scope.launch {
                            LinkoEngineBridge.approvePendingProviderRequest { state ->
                                if (state == "approved") {
                                    LinkoEngineBridge.startApprovedProviderSession()
                                    connectionSessionId = null
                                    statusText = "SHARING ACTIVE • FRIEND CONNECTED"
                                }
                            }
                        }
                    }, color = Green)
                    PrimaryButton("DECLINE", {
                        scope.launch {
                            LinkoEngineBridge.denyPendingProviderRequest {
                                connectionSessionId = null
                                statusText = "DECLINED • CONNECTION DISMISSED"
                            }
                        }
                    }, color = Red, outline = true)
                }
            }
        }

        // 2. Incoming Friend Invitation Banner
        if (friendRequestId != null) {
            LinkoCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("NEW FRIEND REQUEST", color = Yellow, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                SpacerSmall()
                Text("A LINKO user wants to add you to their network.", color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMono)
                SpacerSmall()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("ACCEPT", {
                        val id = friendRequestId ?: return@PrimaryButton
                        scope.launch {
                            runCatching { LinkoFriendsApiHolder.api.respond(id, true) }
                                .onSuccess { friendRequestId = null; statusText = "ACCEPTED • YOU ARE NOW FRIENDS" }
                                .onFailure { statusText = it.message ?: "Accept failed" }
                        }
                    }, color = Green)
                    PrimaryButton("DECLINE", {
                        val id = friendRequestId ?: return@PrimaryButton
                        scope.launch {
                            runCatching { LinkoFriendsApiHolder.api.respond(id, false) }
                                .onSuccess { friendRequestId = null; statusText = "DECLINED • REQUEST NOT ACCEPTED" }
                                .onFailure { statusText = it.message ?: "Decline failed" }
                        }
                    }, color = Red, outline = true)
                }
            }
        }

        // 3. Temporary Status Feedback Toast
        statusText?.let { status ->
            LaunchedEffect(status) {
                kotlinx.coroutines.delay(3500)
                if (statusText == status) statusText = null
            }
            LinkoCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    status,
                    color = if (status.startsWith("ACCEPTED") || status.startsWith("SHARING") || status.startsWith("CONNECTED")) Green else if (status.startsWith("DECLINED")) Red else Yellow,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable private fun SpacerSmall() { androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp)) }
