package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.network.LinkoStateMachine
import com.linkshare.app.ui.components.InfoRow
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import kotlinx.coroutines.launch

@Composable
fun RealFriendProfileScreen(onRequestSent: () -> Unit, onConnected: () -> Unit) {
    val friend = LinkoFriendsApiHolder.selected
    val api = LinkoFriendsApiHolder.api
    val engineState by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var relationship by remember(friend?.userId) { mutableStateOf(friend?.relationshipStatus ?: "none") }
    var availability by remember(friend?.userId) { mutableStateOf<LinkoStateMachine.Availability?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(friend?.userId) {
        availability = null
        LinkoRealtimeManager.events.collect { event ->
            if (event is LinkoRealtimeEvent.PresenceChanged && event.presence.userId == friend?.userId) {
                availability = LinkoStateMachine.availabilityFromPresence(event.presence.state, event.presence.online)
            }
        }
    }

    LaunchedEffect(engineState.phase) {
        if (!busy) return@LaunchedEffect
        when (engineState.phase) {
            LinkoConnectionPhase.Connected -> {
                busy = false
                onConnected()
            }
            LinkoConnectionPhase.Failed -> {
                busy = false
                message = engineState.error ?: "Connection failed"
            }
            else -> Unit
        }
    }

    val presenceLabel = when (availability) {
        LinkoStateMachine.Availability.ONLINE,
        LinkoStateMachine.Availability.READY,
        LinkoStateMachine.Availability.CONNECTING,
        LinkoStateMachine.Availability.SHARING,
        LinkoStateMachine.Availability.CONNECTED -> "ONLINE"
        LinkoStateMachine.Availability.OFFLINE -> "OFFLINE"
        null -> "CHECKING STATUS"
    }
    val presenceColor = when (availability) {
        LinkoStateMachine.Availability.OFFLINE -> Red
        null -> TextMuted
        else -> Green
    }

    val canConnect = relationship == "friend" && availability?.let {
        LinkoStateMachine.canRequestConnection(
            LinkoStateMachine.friendshipFromBackend(relationship),
            it,
        )
    } == true

    val actionLabel = when {
        busy -> "CONNECTING…"
        relationship == "friend" && availability == LinkoStateMachine.Availability.OFFLINE -> "FRIEND OFFLINE"
        relationship == "friend" -> "CONNECT TO FRIEND"
        relationship == "outgoing_pending" -> "REQUEST SENT"
        relationship == "incoming_pending" -> "REQUEST RECEIVED"
        else -> "ADD FRIEND"
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Ring(if (busy) Blue else Green, 120.dp, pulse = busy, label = if (busy) "LINKING" else "USER")
        Spacer(Modifier.height(16.dp))
        Text(friend?.displayName ?: "LINKO USER", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(friend?.linkoId ?: "No friend selected", color = Blue, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))

        LinkoCard {
            InfoRow(
                "FRIENDSHIP",
                when (relationship) {
                    "friend" -> "CONNECTED FRIEND"
                    "outgoing_pending" -> "REQUEST SENT"
                    "incoming_pending" -> "REQUEST RECEIVED"
                    else -> "NOT FRIENDS"
                },
                "Server relationship state",
                accent = when (relationship) {
                    "friend" -> Green
                    "outgoing_pending", "incoming_pending" -> Blue
                    else -> Red
                },
            )
            Spacer(Modifier.height(12.dp))
            InfoRow(
                "PRESENCE",
                presenceLabel,
                "Real device heartbeat state",
                accent = presenceColor,
            )
            Spacer(Modifier.height(12.dp))
            if (busy) {
                InfoRow("CONNECTION", engineState.detail, "Real engine state", accent = Blue)
            } else {
                InfoRow(
                    "DEVICE",
                    if (friend?.isSharing == true) "AVAILABLE" else "NOT SHARING",
                    friend?.deviceName ?: "Provider availability is resolved when connecting",
                    accent = if (friend?.isSharing == true) Green else Blue,
                )
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Red, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            actionLabel,
            {
                val selected = friend
                if (busy || selected == null) return@PrimaryButton
                when (relationship) {
                    "friend" -> {
                        if (!canConnect) {
                            message = "Friend is offline or not ready for a connection"
                            return@PrimaryButton
                        }
                        busy = true
                        message = null
                        LinkoEngineBridge.connectToFriend(selected.userId, selected.displayName, selected.linkoId)
                    }
                    "none" -> {
                        busy = true
                        message = null
                        scope.launch {
                            runCatching { api.sendRequest(selected.userId) }
                                .onSuccess { response ->
                                    when (response.optString("state")) {
                                        "friend" -> relationship = "friend"
                                        "outgoing_pending" -> {
                                            relationship = "outgoing_pending"
                                            onRequestSent()
                                        }
                                        else -> message = "Request state: ${response.optString("state", "unknown")}"
                                    }
                                }
                                .onFailure { message = it.message ?: "Request failed" }
                            busy = false
                        }
                    }
                }
            },
            color = if (relationship == "friend") Green else Blue,
            outline = relationship != "friend" && relationship != "none",
        )
        Spacer(Modifier.height(24.dp))
    }
}
