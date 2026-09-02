package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Card
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub

@Composable
fun NotificationsScreen() {
    val notifications = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        LinkoRealtimeManager.events.collect { event ->
            val message = when (event) {
                is LinkoRealtimeEvent.FriendRequestReceived -> "New friend request received"
                is LinkoRealtimeEvent.FriendRequestAccepted -> "Friend request accepted"
                is LinkoRealtimeEvent.FriendRequestDeclined -> "Friend request declined"
                is LinkoRealtimeEvent.FriendRemoved -> "Friend removed"
                is LinkoRealtimeEvent.IncomingConnectionRequest -> "Incoming connection request"
                is LinkoRealtimeEvent.SessionStateChanged -> when (event.state) {
                    "requested" -> "Connection request sent"
                    "approved" -> "Connection approved"
                    "connected" -> "LINKO connection established"
                    "denied" -> "Connection request declined"
                    "revoked" -> "Connection ended"
                    "expired" -> "Connection session expired"
                    else -> event.state?.replaceFirstChar { it.uppercase() } ?: "Connection updated"
                }
                is LinkoRealtimeEvent.TransportError -> "Connection service: ${event.message}"
                else -> null
            }
            if (message != null) {
                notifications.add(0, message)
                while (notifications.size > 50) notifications.removeLast()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text("Notifications", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(6.dp))
        Text("Friend requests and connection activity appear here.", color = TextSub, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        if (notifications.isEmpty()) {
            Surface(color = Card, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You’re all caught up", color = Green, fontSize = 16.sp, fontFamily = JetBrainsMono)
                    Text("New activity from LINKO will show here automatically.", color = TextSub, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notifications) { text ->
                    Surface(color = Card, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text, color = TextPrimary, fontSize = 12.sp)
                            Text("LIVE", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono)
                        }
                    }
                }
            }
        }
    }
}
