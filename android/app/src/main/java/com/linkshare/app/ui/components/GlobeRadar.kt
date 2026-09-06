package com.linkshare.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.JetBrainsMono
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** LINKO globe/radar visualization: cool ice/ocean cyan-blue visual language. */
@Composable
fun GlobeRadar(
    color: Color,
    size: Dp = 190.dp,
    label: String? = "ONLINE",
    fast: Boolean = false,
    incomingFlow: Boolean = false,
    idle: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "globe_radar")
    val normalizedLabel = label?.uppercase()
    val readyRadar = normalizedLabel == "READY"
    val activeFast = fast || normalizedLabel == "CONNECTING" || normalizedLabel == "WAITING" ||
        normalizedLabel == "LINKING" || normalizedLabel == "APPROVED" || normalizedLabel == "SIGNALING"

    // Ice + ocean palette: no green on the home ring.
    val ice = Color(0xFFE8FAFF)
    val frost = Color(0xFF9DEBFF)
    val ocean = Color(0xFF20BFEF)
    val deepOcean = Color(0xFF087FB8)
    val primary = ocean

    val rotation by transition.animateFloat(
        -180f, 180f,
        infiniteRepeatable(tween(if (activeFast) 1800 else if (readyRadar) 7200 else 9000, easing = LinearEasing), RepeatMode.Restart),
        label = "globe_rotation",
    )
    val sweep by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (activeFast) 900 else if (readyRadar) 1800 else 2600, easing = LinearEasing), RepeatMode.Restart),
        label = "radar_sweep",
    )
    val radarPulse by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (readyRadar) 1800 else 2600, easing = LinearEasing), RepeatMode.Restart),
        label = "radar_pulse",
    )
    val flow by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (activeFast) 650 else 900, easing = LinearEasing), RepeatMode.Restart),
        label = "connection_flow",
    )
    val fastSpin by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (activeFast) 1800 else 2400, easing = LinearEasing), RepeatMode.Restart),
        label = "fast_connection_spin",
    )

    val outgoing = normalizedLabel == "SYNCING" || normalizedLabel == "REQUESTING" ||
        normalizedLabel == "APPROVED" || normalizedLabel == "SIGNALING" ||
        normalizedLabel == "SDP" || normalizedLabel == "ICE GATHERING" || normalizedLabel == "ICE CHECKING" ||
        normalizedLabel == "NOMINATING" || normalizedLabel == "HANDSHAKE" || normalizedLabel == "TUNNEL" ||
        normalizedLabel == "SHARING"
    val receiverNegotiating = incomingFlow || normalizedLabel == "CONNECTING" || normalizedLabel == "WAITING" || normalizedLabel == "LINKING"
    val connected = normalizedLabel == "CONNECTED" || normalizedLabel == "LIVE" || normalizedLabel == "ONLINE" || normalizedLabel == "SHARING"
    val packetFlow = normalizedLabel == "PACKET FLOW" || connected
    val flowing = outgoing || receiverNegotiating || packetFlow

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.minDimension / 2f - 9.dp.toPx()
            val center = Offset(cx, cy)
            val gridStroke = 1.1.dp.toPx()

            drawCircle(deepOcean.copy(alpha = if (flowing) 0.075f else 0.045f), radius * 1.08f, center)
            drawCircle(ocean.copy(alpha = 0.13f), radius, center, style = Stroke(1.5.dp.toPx()))
            drawCircle(frost.copy(alpha = 0.22f), radius * 0.94f, center, style = Stroke(0.8.dp.toPx()))

            if (readyRadar && !idle) {
                val pulseRadius = radius * (0.42f + radarPulse * 0.58f)
                drawCircle(frost.copy(alpha = (0.16f * (1f - radarPulse)).coerceAtLeast(0f)), pulseRadius, center, style = Stroke(1.4.dp.toPx()))
            }

            floatArrayOf(-0.72f, -0.42f, -0.18f, 0.18f, 0.42f, 0.72f).forEach { latitude ->
                val y = cy + radius * latitude
                val rx = radius * sqrt((1f - latitude * latitude).coerceAtLeast(0.08f))
                val ry = radius * 0.13f
                drawOval(frost.copy(alpha = if (abs(latitude) < 0.2f) 0.25f else 0.13f), topLeft = Offset(cx - rx, y - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(gridStroke))
            }

            intArrayOf(-72, -36, 0, 36, 72).forEach { longitude ->
                val phase = Math.toRadians((longitude + rotation).toDouble())
                val x = cx + radius * sin(phase).toFloat()
                val squeeze = abs(cos(phase)).toFloat().coerceIn(0.06f, 1f)
                val path = Path()
                path.moveTo(x, cy - radius)
                path.cubicTo(cx + (x - cx) * squeeze * 0.38f, cy - radius * 0.48f, cx + (x - cx) * squeeze * 0.38f, cy + radius * 0.48f, x, cy + radius)
                drawPath(path, frost.copy(alpha = if (longitude == 0) 0.25f else 0.12f), style = Stroke(gridStroke, cap = StrokeCap.Round))
            }

            drawOval(ocean.copy(alpha = 0.26f), topLeft = Offset(cx - radius, cy - radius * 0.055f), size = Size(radius * 2f, radius * 0.11f), style = Stroke(1.2.dp.toPx()))

            val nodes = arrayOf(-0.62f to -0.18f, -0.38f to 0.42f, -0.08f to -0.52f, 0.18f to 0.20f, 0.42f to -0.30f, 0.62f to 0.18f, -0.16f to 0.64f, 0.34f to 0.62f)
            nodes.forEachIndexed { index, node ->
                val nx = cx + radius * node.first
                val ny = cy + radius * node.second
                val depth = (1f - node.first * node.first - node.second * node.second).coerceAtLeast(0.12f)
                drawCircle(ocean.copy(alpha = 0.16f * depth), 7.dp.toPx() * depth, Offset(nx, ny))
                drawCircle(ice.copy(alpha = 0.88f * depth), 2.1.dp.toPx(), Offset(nx, ny))
                if (index % 2 == 0) drawLine(frost.copy(alpha = 0.16f), center, Offset(nx, ny), 0.7.dp.toPx(), StrokeCap.Round)
            }

            if (flowing) {
                repeat(9) { index ->
                    val start = fastSpin + index * 40f
                    drawArc(ice.copy(alpha = 0.92f), startAngle = start, sweepAngle = 25f, useCenter = false, topLeft = Offset(cx - radius * 1.035f, cy - radius * 1.035f), size = Size(radius * 2.07f, radius * 2.07f), style = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round))
                }

                val particles = arrayOf(0.00f to 0.00f, 0.18f to 0.52f, 0.42f to 0.91f, 0.67f to 0.33f, 0.84f to 0.76f, 0.30f to 0.16f, 0.56f to 0.58f, 0.95f to 0.46f, 0.12f to 0.78f, 0.73f to 0.07f)
                particles.forEachIndexed { index, particle ->
                    val angle = particle.second * (Math.PI * 2.0) + index * 0.37
                    val local = (flow + particle.first).let { value -> value - floor(value.toDouble()).toFloat() }
                    val inward = receiverNegotiating && !outgoing
                    val distance = if (inward) radius * (1.02f - local * 0.90f) else radius * (0.12f + local * 0.92f)
                    val tailDistance = if (inward) distance + radius * 0.11f else distance - radius * 0.11f
                    val px = cx + cos(angle).toFloat() * distance
                    val py = cy + sin(angle).toFloat() * distance
                    val tx = cx + cos(angle).toFloat() * tailDistance
                    val ty = cy + sin(angle).toFloat() * tailDistance
                    drawLine(ocean.copy(alpha = 0.30f), Offset(tx, ty), Offset(px, py), 3.8.dp.toPx(), StrokeCap.Round)
                    drawCircle(ice.copy(alpha = 0.96f), 2.7.dp.toPx(), Offset(px, py))
                    if (inward) {
                        val side = radius * 0.035f
                        drawCircle(frost.copy(alpha = 0.62f), 1.3.dp.toPx(), Offset(px - sin(angle).toFloat() * side, py + cos(angle).toFloat() * side))
                        drawCircle(frost.copy(alpha = 0.44f), 1.1.dp.toPx(), Offset(px + sin(angle).toFloat() * side, py - cos(angle).toFloat() * side))
                    }
                }
            } else if (!idle) {
                val sweepRad = Math.toRadians(sweep.toDouble())
                val sx = cx + radius * cos(sweepRad).toFloat()
                val sy = cy + radius * sin(sweepRad).toFloat()
                drawLine(frost.copy(alpha = if (readyRadar) 0.14f else 0.10f), center, Offset(sx, sy), if (readyRadar) 13.dp.toPx() else 11.dp.toPx(), StrokeCap.Round)
                drawLine(ice.copy(alpha = if (readyRadar) 0.96f else 0.88f), center, Offset(sx, sy), 2.2.dp.toPx(), StrokeCap.Round)
                drawCircle(frost.copy(alpha = if (readyRadar) 0.36f else 0.30f), 6.dp.toPx(), Offset(sx, sy))
                drawCircle(ice, 2.4.dp.toPx(), Offset(sx, sy))
                drawArc(color = ocean.copy(alpha = if (readyRadar) 0.70f else 0.60f), startAngle = rotation - 48f, sweepAngle = 105f, useCenter = false, topLeft = Offset(cx - radius, cy - radius), size = Size(radius * 2f, radius * 2f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        label?.let { Text(it, color = ice, fontSize = 9.5.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
    }
}
