package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight

@Composable
fun RealReconnectingScreen(onConnected: () -> Unit, onFailed: () -> Unit) {
    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    val peer = state.peerDisplayName?.takeIf { it.isNotBlank() } ?: state.peerLinkoId?.takeIf { it.isNotBlank() } ?: "LINKO peer"
    val failureReason = (state.error ?: state.detail).lowercase()
    val directPathFailure = failureReason.contains("direct") || failureReason.contains("ice") || failureReason.contains("candidate") || failureReason.contains("nomination") || failureReason.contains("probe")

    LaunchedEffect(Unit) {
        LinkoEngineBridge.reconnect()
    }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            LinkoConnectionPhase.Connected -> onConnected()
            LinkoConnectionPhase.Failed -> onFailed()
            else -> Unit
        }
    }

    val color = when (state.phase) {
        LinkoConnectionPhase.Failed -> Red
        LinkoConnectionPhase.Connected -> Green
        else -> Blue
    }
    val label = when {
        state.phase == LinkoConnectionPhase.Connected -> "CONNECTED"
        state.phase == LinkoConnectionPhase.Failed -> "FAILED"
        state.phase == LinkoConnectionPhase.Signaling -> "NEGOTIATING"
        state.phase == LinkoConnectionPhase.Establishing || state.phase == LinkoConnectionPhase.Securing || state.phase == LinkoConnectionPhase.Routing -> "FINDING PATH"
        else -> "RECONNECTING"
    }
    val title = when {
        state.phase == LinkoConnectionPhase.Failed && directPathFailure -> "Direct path failed"
        state.phase == LinkoConnectionPhase.Failed -> "Reconnect failed"
        state.phase == LinkoConnectionPhase.Signaling -> "Negotiating with $peer"
        state.phase == LinkoConnectionPhase.Establishing || state.phase == LinkoConnectionPhase.Securing || state.phase == LinkoConnectionPhase.Routing -> "Finding a direct path to $peer"
        state.phase == LinkoConnectionPhase.Connected -> "Connected to $peer"
        else -> "Reconnecting to $peer"
    }
    val message = when {
        state.phase == LinkoConnectionPhase.Failed && directPathFailure -> "LINKO could not establish a usable direct path with $peer. Check both phones' internet and try again."
        state.phase == LinkoConnectionPhase.Failed -> "LINKO could not restore the connection with $peer."
        state.phase == LinkoConnectionPhase.Signaling -> "Exchanging fresh connection information with the peer."
        state.phase == LinkoConnectionPhase.Establishing || state.phase == LinkoConnectionPhase.Securing || state.phase == LinkoConnectionPhase.Routing -> "Checking candidates and validating the secure direct transport."
        else -> state.detail
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ring(
            color,
            190.dp,
            pulse = state.phase != LinkoConnectionPhase.Connected && state.phase != LinkoConnectionPhase.Failed,
            fast = state.phase != LinkoConnectionPhase.Failed,
            label = label,
            incomingFlow = state.phase != LinkoConnectionPhase.Failed,
        )
        Spacer(Modifier.height(22.dp))
        Text(title, color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = if (state.phase == LinkoConnectionPhase.Failed) Red else TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)

        if (state.phase != LinkoConnectionPhase.Idle) {
            Spacer(Modifier.height(14.dp))
            LinkoCard {
                Text("PEER", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(peer, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                state.peerLinkoId?.let { id ->
                    Spacer(Modifier.height(3.dp))
                    Text("@${id.removePrefix("@")}", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                }
                if (state.phase == LinkoConnectionPhase.Failed) {
                    Spacer(Modifier.height(8.dp))
                    Text((state.error ?: state.detail).replace('_', ' '), color = Red, fontSize = 10.sp, fontFamily = JetBrainsMono)
                }
            }
        }

        if (state.phase == LinkoConnectionPhase.Failed) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton("RETURN TO CONNECTION", onFailed, color = Red, outline = true)
        }
    }
}
