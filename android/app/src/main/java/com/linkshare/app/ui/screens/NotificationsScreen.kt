package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoNotification
import com.linkshare.app.network.LinkoNotificationCenter
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Card
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
                .onSuccess {
                    LinkoNotificationCenter.remove(notification.id)
                    LinkoNotificationCenter.add(
                        LinkoNotification(
                            id = "local-response:$requestId:${if (accepted) "accepted" else "declined"}",
                            title = if (accepted) "Friend Request Accepted" else "Friend Request Declined",
                            message = if (accepted) "You are now LINKO friends." else "The friend request was declined.",
                            kind = if (accepted) LinkoNotification.Kind.FRIEND_ACCEPTED else LinkoNotification.Kind.FRIEND_DECLINED,
                            requestId = requestId,
                        )
                    )
                }
                .onFailure { error = it.message ?: "Request action failed" }
            respondingId = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notifications", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("Your LINKO activity and connection events", color = TextSub, fontSize = 12.sp)
            }
            if (notifications.isNotEmpty()) {
                IconButton(onClick = { LinkoNotificationCenter.clear(); error = null }) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "Clear all notifications", tint = TextSub)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (notifications.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Blue.copy(alpha = .10f)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "${notifications.size} ${if (notifications.size == 1) "notification" else "notifications"}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMono,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        error?.let {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Red.copy(alpha = .10f)) {
                Text(it, color = Red, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (notifications.isEmpty()) {
            EmptyNotifications()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(notification, respondingId, ::respond)
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: LinkoNotification,
    respondingId: String?,
    onRespond: (LinkoNotification, Boolean) -> Unit,
) {
    val (icon, accent) = notificationVisuals(notification.kind)
    LinkoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(notification.title, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(relativeTime(notification.createdAt), color = TextSub, fontSize = 9.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text(notification.message, color = TextSub, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        if (notification.kind == LinkoNotification.Kind.FRIEND_REQUEST_INCOMING && !notification.requestId.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                PrimaryButton(if (respondingId == notification.id) "..." else "ACCEPT", { onRespond(notification, true) }, color = Green)
                Spacer(Modifier.width(8.dp))
                PrimaryButton(if (respondingId == notification.id) "..." else "DECLINE", { onRespond(notification, false) }, color = Red, outline = true)
            }
        }

        Spacer(Modifier.height(7.dp))
        Text("LINKO", color = Blue, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyNotifications() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Blue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = Blue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("You're all caught up", color = Green, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Friend requests, connection changes and important LINKO events will appear here.", color = TextSub, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private fun notificationVisuals(kind: LinkoNotification.Kind): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (kind) {
    LinkoNotification.Kind.FRIEND_REQUEST_INCOMING,
    LinkoNotification.Kind.FRIEND_REQUEST_SENT -> Icons.Filled.GroupAdd to Blue
    LinkoNotification.Kind.FRIEND_ACCEPTED,
    LinkoNotification.Kind.CONNECTION_CONNECTED -> Icons.Filled.CheckCircle to Green
    LinkoNotification.Kind.FRIEND_DECLINED,
    LinkoNotification.Kind.FRIEND_REMOVED -> Icons.Filled.PersonRemove to Red
    LinkoNotification.Kind.FRIEND_ONLINE -> Icons.Filled.Wifi to Green
    LinkoNotification.Kind.FRIEND_OFFLINE -> Icons.Filled.CloudOff to TextSub
    LinkoNotification.Kind.CONNECTION,
    LinkoNotification.Kind.REALTIME_ERROR,
    LinkoNotification.Kind.CONNECTION_FAILED -> Icons.Filled.Sync to Blue
}

private fun relativeTime(timestamp: Long): String {
    val seconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)) / 1000L
    return when {
        seconds < 10 -> "NOW"
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        else -> "${seconds / 86_400}d"
    }
}
