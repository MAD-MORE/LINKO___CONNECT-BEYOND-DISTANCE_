package com.linkshare.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LINKO connection ring. Animation intensity is tied to real connection work:
 * pulse=true means negotiation is active; incomingFlow=true makes atoms travel
 * from the outer path into the receiver instead of the ring looking like a fake timer.
 */
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
            label = label,
            fast = fast,
            incomingFlow = incomingFlow,
            idle = idle,
        )
    }
}
