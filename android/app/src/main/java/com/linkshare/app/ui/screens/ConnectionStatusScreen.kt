package com.linkshare.app.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import androidx.compose.ui.graphics.Brush
import com.linkshare.app.ui.components.GlassCard
import com.linkshare.app.ui.components.InfoRow
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.*

@Composable
fun ConnectionStatusScreen(
    onConnected: () -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    var vpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vpnGranted = result.resultCode == Activity.RESULT_OK || VpnService.prepare(context) == null
    }

    LaunchedEffect(Unit) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnLauncher.launch(prepareIntent)
        } else {
            vpnGranted = true
        }
    }

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
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.07f), GradientMid),
                    radius = 900f
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                lineHeight = 18.sp
            )

            if (!vpnGranted) {
                Spacer(Modifier.height(14.dp))
                LinkoCard {
                    Text("VPN PERMISSION REQUIRED", color = Yellow, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Android requires one-time VPN permission to route device internet traffic through your friend's connection.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("ALLOW VPN PERMISSION", {
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent != null) vpnLauncher.launch(prepareIntent)
                        else vpnGranted = true
                    }, color = Blue)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Step-by-step 4-Phase Progress Checklist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "CONNECTION PIPELINE",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.15.sp
                )

                PipelineStepRow(
                    stepNumber = 1,
                    title = "Device Discovery",
                    subtitle = "Finding friend on LINKO network",
                    currentStep = stepIndex,
                    stepTarget = 1
                )

                PipelineStepRow(
                    stepNumber = 2,
                    title = "Friend Authorization",
                    subtitle = "Waiting for friend approval",
                    currentStep = stepIndex,
                    stepTarget = 2
                )

                PipelineStepRow(
                    stepNumber = 3,
                    title = "AES-256-GCM Key Exchange",
                    subtitle = "Securing zero-knowledge data tunnel",
                    currentStep = stepIndex,
                    stepTarget = 3
                )

                PipelineStepRow(
                    stepNumber = 4,
                    title = "VPN Packet Routing",
                    subtitle = "Piping Android IP traffic through tunnel",
                    currentStep = stepIndex,
                    stepTarget = 4
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.phase == LinkoConnectionPhase.Failed) {
                PrimaryButton(
                    label = "RETRY CONNECTION",
                    onClick = {
                        LinkoEngineBridge.disconnect()
                        onFailed()
                    },
                    color = Blue
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    label = "CANCEL",
                    onClick = {
                        LinkoEngineBridge.disconnect()
                        onFailed()
                    },
                    color = Red,
                    outline = true
                )
            } else {
                PrimaryButton(
                    label = "CANCEL REQUEST",
                    onClick = {
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
private fun PipelineStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    currentStep: Int,
    stepTarget: Int
) {
    val isComplete = currentStep > stepTarget
    val isActive = currentStep == stepTarget
    val isFailed = currentStep == -1

    val iconColor = when {
        isComplete -> Green
        isActive -> Blue
        isFailed -> TextMuted
        else -> TextMuted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = if (isActive || isComplete) 0.2f else 0.1f))
        ) {
            if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Blue,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isComplete) "✓" else stepNumber.toString(),
                    color = iconColor,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (isActive || isComplete) TextPrimary else TextMuted,
                fontSize = 13.sp,
                fontFamily = JetBrainsMono,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                subtitle,
                color = if (isActive) Blue else TextSub,
                fontSize = 10.sp,
                fontFamily = JetBrainsMono
            )
        }
    }
}

private fun resolveFriendlyErrorMessage(raw: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("timeout") || lower.contains("timed out") ->
            "The connection request timed out. Make sure your friend's phone has LINKO open and screen turned on."
        lower.contains("denied") || lower.contains("rejected") || lower.contains("declined") ->
            "Your friend declined the connection request."
        lower.contains("offline") || lower.contains("not_sharing") || lower.contains("provider_not_ready") ->
            "Your friend is currently offline or not sharing. Ask them to open LINKO and tap 'Share My Network'."
        lower.contains("permission") || lower.contains("vpn") ->
            "Android requires VPN permission to route device internet traffic. Tap Allow to continue."
        lower.contains("network") || lower.contains("unreachable") || lower.contains("socket") ->
            "Network issue: please check that your device has mobile data or Wi-Fi turned on."
        else ->
            "Unable to connect: ${raw.replace('_', ' ')}. Please try again."
    }
}
