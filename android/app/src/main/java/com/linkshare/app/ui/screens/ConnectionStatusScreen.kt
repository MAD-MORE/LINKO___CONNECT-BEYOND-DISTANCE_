package com.linkshare.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.components.InfoRow
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.BG
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.Surface
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow

@Composable
fun ConnectionStatusScreen(
    onConnected: () -> Unit,
    onFailed: () -> Unit,
) {
    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase == LinkoConnectionPhase.Connected) onConnected()
    }

    val color = when (state.phase) {
        LinkoConnectionPhase.Connected -> Green
        LinkoConnectionPhase.Failed -> Red
        LinkoConnectionPhase.Idle -> TextSub
        else -> Blue
    }

    val stepIndex = when (state.phase) {
        LinkoConnectionPhase.Idle -> 0
        LinkoConnectionPhase.Connecting -> 1
        LinkoConnectionPhase.Authenticating, LinkoConnectionPhase.Signaling -> 2
        LinkoConnectionPhase.Establishing, LinkoConnectionPhase.Securing -> 3
        LinkoConnectionPhase.Routing, LinkoConnectionPhase.Connected -> 4
        LinkoConnectionPhase.Failed -> -1
    }

    val detailedExplanation = when (state.phase) {
        LinkoConnectionPhase.Connecting -> "Contacting your friend's phone over the secure LINKO control plane…"
        LinkoConnectionPhase.Authenticating -> "Verifying cryptographic device credentials with Supabase…"
        LinkoConnectionPhase.Signaling -> "Sending connection request — waiting for your friend to tap Accept…"
        LinkoConnectionPhase.Establishing -> "Friend accepted! Setting up AES-256-GCM encrypted UDP data tunnel…"
        LinkoConnectionPhase.Securing -> "Authenticating tunnel headers and verifying anti-replay sequence keys…"
        LinkoConnectionPhase.Routing -> "Configuring Android VPN interface (10.48.0.2) to route your apps through friend's network…"
        LinkoConnectionPhase.Connected -> "Connected! All device internet traffic is now protected and sharing your friend's connection."
        LinkoConnectionPhase.Failed -> resolveFriendlyErrorMessage(state.error ?: state.detail)
        LinkoConnectionPhase.Idle -> "Ready to connect to a friend."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Ring(
                color,
                160.dp,
                pulse = state.phase != LinkoConnectionPhase.Idle && state.phase != LinkoConnectionPhase.Connected && state.phase != LinkoConnectionPhase.Failed,
                label = when (state.phase) {
                    LinkoConnectionPhase.Connected -> "LIVE"
                    LinkoConnectionPhase.Failed -> "STOPPED"
                    LinkoConnectionPhase.Signaling -> "WAITING"
                    else -> "SYNCING"
                }
            )

            Spacer(Modifier.height(20.dp))
            Text(
                when (state.phase) {
                    LinkoConnectionPhase.Connected -> "TUNNEL CONNECTED"
                    LinkoConnectionPhase.Failed -> "CONNECTION STOPPED"
                    LinkoConnectionPhase.Signaling -> "WAITING FOR FRIEND"
                    else -> "ESTABLISHING CONNECTION"
                },
                color = TextPrimary,
                fontSize = 18.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                detailedExplanation,
                color = if (state.phase == LinkoConnectionPhase.Failed) Red else TextSub,
                fontSize = 12.sp,
                fontFamily = JetBrainsMono,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // Live Connection Steps Progress Card
        if (state.phase != LinkoConnectionPhase.Failed) {
            LinkoCard {
                Text("CONNECTION PIPELINE", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                StepRow("1. Device Discovery", stepIndex >= 1, stepIndex == 1)
                Spacer(Modifier.height(8.dp))
                StepRow("2. Friend Authorization", stepIndex >= 2, stepIndex == 2)
                Spacer(Modifier.height(8.dp))
                StepRow("3. AES-256-GCM Key Exchange", stepIndex >= 3, stepIndex == 3)
                Spacer(Modifier.height(8.dp))
                StepRow("4. VPN Packet Routing", stepIndex >= 4, stepIndex == 4)
            }
        } else {
            LinkoCard {
                Text("WHAT WENT WRONG?", color = Red, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(detailedExplanation, color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(8.dp))
                Text("What you can do: Ask your friend to open LINKO and verify 'Share My Network' is running, then tap Retry below.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
            }
        }

        // Action Buttons
        Column(Modifier.fillMaxWidth()) {
            if (state.phase == LinkoConnectionPhase.Failed) {
                PrimaryButton("RETRY CONNECTION", { LinkoEngineBridge.reconnect() }, color = Blue)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("RETURN TO FRIENDS", onFailed, outline = true)
            } else {
                PrimaryButton(
                    "CANCEL CONNECTION",
                    {
                        LinkoEngineBridge.disconnect()
                        onFailed()
                    },
                    color = Red,
                    outline = true
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepRow(title: String, completed: Boolean, inProgress: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    when {
                        completed && !inProgress -> Green.copy(alpha = 0.2f)
                        inProgress -> Blue.copy(alpha = 0.2f)
                        else -> Surface
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (inProgress) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Blue, strokeWidth = 2.dp)
            } else {
                Text(
                    if (completed) "✓" else "·",
                    color = if (completed) Green else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = when {
                completed && !inProgress -> Green
                inProgress -> Blue
                else -> TextMuted
            },
            fontSize = 12.sp,
            fontFamily = JetBrainsMono,
            fontWeight = if (inProgress || completed) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun resolveFriendlyErrorMessage(raw: String): String {
    val clean = raw.lowercase()
    return when {
        clean.contains("provider_offline") -> "Your friend is currently offline or not sharing. Ask them to open LINKO and tap 'Share My Network'."
        clean.contains("denied") -> "Your friend declined the connection request."
        clean.contains("expired") || clean.contains("timeout") -> "The connection request timed out. Make sure your friend's phone has LINKO open."
        clean.contains("auth_required") || clean.contains("session") -> "Account authentication expired. Please sign in again."
        clean.contains("internet") || clean.contains("network") -> "Network issue: please check that your device has mobile data or Wi-Fi turned on."
        clean.contains("vpn") -> "Android VPN permission is required to route your connection."
        else -> "Could not connect (${raw.replace('_', ' ')}). Please verify your friend is sharing and try again."
    }
}
