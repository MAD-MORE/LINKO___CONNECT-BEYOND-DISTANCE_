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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** LINKO globe/radar visualization. Home uses a calm premium ice/ocean treatment. */
@Composable
fun GlobeRadar(
    color: Color,
    size: Dp = 190.dp,
    label: String? = "ONLINE",
    fast: Boolean = false,
    incomingFlow: Boolean = false,
    idle: Boolean = false,
    iceOcean: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "globe_radar")
    val normalizedLabel = label?.uppercase()
    val readyRadar = normalizedLabel == "READY"
    val activeFast = fast || normalizedLabel == "CONNECTING" || normalizedLabel == "WAITING" ||
        normalizedLabel == "LINKING" || normalizedLabel == "APPROVED" || normalizedLabel == "SIGNALING"

    val ice = if (iceOcean) Color(0xFFF3FDFF) else Color.White
    val frost = if (iceOcean) Color(0xFFB5F2FF) else color.copy(alpha = 0.72f)
    val ocean = if (iceOcean) Color(0xFF25C7F4) else color
    val deepOcean = if (iceOcean) Color(0xFF0A6FA5) else Green
    val glass = if (iceOcean) Color(0xFF081D2A) else Color.Transparent

    val rotation by transition.animateFloat(-180f, 180f,
        infiniteRepeatable(tween(if (activeFast) 1800 else if (readyRadar) 7200 else 10000, easing = LinearEasing), RepeatMode.Restart), label = "globe_rotation")
    val sweep by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(if (activeFast) 1000 else if (readyRadar) 2400 else 3400, easing = LinearEasing), RepeatMode.Restart), label = "radar_sweep")
    val breathe by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "home_breathe")
    val flow by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(if (activeFast) 650 else 900, easing = LinearEasing), RepeatMode.Restart), label = "connection_flow")

    val outgoing = normalizedLabel == "SYNCING" || normalizedLabel == "REQUESTING" || normalizedLabel == "APPROVED" || normalizedLabel == "SIGNALING" ||
        normalizedLabel == "SDP" || normalizedLabel == "ICE GATHERING" || normalizedLabel == "ICE CHECKING" || normalizedLabel == "NOMINATING" ||
        normalizedLabel == "HANDSHAKE" || normalizedLabel == "TUNNEL" || normalizedLabel == "SHARING"
    val receiverNegotiating = incomingFlow || normalizedLabel == "CONNECTING" || normalizedLabel == "WAITING" || normalizedLabel == "LINKING"
    val connected = normalizedLabel == "CONNECTED" || normalizedLabel == "LIVE" || normalizedLabel == "ONLINE" || normalizedLabel == "SHARING"
    val flowing = outgoing || receiverNegotiating || connected

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.minDimension / 2f - 12.dp.toPx()
            val center = Offset(cx, cy)
            val pulse = if (iceOcean) (0.92f + breathe * 0.08f) else 1f

            if (iceOcean) {
                drawCircle(deepOcean.copy(alpha = 0.06f + breathe * 0.025f), radius * 1.22f, center)
                drawCircle(ocean.copy(alpha = 0.075f + breathe * 0.025f), radius * 1.10f, center)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF12394B).copy(alpha = 0.92f), glass.copy(alpha = 0.98f), Color(0xFF06131C))
                    ),
                    radius = radius * pulse,
                    center = center
                )
                drawCircle(ocean.copy(alpha = 0.38f), radius * 1.015f, center, style = Stroke(2.2.dp.toPx()))
                drawCircle(frost.copy(alpha = 0.25f), radius * 0.965f, center, style = Stroke(0.9.dp.toPx()))
            } else {
                drawCircle(deepOcean.copy(alpha = if (flowing) 0.075f else 0.045f), radius * 1.08f, center)
                drawCircle(ocean.copy(alpha = 0.13f), radius, center, style = Stroke(1.5.dp.toPx()))
                drawCircle(frost.copy(alpha = 0.22f), radius * 0.94f, center, style = Stroke(0.8.dp.toPx()))
            }

            // A restrained globe: curved meridians and latitude bands create depth without a busy grid.
            val globeRadius = radius * if (iceOcean) 0.78f else 0.94f
            floatArrayOf(-0.58f, -0.30f, 0f, 0.30f, 0.58f).forEachIndexed { index, latitude ->
                val y = cy + globeRadius * latitude
                val rx = globeRadius * (0.86f - kotlin.math.abs(latitude) * 0.34f)
                val ry = globeRadius * 0.075f
                drawOval(
                    frost.copy(alpha = if (iceOcean) 0.09f else if (index == 2) 0.25f else 0.13f),
                    topLeft = Offset(cx - rx, y - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(if (iceOcean) 0.8.dp.toPx() else 1.1.dp.toPx())
                )
            }

            intArrayOf(-58, -29, 0, 29, 58).forEach { longitude ->
                val phase = Math.toRadians((longitude + rotation * if (iceOcean) 0.55f else 1f).toDouble())
                val x = cx + globeRadius * sin(phase).toFloat()
                val squeeze = kotlin.math.abs(cos(phase)).toFloat().coerceIn(0.08f, 1f)
                val path = Path()
                path.moveTo(x, cy - globeRadius)
                path.cubicTo(cx + (x - cx) * squeeze * 0.36f, cy - globeRadius * 0.50f,
                    cx + (x - cx) * squeeze * 0.36f, cy + globeRadius * 0.50f, x, cy + globeRadius)
                drawPath(path, frost.copy(alpha = if (iceOcean) 0.10f else 0.12f), style = Stroke(if (iceOcean) 0.8.dp.toPx() else 1.1.dp.toPx(), join = StrokeJoin.Round))
            }

            if (iceOcean) {
                drawArc(ocean.copy(alpha = 0.18f), -32f + rotation * 0.08f, 145f, false,
                    Offset(cx - globeRadius, cy - globeRadius), Size(globeRadius * 2f, globeRadius * 2f), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                drawArc(frost.copy(alpha = 0.15f), 145f + rotation * 0.05f, 95f, false,
                    Offset(cx - globeRadius, cy - globeRadius), Size(globeRadius * 2f, globeRadius * 2f), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(ice.copy(alpha = 0.92f), 2.5.dp.toPx(), center)
                drawCircle(ocean.copy(alpha = 0.15f + breathe * 0.06f), 14.dp.toPx() + breathe * 3.dp.toPx(), center)
            }

            if (readyRadar && !idle) {
                val pulseRadius = radius * (0.42f + breathe * 0.48f)
                drawCircle(frost.copy(alpha = 0.10f * (1f - breathe)), pulseRadius, center, style = Stroke(1.2.dp.toPx()))
            }

            if (flowing) {
                // Directional packet arcs: sparse, small and deliberately elegant.
                repeat(if (iceOcean) 5 else 9) { index ->
                    val start = rotation * 0.45f + index * if (iceOcean) 72f else 40f
                    drawArc(ice.copy(alpha = if (iceOcean) 0.65f else 0.92f), startAngle = start,
                        sweepAngle = if (iceOcean) 17f else 25f, useCenter = false,
                        topLeft = Offset(cx - radius * 1.025f, cy - radius * 1.025f),
                        size = Size(radius * 2.05f, radius * 2.05f),
                        style = Stroke(if (iceOcean) 2.0.dp.toPx() else 2.8.dp.toPx(), cap = StrokeCap.Round))
                }

                val particles = arrayOf(0.00f to 0.00f, 0.18f to 0.52f, 0.42f to 0.91f, 0.67f to 0.33f, 0.84f to 0.76f, 0.30f to 0.16f, 0.56f to 0.58f)
                particles.forEachIndexed { index, particle ->
                    val angle = particle.second * (Math.PI * 2.0) + index * 0.37
                    val local = (flow + particle.first).let { value -> value - floor(value.toDouble()).toFloat() }
                    val inward = receiverNegotiating && !outgoing
                    val distance = if (inward) radius * (0.98f - local * 0.82f) else radius * (0.18f + local * 0.80f)
                    val px = cx + cos(angle).toFloat() * distance
                    val py = cy + sin(angle).toFloat() * distance
                    drawCircle(ocean.copy(alpha = if (iceOcean) 0.38f else 0.30f), if (iceOcean) 4.dp.toPx() else 5.dp.toPx(), Offset(px, py))
                    drawCircle(ice.copy(alpha = 0.95f), if (iceOcean) 1.9.dp.toPx() else 2.7.dp.toPx(), Offset(px, py))
                }
            } else if (!idle) {
                val sweepRad = Math.toRadians(sweep.toDouble())
                val sx = cx + radius * cos(sweepRad).toFloat()
                val sy = cy + radius * sin(sweepRad).toFloat()
                drawLine(frost.copy(alpha = if (iceOcean) 0.07f else 0.10f), center, Offset(sx, sy), if (iceOcean) 10.dp.toPx() else 11.dp.toPx(), StrokeCap.Round)
                drawLine(ice.copy(alpha = if (iceOcean) 0.82f else 0.88f), center, Offset(sx, sy), if (iceOcean) 1.7.dp.toPx() else 2.2.dp.toPx(), StrokeCap.Round)
                drawCircle(frost.copy(alpha = if (iceOcean) 0.22f else 0.30f), if (iceOcean) 4.dp.toPx() else 6.dp.toPx(), Offset(sx, sy))
                drawCircle(ice, if (iceOcean) 1.9.dp.toPx() else 2.4.dp.toPx(), Offset(sx, sy))
            }
        }

        label?.let {
            Text(
                it,
                color = if (iceOcean) ice else Color.White,
                fontSize = if (iceOcean) 8.5.sp else 9.5.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (iceOcean) 0.5.sp else 0.sp,
            )
        }
    }
}
