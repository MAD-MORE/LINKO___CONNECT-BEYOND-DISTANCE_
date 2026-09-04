package com.linkshare.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.JetBrainsMono

/** LINKO connection ring. Live states keep fast directional packet flow visible. */
@Composable
fun Ring(
    color: Color,
    size: Dp = 160.dp,
    pulse: Boolean = false,
    idle: Boolean = false,
    label: String? = null,
    incomingFlow: Boolean = false,
    fast: Boolean = pulse,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val live = label == "ONLINE" || label == "LIVE" || label == "CONNECTED"
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
            // PACKET FLOW activates the moving split-atom renderer. incomingFlow controls
            // direction, so receiver traffic moves inward while provider traffic moves outward.
            label = if (live) "PACKET FLOW" else label,
            fast = fast || live,
            incomingFlow = incomingFlow,
            idle = idle && !live,
        )
        if (live && !label.isNullOrBlank()) {
            Text(label, color = color, fontSize = 9.5.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        }
    }
}
