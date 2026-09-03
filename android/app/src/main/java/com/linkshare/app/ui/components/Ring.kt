package com.linkshare.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linkshare.app.network.ConnectionStage
import com.linkshare.app.network.LinkoConnectionDiagnostics

/** Unified LINKO visual ring. Its status label follows the real transport stage. */
@Composable
fun Ring(
    color: Color,
    size: Dp = 160.dp,
    pulse: Boolean = false,
    idle: Boolean = false,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val diagnostics by LinkoConnectionDiagnostics.snapshot.collectAsState()
    val diagnosticLabel = when (diagnostics.stage) {
        ConnectionStage.REQUESTING -> "REQUESTING"
        ConnectionStage.APPROVING -> "APPROVED"
        ConnectionStage.SIGNALING -> "SIGNALING"
        ConnectionStage.SDP_NEGOTIATION -> "SDP"
        ConnectionStage.ICE_GATHERING -> "ICE GATHERING"
        ConnectionStage.ICE_CHECKING -> "ICE CHECKING"
        ConnectionStage.NOMINATING -> "NOMINATING"
        ConnectionStage.HANDSHAKE -> "HANDSHAKE"
        ConnectionStage.TUNNEL_STARTING -> "TUNNEL"
        ConnectionStage.PACKET_FLOW -> "PACKET FLOW"
        ConnectionStage.CONNECTED -> "CONNECTED"
        ConnectionStage.FAILED -> diagnostics.failureReason
            ?.uppercase()
            ?.replace('_', ' ')
            ?.take(18)
            ?: "FAILED"
    }
    val effectiveLabel = if (diagnostics.headline != "Ready" || diagnostics.stage != ConnectionStage.REQUESTING) diagnosticLabel else label

    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlobeRadar(
            color = color,
            size = size,
            label = effectiveLabel,
        )
    }
}
