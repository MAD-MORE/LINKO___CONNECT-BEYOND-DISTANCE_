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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.JetBrainsMono
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * LINKO's main globe visualization.
 *
 * This is intentionally rendered as a live scene rather than a static bitmap:
 * the globe rotates, the atmosphere breathes, network routes orbit it, and
 * packets move when the connection is active.
 */
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
    val transition = rememberInfiniteTransition(label = "linko_globe")
    val state = label?.uppercase().orEmpty()
    val active = state in setOf("CONNECTED", "ONLINE", "LIVE", "SHARING")
    val negotiating = fast || state in setOf("CONNECTING", "WAITING", "LINKING", "APPROVED", "SIGNALING", "FINDING PATH", "NEGOTIATING")
    val flowing = active || incomingFlow || negotiating

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (negotiating) 4200 else 9000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "earth_rotation",
    )
    val orbitRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (flowing) 2600 else 6500, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "network_orbit",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "atmosphere",
    )
    val packets by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 700 else 1050, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "packets",
    )

    val ocean = if (iceOcean) Color(0xFF24C8F5) else color
    val cyan = Color(0xFF8DEBFF)
    val deep = Color(0xFF041521)
    val earthBlue = Color(0xFF0B466C)
    val earthBlue2 = Color(0xFF092B43)
    val land = Color(0xFF74C9B4)
    val white = Color.White

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val outer = this.size.minDimension / 2f - 5.dp.toPx()
            val globe = outer * 0.73f
            val center = Offset(cx, cy)
            val glow = 0.12f + pulse * 0.06f

            // Deep atmospheric halo and glass-like outer ring.
            drawCircle(ocean.copy(alpha = glow), outer * 1.06f, center)
            drawCircle(ocean.copy(alpha = 0.07f + pulse * 0.025f), outer * 1.16f, center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyan.copy(alpha = 0.25f),
                        ocean.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = outer * 1.17f,
                ),
                radius = outer * 1.17f,
                center = center,
            )
            drawCircle(ocean.copy(alpha = 0.65f), outer, center, style = Stroke(2.2.dp.toPx()))
            drawCircle(cyan.copy(alpha = 0.20f), outer * 0.94f, center, style = Stroke(0.8.dp.toPx()))

            // Rotating Earth body: radial lighting makes the flat canvas read as a sphere.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF5CD9FF).copy(alpha = 0.88f),
                        earthBlue.copy(alpha = 0.98f),
                        earthBlue2.copy(alpha = 0.99f),
                        deep,
                    ),
                    center = Offset(cx - globe * 0.28f, cy - globe * 0.30f),
                    radius = globe * 1.45f,
                ),
                radius = globe,
                center = center,
            )

            // Atmospheric rim.
            drawCircle(cyan.copy(alpha = 0.70f), globe, center, style = Stroke(2.4.dp.toPx()))
            drawCircle(white.copy(alpha = 0.18f), globe * 0.965f, center, style = Stroke(1.dp.toPx()))

            // Simplified rotating continental silhouettes. They intentionally stay sparse so
            // the network remains the visual focus while still reading as Earth.
            val landScale = globe / 100f
            val continents = listOf(
                listOf(-58f to -12f, -40f to -35f, -18f to -25f, -12f to 5f, -28f to 15f, -50f to 4f),
                listOf(8f to -45f, 28f to -50f, 48f to -30f, 42f to -5f, 18f to 2f, 2f to -18f),
                listOf(30f to 8f, 52f to 10f, 62f to 28f, 42f to 45f, 18f to 32f, 22f to 15f),
                listOf(-5f to 35f, 14f to 30f, 20f to 58f, 5f to 72f, -12f to 58f, -20f to 42f),
            )
            continents.forEachIndexed { index, points ->
                val path = Path()
                points.forEachIndexed { pointIndex, point ->
                    val lon = Math.toRadians((point.first + rotation * 0.72f + index * 8f).toDouble())
                    val x3 = sin(lon).toFloat()
                    val visible = abs(x3) < 0.96f
                    if (!visible) return@forEachIndexed
                    val x = cx + point.first * landScale * 0.68f
                    val y = cy + point.second * landScale * 0.68f
                    val rotatedX = cx + (x - cx) * cos(Math.toRadians(rotation.toDouble())).toFloat()
                    val finalX = if (abs(cos(Math.toRadians(rotation.toDouble())).toFloat()) < 0.08f) cx else rotatedX
                    if (pointIndex == 0) path.moveTo(finalX, y) else path.lineTo(finalX, y)
                }
                path.close()
                drawPath(path, land.copy(alpha = 0.38f), Stroke(1.1.dp.toPx(), join = StrokeJoin.Round))
            }

            // Longitude/latitude curves give the Earth its spherical geometry.
            for (i in -4..4) {
                val longitude = i * 22f + rotation
                val rad = Math.toRadians(longitude.toDouble())
                val squeeze = abs(cos(rad)).toFloat().coerceIn(0.05f, 1f)
                val path = Path()
                path.moveTo(cx, cy - globe)
                path.cubicTo(
                    cx + globe * squeeze * 0.72f,
                    cy - globe * 0.45f,
                    cx - globe * squeeze * 0.72f,
                    cy + globe * 0.45f,
                    cx,
                    cy + globe,
                )
                drawPath(path, cyan.copy(alpha = 0.10f), Stroke(0.75.dp.toPx()))
            }
            for (i in -2..2) {
                val y = cy + i * globe * 0.27f
                val half = globe * (1f - abs(i) * 0.13f)
                drawOval(
                    cyan.copy(alpha = 0.09f),
                    Offset(cx - half, y - globe * 0.055f),
                    androidx.compose.ui.geometry.Size(half * 2f, globe * 0.11f),
                    style = Stroke(0.75.dp.toPx()),
                )
            }

            // Global network routes. Arcs are projected onto the face of the globe.
            val nodes = listOf(
                -0.62f to -0.12f,
                -0.35f to 0.34f,
                -0.02f to -0.28f,
                0.25f to 0.18f,
                0.55f to -0.20f,
                0.42f to 0.46f,
                -0.10f to 0.50f,
            )
            val routePairs = listOf(0 to 2, 2 to 4, 1 to 3, 3 to 5, 6 to 3, 0 to 1, 4 to 5)
            routePairs.forEach { (a, b) ->
                val p1 = Offset(cx + nodes[a].first * globe, cy + nodes[a].second * globe)
                val p2 = Offset(cx + nodes[b].first * globe, cy + nodes[b].second * globe)
                val midY = (p1.y + p2.y) / 2f - globe * 0.16f
                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    cubicTo(p1.x + (p2.x - p1.x) * 0.25f, midY, p2.x - (p2.x - p1.x) * 0.25f, midY, p2.x, p2.y)
                }
                drawPath(path, cyan.copy(alpha = if (flowing) 0.30f else 0.16f), Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))
            }

            // Orbiting communication bands.
            drawOval(
                ocean.copy(alpha = 0.40f),
                Offset(cx - outer * 0.86f, cy - outer * 0.30f),
                androidx.compose.ui.geometry.Size(outer * 1.72f, outer * 0.60f),
                style = Stroke(1.7.dp.toPx()),
            )
            drawArc(
                white.copy(alpha = 0.80f),
                startAngle = orbitRotation,
                sweepAngle = 28f,
                useCenter = false,
                topLeft = Offset(cx - outer * 0.86f, cy - outer * 0.30f),
                size = androidx.compose.ui.geometry.Size(outer * 1.72f, outer * 0.60f),
                style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round),
            )

            // Network nodes and moving packets.
            nodes.forEachIndexed { index, node ->
                val point = Offset(cx + node.first * globe, cy + node.second * globe)
                drawCircle(cyan.copy(alpha = 0.16f), 7.dp.toPx(), point)
                drawCircle(white.copy(alpha = if (flowing) 0.95f else 0.60f), 2.2.dp.toPx(), point)
                if (flowing && index < 5) {
                    val t = (packets + index * 0.19f).let { it - floor(it.toDouble()).toFloat() }
                    val packet = Offset(
                        point.x + cos(t * Math.PI * 2.0).toFloat() * 8.dp.toPx(),
                        point.y + sin(t * Math.PI * 2.0).toFloat() * 8.dp.toPx(),
                    )
                    drawCircle(ocean.copy(alpha = 0.45f), 4.dp.toPx(), packet)
                    drawCircle(white, 1.7.dp.toPx(), packet)
                }
            }

            // Active flow streaks around the outside make the connection readable at a glance.
            if (flowing) {
                repeat(7) { index ->
                    val start = orbitRotation + index * 51f
                    drawArc(
                        cyan.copy(alpha = 0.75f),
                        start,
                        13f,
                        false,
                        Offset(cx - outer * 0.98f, cy - outer * 0.98f),
                        androidx.compose.ui.geometry.Size(outer * 1.96f, outer * 1.96f),
                        style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            if (!idle && !flowing) {
                val angle = Math.toRadians(orbitRotation.toDouble())
                val point = Offset(cx + outer * 0.88f * cos(angle).toFloat(), cy + outer * 0.88f * sin(angle).toFloat())
                drawCircle(cyan.copy(alpha = 0.30f), 7.dp.toPx(), point)
                drawCircle(white, 2.dp.toPx(), point)
            }
        }

        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = white.copy(alpha = 0.92f),
                fontSize = 8.5.sp,
                fontFamily = JetBrainsMono,
            )
        }
    }
}
