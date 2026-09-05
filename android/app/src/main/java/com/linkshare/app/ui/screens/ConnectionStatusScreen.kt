package com.linkshare.app.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoConnectionLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.components.InfoRow
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoNetworkHealthLevel
import com.linkshare.app.ui.components.LinkoNetworkHealthMonitor
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub

@Composable
fun ConnectionStatusScreen(onConnected: () -> Unit = {}, onFailed: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    val health by LinkoNetworkHealthMonitor.snapshot.collectAsStateWithLifecycle()
    var vpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vpnGranted = result.resultCode == Activity.RESULT_OK || VpnService.prepare(context) == null
    }

    LaunchedEffect(Unit) {
        LinkoNetworkHealthMonitor.start(context)
        VpnService.prepare(context)?.let(vpnLauncher::launch) ?: run { vpnGranted = true }
    }

    // Stay on this screen after connection. This is the receiver's actual live session UI.
    // Navigation away here previously caused the receiver to land on the generic LIVE/ONLINE view.

    val rawReason = (state.error ?: state.detail).trim()
    val failureKind = failureKind(rawReason)
    val activeConnection = state.phase != LinkoConnectionPhase.Idle
    val pathNegotiating = state.phase == LinkoConnectionPhase.Establishing || state.phase == LinkoConnectionPhase.Securing || state.phase == LinkoConnectionPhase.Routing
    val negotiating = activeConnection && state.phase != LinkoConnectionPhase.Failed
    val fastAnimation = negotiating && (!health.available || health.score >= 60)
    val color = when {
        state.phase == LinkoConnectionPhase.Failed -> Red
        state.phase == LinkoConnectionPhase.Connected && health.score >= 65 -> Green
        health.score < 45 && health.available -> Red
        else -> Blue
    }
    val label = when {
        state.phase == LinkoConnectionPhase.Connected -> "CONNECTED"
        state.phase == LinkoConnectionPhase.Failed -> "FAILED"
        pathNegotiating -> "FINDING PATH"
        state.phase == LinkoConnectionPhase.Signaling -> "NEGOTIATING"
        state.phase == LinkoConnectionPhase.Idle -> "READY"
        else -> "CONNECTING"
    }
    val peer = state.peerDisplayName?.takeIf { it.isNotBlank() } ?: state.peerLinkoId?.takeIf { it.isNotBlank() } ?: "LINKO peer"
    val title = when {
        state.phase == LinkoConnectionPhase.Connected -> "Connected to $peer"
        state.phase == LinkoConnectionPhase.Failed && failureKind == FailureKind.DIRECT_PATH -> "Direct path failed"
        state.phase == LinkoConnectionPhase.Failed && failureKind == FailureKind.NEGOTIATION -> "Negotiation failed"
        state.phase == LinkoConnectionPhase.Failed -> "We couldn't connect to $peer"
        pathNegotiating -> "Finding a direct path to $peer"
        state.phase == LinkoConnectionPhase.Signaling -> "Negotiating with $peer"
        state.phase == LinkoConnectionPhase.Idle -> "Ready to connect"
        else -> "Connecting to $peer"
    }
    val message = when {
        state.phase == LinkoConnectionPhase.Failed && failureKind == FailureKind.DIRECT_PATH -> "LINKO could not establish a usable direct UDP path with this peer. Check both phones' internet and try again."
        state.phase == LinkoConnectionPhase.Failed && failureKind == FailureKind.NEGOTIATION -> "LINKO could not complete direct negotiation with this peer. Make sure both phones are online and try again."
        state.phase == LinkoConnectionPhase.Failed -> friendlyFailure(rawReason, peer)
        pathNegotiating -> "Checking candidate paths, validating the peer, and establishing the secure tunnel."
        state.phase == LinkoConnectionPhase.Connected && health.level == LinkoNetworkHealthLevel.POOR -> "Your connection is weak. LINKO is trying to keep you connected."
        state.phase == LinkoConnectionPhase.Connected && health.level == LinkoNetworkHealthLevel.WEAK -> "Connection is slowing down."
        state.phase == LinkoConnectionPhase.Connected -> "Your friend's internet is available now."
        state.phase == LinkoConnectionPhase.Signaling -> "Waiting for the peer to accept and exchange direct connection information."
        state.phase == LinkoConnectionPhase.Idle -> "Choose a friend and we'll handle the connection for you."
        else -> "We're working on the connection."
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Ring(
            color = color,
            size = 170.dp,
            idle = !activeConnection,
            label = label,
            pulse = negotiating,
            fast = fastAnimation,
            incomingFlow = negotiating || state.phase == LinkoConnectionPhase.Connected,
        )
        Spacer(Modifier.height(18.dp))
        Text(title, color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text(message, color = if (state.phase == LinkoConnectionPhase.Failed) Red else TextSub, fontSize = 12.sp, textAlign = TextAlign.Center)

        if (state.phase != LinkoConnectionPhase.Idle) {
            Spacer(Modifier.height(12.dp))
            LinkoCard {
                Text("CONNECTED PEER", color = if (state.phase == LinkoConnectionPhase.Connected) Green else TextSub, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(peer, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                state.peerLinkoId?.takeIf { it.isNotBlank() }?.let { id ->
                    Spacer(Modifier.height(3.dp))
                    Text("@${id.removePrefix("@")}", color = TextSub, fontSize = 11.sp)
                }
                state.sessionId?.let { session ->
                    Spacer(Modifier.height(3.dp))
                    Text("Session ${session.take(8)}…", color = TextSub, fontSize = 10.sp)
                }
                if (state.phase == LinkoConnectionPhase.Failed && rawReason.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Reason", color = TextSub, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(rawReason.replace('_', ' '), color = Red, fontSize = 10.sp)
                }
            }
        }

        if (state.phase == LinkoConnectionPhase.Connected) {
            Spacer(Modifier.height(12.dp))
            LinkoCard {
                Text("LIVE USAGE", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Real-time traffic through the friend's connection", color = TextSub, fontSize = 10.sp)
                Spacer(Modifier.height(9.dp))
                InfoRow("DOWNLOADED", formatBytes(state.bytesIn), "Internet received by this receiver", Blue, true)
                Spacer(Modifier.height(7.dp))
                InfoRow("UPLOADED", formatBytes(state.bytesOut), "Traffic sent through the provider", Green, true)
                Spacer(Modifier.height(7.dp))
                InfoRow("TOTAL USAGE", formatBytes(state.bytesIn + state.bytesOut), "Combined session traffic", TextPrimary, true)
                if (state.latencyMs > 0) {
                    Spacer(Modifier.height(7.dp))
                    InfoRow("LATENCY", "${state.latencyMs} ms", "Live response time to provider", Blue)
                }
            }
            Spacer(Modifier.height(10.dp))
            LinkoCard {
                Text("CONNECTION ACTIVE", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Your phone is currently using the provider's internet connection. Keep this screen open to monitor live usage.", color = TextSub, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        if (!vpnGranted) {
            Spacer(Modifier.height(14.dp))
            LinkoCard {
                Text("One permission is needed", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Android needs permission before LINKO can share the connection with your apps.", color = TextSub, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("ALLOW", { VpnService.prepare(context)?.let(vpnLauncher::launch) }, color = Blue)
            }
        }

        Spacer(Modifier.height(22.dp))
        when {
            state.phase == LinkoConnectionPhase.Failed -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("TRY AGAIN", { LinkoEngineBridge.reconnect() }, color = Blue)
                    PrimaryButton("CANCEL", { LinkoConnectionLifecycle.stop(context) }, color = Red, outline = true)
                }
            }
            state.phase == LinkoConnectionPhase.Connected -> {
                PrimaryButton("DISCONNECT", { LinkoConnectionLifecycle.stop(context) }, color = Red, outline = true)
            }
            state.phase != LinkoConnectionPhase.Idle -> {
                PrimaryButton("STOP", { LinkoConnectionLifecycle.stop(context) }, color = Red, outline = true)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private enum class FailureKind { NEGOTIATION, DIRECT_PATH, OTHER }

private fun failureKind(raw: String): FailureKind {
    val lower = raw.lowercase()
    return when {
        lower.contains("no_local_udp_candidate") || lower.contains("direct_check") || lower.contains("ice_check") ||
            lower.contains("candidate") || lower.contains("nomination") || lower.contains("direct_path") ||
            lower.contains("direct_receive") || lower.contains("direct_data_plane") || lower.contains("unreachable") -> FailureKind.DIRECT_PATH
        lower.contains("signaling") || lower.contains("offer") || lower.contains("answer") ||
            lower.contains("negotiat") || lower.contains("ice") -> FailureKind.NEGOTIATION
        else -> FailureKind.OTHER
    }
}

private fun friendlyFailure(raw: String, peer: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("timeout") -> "The connection to $peer took too long. Check both phones' internet and try again."
        lower.contains("denied") || lower.contains("declined") -> "$peer declined the connection request."
        lower.contains("offline") -> "$peer is not available right now."
        lower.contains("permission") || lower.contains("vpn") -> "Android permission is needed before LINKO can connect."
        lower.contains("network") || lower.contains("socket") -> "The network is having trouble reaching $peer. Check your connection and try again."
        else -> "Something went wrong while connecting to $peer. Please try again."
    }
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value < 1024 -> "$value B"
        value < 1024 * 1024 -> "${value / 1024} KB"
        value < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", value / 1024.0 / 1024.0)
        else -> String.format(java.util.Locale.US, "%.2f GB", value / 1024.0 / 1024.0 / 1024.0)
    }
}

// Final connected-session UI is intentionally kept on this screen so the receiver sees live state and usage.