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

    // Stay on the live connection screen for Connected and Failed states.
    // Navigation is deliberately not triggered by state changes here; the user
    // controls STOP, RETRY and DISCONNECT from this screen.
    val activeConnection = state.phase != LinkoConnectionPhase.Idle
    val negotiating = state.phase != LinkoConnectionPhase.Idle && state.phase != LinkoConnectionPhase.Failed
    val fastAnimation = negotiating && (!health.available || health.score >= 60)
    val color = when {
        state.phase == LinkoConnectionPhase.Failed -> Red
        state.phase == LinkoConnectionPhase.Connected && health.score >= 65 -> Green
        health.score < 45 && health.available -> Red
        else -> Blue
    }
    val label = when (state.phase) {
        LinkoConnectionPhase.Connected -> "CONNECTED"
        LinkoConnectionPhase.Failed -> "LOST"
        LinkoConnectionPhase.Signaling -> "WAITING"
        LinkoConnectionPhase.Idle -> "READY"
        else -> "CONNECTING"
    }
    val title = when (state.phase) {
        LinkoConnectionPhase.Connected -> if (state.peerDisplayName.isNullOrBlank()) "Connected" else "Connected to ${state.peerDisplayName}"
        LinkoConnectionPhase.Failed -> "We couldn't connect"
        LinkoConnectionPhase.Signaling -> "Waiting for your friend"
        LinkoConnectionPhase.Idle -> "Ready to connect"
        else -> "Connecting"
    }
    val message = when {
        state.phase == LinkoConnectionPhase.Failed -> friendlyFailure(state.error ?: state.detail)
        state.phase == LinkoConnectionPhase.Connected && health.level == LinkoNetworkHealthLevel.POOR -> "Your connection is weak. LINKO is trying to keep you connected."
        state.phase == LinkoConnectionPhase.Connected && health.level == LinkoNetworkHealthLevel.WEAK -> "Connection is slowing down."
        state.phase == LinkoConnectionPhase.Connected -> "Your friend's internet is available now."
        state.phase == LinkoConnectionPhase.Signaling -> "Your friend needs to accept the request."
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

        if (state.phase == LinkoConnectionPhase.Connected) {
            Spacer(Modifier.height(18.dp))
            LinkoCard {
                Text("Live connection", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                InfoRow("Downloaded", formatBytes(state.bytesIn))
                InfoRow("Uploaded", formatBytes(state.bytesOut))
                InfoRow("Total", formatBytes(state.bytesIn + state.bytesOut))
                if (state.latencyMs > 0) InfoRow("Response time", "${state.latencyMs} ms")
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
                    PrimaryButton("CANCEL", { LinkoEngineBridge.disconnect() }, color = Red, outline = true)
                }
            }
            state.phase == LinkoConnectionPhase.Connected -> {
                PrimaryButton("DISCONNECT", { LinkoEngineBridge.disconnect() }, color = Red, outline = true)
            }
            state.phase != LinkoConnectionPhase.Idle -> {
                PrimaryButton("STOP", { LinkoEngineBridge.disconnect() }, color = Red, outline = true)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun friendlyFailure(raw: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("timeout") -> "The connection took too long. Check both phones' internet and try again."
        lower.contains("denied") || lower.contains("declined") -> "Your friend declined the connection request."
        lower.contains("offline") -> "Your friend is not available right now."
        lower.contains("permission") || lower.contains("vpn") -> "Android permission is needed before LINKO can connect."
        lower.contains("network") || lower.contains("socket") || lower.contains("unreachable") -> "The network is having trouble. Check your connection and try again."
        else -> "Something went wrong while connecting. Please try again."
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
