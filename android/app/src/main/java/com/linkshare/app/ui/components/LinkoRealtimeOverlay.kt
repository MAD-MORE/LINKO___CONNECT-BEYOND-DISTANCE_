package com.linkshare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import com.linkshare.app.ui.theme.JetBrainsMono
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LinkoRealtimeOverlay() {
    val scope = rememberCoroutineScope()
    var requestId by remember { mutableStateOf<String?>(null) }
    var requestLabel by remember { mutableStateOf("LINKO friend request") }
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.FriendRequestReceived -> {
                    requestId = event.requestId
                    requestLabel = "New LINKO friend request"
                    statusText = null
                }
                is LinkoRealtimeEvent.FriendRequestAccepted -> {
                    requestId = null
                    statusText = "ACCEPTED • YOU ARE NOW FRIENDS"
                }
                is LinkoRealtimeEvent.FriendRequestDeclined -> {
                    requestId = null
                    statusText = "DECLINED • REQUEST NOT ACCEPTED"
                }
                is LinkoRealtimeEvent.FriendRemoved -> {
                    requestId = null
                    statusText = "FRIEND REMOVED"
                }
                else -> Unit
            }
        }
    }

    if (requestId != null) {
        LinkoCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(requestLabel, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            SpacerSmall()
            Text("Open request: ${requestId!!.take(8)}…", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
            SpacerSmall()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("ACCEPT", {
                    val id = requestId ?: return@PrimaryButton
                    scope.launch {
                        runCatching { LinkoFriendsApiHolder.api.respond(id, true) }
                            .onSuccess { requestId = null; statusText = "ACCEPTED • YOU ARE NOW FRIENDS" }
                            .onFailure { statusText = it.message ?: "Accept failed" }
                    }
                }, color = Green)
                PrimaryButton("DECLINE", {
                    val id = requestId ?: return@PrimaryButton
                    scope.launch {
                        runCatching { LinkoFriendsApiHolder.api.respond(id, false) }
                            .onSuccess { requestId = null; statusText = "DECLINED • REQUEST NOT ACCEPTED" }
                            .onFailure { statusText = it.message ?: "Decline failed" }
                    }
                }, color = Red, outline = true)
            }
        }
    }

    statusText?.let { status ->
        LaunchedEffect(status) {
            kotlinx.coroutines.delay(3500)
            if (statusText == status) statusText = null
        }
        LinkoCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(status, color = if (status.startsWith("ACCEPTED")) Green else if (status.startsWith("DECLINED")) Red else Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun SpacerSmall() { androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp)) }
