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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.PrototypeScreen

@Composable
fun ReceiverHomeScreen(
    state: ConnectionUiState,
    onFriendSelected: (Friend) -> Unit,
    onUsage: () -> Unit,
    onSettings: () -> Unit
) {
    val screen = state.screen
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LinkoScreenHeader(
                when (screen) {
                    PrototypeScreen.HomeEngine -> "Home Engine"
                    else -> "Select Provider"
                },
                when (screen) {
                    PrototypeScreen.HomeEngine -> "Choose how LINKO operates"
                    else -> "Choose a trusted peer to connect through"
                }
            )
        }
        if (screen == PrototypeScreen.HomeEngine) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(32.dp))
                    LinkoRing(color = LinkoBlue, size = 210.dp, label = "READY", onClick = { state.friends.firstOrNull { it.isSharing }?.let(onFriendSelected) })
                    Spacer(Modifier.height(18.dp))
                    Text("ENCRYPTED • AUTHORIZED • PRIVATE", color = LinkoMuted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(28.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LinkoPrimaryButton("Receiver", { state.friends.firstOrNull { it.isSharing }?.let(onFriendSelected) }, Modifier.weight(1f))
                    LinkoSecondaryButton("Provider", onSettings, Modifier.weight(1f))
                }
            }
        } else {
            item {
                LinkoCard {
                    Text("Friends sharing data", color = LinkoInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("Only trusted peers who have enabled sharing appear here.", color = LinkoSub, fontSize = 12.sp)
                }
            }
            items(state.friends.filter { it.isSharing }) { friend ->
                LinkoCard {
                    Text(friend.name, color = LinkoInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${friend.deviceName} • ${friend.distanceLabel}", color = LinkoSub, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    LinkoPrimaryButton("Connect", { onFriendSelected(friend) }, Modifier.fillMaxWidth())
                }
            }
            item {
                LinkoCard {
                    Text("USAGE LIMIT", color = LinkoMuted, fontSize = 10.sp, letterSpacing = 1.6.sp)
                    Text("Provider controlled", color = LinkoInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("You will see limits before connecting.", color = LinkoSub, fontSize = 11.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LinkoSecondaryButton("Usage", onUsage, Modifier.weight(1f))
                LinkoSecondaryButton("Settings", onSettings, Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
fun ReceiverProgressScreen(state: ConnectionUiState, onBack: () -> Unit, onDisconnect: () -> Unit) {
    val (title, detail, color, label) = when (state.screen) {
        PrototypeScreen.RxRequest -> Quad("Requesting", "Sending a protected connection request", LinkoBlue, "REQUESTING")
        PrototypeScreen.RxWaiting -> Quad("Awaiting Approval", "Provider controls authorization", LinkoYellow, "WAITING")
        PrototypeScreen.RxApproved -> Quad("Session Approved", "Provider authorized this connection", LinkoGreen, "APPROVED")
        PrototypeScreen.RxConnecting -> Quad("Establishing Tunnel", "Negotiating secure path", LinkoBlue, "CONNECTING")
        PrototypeScreen.RxDirectPath -> Quad("Direct Path", "Fastest secure route selected", LinkoGreen, "DIRECT")
        PrototypeScreen.RxRelayFallback -> Quad("Relay Fallback", "Direct path unavailable", LinkoYellow, "RELAY")
        PrototypeScreen.Connected -> Quad("Connected", "Your traffic is protected through the approved connection", LinkoGreen, "CONNECTED")
        PrototypeScreen.ConnectionLost -> Quad("Connection Lost", "The protected path was interrupted", LinkoRed, "LOST")
        PrototypeScreen.Reconnecting -> Quad("Reconnecting", "Trying to restore the protected path", LinkoYellow, "RECONNECTING")
        PrototypeScreen.NetworkSwitching -> Quad("Network Switching", "Adapting the tunnel to the new network", LinkoYellow, "SWITCHING")
        PrototypeScreen.SessionExpired -> Quad("Session Expired", "The authorization has ended", LinkoRed, "EXPIRED")
        else -> Quad("Connection", "LINKO connection status", LinkoBlue, "LINKO")
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LinkoScreenHeader(title, detail)
        Spacer(Modifier.weight(1f))
        LinkoRing(color = color, size = 200.dp, label = label, pulse = state.connectionPhase != ConnectionPhase.Connected)
        Spacer(Modifier.height(8.dp))
        LinkoCard {
            Text("CONNECTION STATE", color = LinkoMuted, fontSize = 10.sp, letterSpacing = 1.6.sp)
            Text(state.connectionPhase.name.uppercase(), color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
            state.activeFriend?.let { Text("Through ${it.name}", color = LinkoSub, fontSize = 12.sp) }
        }
        Spacer(Modifier.weight(1f))
        if (state.connectionPhase == ConnectionPhase.Connected) {
            LinkoDangerButton("Disconnect", onDisconnect, Modifier.fillMaxWidth())
        } else {
            LinkoSecondaryButton("Back", onBack, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ProviderSharingScreen(state: ConnectionUiState, onToggle: () -> Unit, onApprove: () -> Unit, onDeny: () -> Unit) {
    val active = state.hostSharingEnabled
    val incoming = state.incomingRequest
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LinkoScreenHeader(
            if (incoming != null) "Incoming Request" else "Share your data",
            if (incoming != null) "Review before sharing" else "Share your connection with people you trust."
        )
        Spacer(Modifier.height(18.dp))
        LinkoCard {
            LinkoStatRow("Sharing", if (active) "On" else "Off")
            LinkoStatRow("Connected clients", state.usageStats.connectedClients.toString())
            LinkoPrimaryButton(if (active) "Stop sharing" else "Start sharing", onToggle, Modifier.fillMaxWidth())
        }
        incoming?.let { request ->
            LinkoCard {
                Text(request.friendName, color = LinkoInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("${request.deviceName} • ${request.distanceLabel}", color = LinkoSub, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LinkoPrimaryButton("Approve", onApprove, Modifier.weight(1f))
                    LinkoDangerButton("Deny", onDeny, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(if (active) "CONNECTED • TRUSTED" else "PRIVATE • AUTHORIZED", color = LinkoMuted, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
    }
}

private data class Quad(val title: String, val detail: String, val color: androidx.compose.ui.graphics.Color, val label: String)
