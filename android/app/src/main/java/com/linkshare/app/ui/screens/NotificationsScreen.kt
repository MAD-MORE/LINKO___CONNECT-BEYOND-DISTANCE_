package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoNotification
import com.linkshare.app.network.LinkoNotificationCenter
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.Card
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen() {
    val notifications by LinkoNotificationCenter.notifications.collectAsState()
    val scope = rememberCoroutineScope()
    var respondingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun respond(notification: LinkoNotification, accepted: Boolean) {
        val requestId = notification.requestId ?: return
        if (respondingId != null) return
        respondingId = notification.id
        error = null
        scope.launch {
            runCatching { LinkoFriendsApiHolder.api.respond(requestId, accepted) }
                .onSuccess { LinkoNotificationCenter.remove(notification.id) }
                .onFailure { error = it.message ?: "Request action failed" }
            respondingId = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("Notifications", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(6.dp))
        Text("Friend requests, responses and connection updates appear here.", color = TextSub, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        error?.let { Text(it, color = Red, fontSize = 11.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(8.dp)) }
        if (notifications.isEmpty()) {
            Surface(color = Card, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You're all caught up", color = Green, fontSize = 16.sp, fontFamily = JetBrainsMono)
                    Text("New LINKO activity will appear here automatically.", color = TextSub, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notifications, key = { it.id }) { notification ->
                    LinkoCard {
                        Text(notification.title, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono)
                        Spacer(Modifier.height(5.dp))
                        Text(notification.message, color = TextSub, fontSize = 12.sp)
                        if (notification.kind == LinkoNotification.Kind.FRIEND_REQUEST_INCOMING && !notification.requestId.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth()) {
                                PrimaryButton(if (respondingId == notification.id) "..." else "ACCEPT", { respond(notification, true) }, color = Green)
                                Spacer(Modifier.width(8.dp))
                                PrimaryButton(if (respondingId == notification.id) "..." else "DECLINE", { respond(notification, false) }, color = Red, outline = true)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("LINKO", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono)
                    }
                }
            }
        }
    }
}
