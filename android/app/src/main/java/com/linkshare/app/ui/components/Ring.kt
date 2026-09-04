package com.linkshare.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Ring(
    color: Color,
    size: Dp = 160.dp,
    pulse: Boolean = false,
    idle: Boolean = false,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val health by LinkoNetworkHealthMonitor.snapshot.collectAsState()
    val duration = health.ringDurationMs()
    val transition = rememberInfiniteTransition(label = "linko_connection_ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_rotation",
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (duration * 0.8f).toInt().coerceAtLeast(300), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ring_pulse",
    )
    Box(
        Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size).alpha(if (idle) 0.65f else 1f)) {
            val radius = this.size.minDimension / 2f - 9.dp.toPx()
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(color.copy(alpha = 0.08f), radius * pulseScale, center)
            drawCircle(color.copy(alpha = 0.22f), radius, center, style = Stroke(2.dp.toPx()))
            if (!idle) {
                drawArc(
                    color = color,
                    startAngle = rotation - 80f,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                )
                val theta = Math.toRadians(rotation.toDouble())
                val dot = Offset(center.x + cos(theta).toFloat() * radius, center.y + sin(theta).toFloat() * radius)
                drawCircle(color, 5.dp.toPx(), dot)
                drawCircle(color.copy(alpha = 0.22f), 11.dp.toPx(), dot)
            }
        }
        label?.let { Text(it, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}
