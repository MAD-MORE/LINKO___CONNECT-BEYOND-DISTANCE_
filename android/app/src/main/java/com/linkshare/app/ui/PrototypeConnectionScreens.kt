package com.linkshare.app.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend

@Composable
fun ReceiverHomeScreen(
    state: ConnectionUiState,
    onFriendSelected: (Friend) -> Unit,
    onUsage: () -> Unit,
    onSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LinkoScreenHeader(
                "Connect beyond distance",
                "Choose a trusted friend and request a protected connection through their phone."
            )
        }
        item {
            LinkoCard {
                Text("Friends sharing data", color = LinkoInk, fontSize = 20.dp.value.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                Text("Only friends who have enabled sharing appear here.", color = LinkoInk.copy(alpha = .62f))
            }
        }
        items(state.friends.filter { it.isSharing }) { friend ->
            LinkoCard {
                Text(friend.name, color = LinkoInk, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("${friend.deviceName} · ${friend.distanceLabel}", color = LinkoInk.copy(alpha = .62f))
                LinkoPrimaryButton("Connect", { onFriendSelected(friend) }, Modifier.fillMaxWidth())
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LinkoSecondaryButton("Usage", onUsage, Modifier.weight(1f))
                LinkoSecondaryButton("Settings", onSettings, Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
fun ReceiverProgressScreen(state: ConnectionUiState, onBack: () -> Unit, onDisconnect: () -> Unit) {
    val phaseTitle = when (state.screen.name) {
        "RxRequest" -> "Requesting access"
        "RxWaiting" -> "Waiting for approval"
        "RxApproved" -> "Approved"
        "RxConnecting" -> "Connecting"
        "RxDirectPath" -> "Direct path ready"
        "RxRelayFallback" -> "Relay fallback"
        "Connected" -> "Connected"
        else -> "Connection"
    }
    val phaseDetail = state.eventMessage ?: when (state.connectionPhase) {
        ConnectionPhase.Requesting -> "Your friend will decide whether to share their connection."
        ConnectionPhase.Handshaking -> "LINKO is establishing the protected path."
        ConnectionPhase.Retrying -> "The direct path was unavailable. LINKO is trying relay fallback."
        ConnectionPhase.Connected -> "Your traffic is protected through the approved connection."
        else -> "LINKO is preparing the connection."
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinkoScreenHeader(phaseTitle, phaseDetail)
        LinkoCard {
            LinkoStatusDot(state.connectionPhase == ConnectionPhase.Connected)
            Text("Connection state", color = LinkoInk.copy(alpha = .62f))
            Text(state.connectionPhase.name, color = LinkoInk, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            state.activeFriend?.let { Text("Through ${it.name}", color = LinkoInk.copy(alpha = .68f)) }
        }
        Spacer(Modifier.weight(1f))
        if (state.connectionPhase == ConnectionPhase.Connected) {
            LinkoSecondaryButton("Disconnect", onDisconnect, Modifier.fillMaxWidth())
        } else {
            LinkoSecondaryButton("Back", onBack, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ProviderSharingScreen(state: ConnectionUiState, onToggle: () -> Unit, onApprove: () -> Unit, onDeny: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinkoScreenHeader(
            if (state.incomingRequest != null) "Incoming request" else if (state.hostSharingEnabled) "Sharing active" else "Share your data",
            state.eventMessage ?: "Share your connection only with people you trust."
        )
        LinkoCard {
            LinkoStatRow("Sharing", if (state.hostSharingEnabled) "Active" else "Off")
            LinkoStatRow("Connected clients", state.usageStats.connectedClients.toString())
            LinkoPrimaryButton(if (state.hostSharingEnabled) "Stop sharing" else "Start sharing", onToggle, Modifier.fillMaxWidth())
        }
        state.incomingRequest?.let { request ->
            LinkoCard {
                Text(request.friendName, color = LinkoInk, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                Text("${request.deviceName} · ${request.distanceLabel}", color = LinkoInk.copy(alpha = .62f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LinkoPrimaryButton("Approve", onApprove, Modifier.weight(1f))
                    LinkoSecondaryButton("Deny", onDeny, Modifier.weight(1f))
                }
            }
        }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.sp(this)
