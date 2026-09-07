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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.JetBrainsMono
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * LINKO's connection ring.
 *
 * The visual is built as a layered orbital network: a soft atmosphere, multiple
 * concentric rings, a dashed outer orbit, network nodes/routes, and animated
 * packet travel. The same geometry is reused for idle, negotiating and live states.
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
    val transition = rememberInfiniteTransition(label = "linko_orbital_ring")
    val state = label?.uppercase().orEmpty()
    val active = state in setOf("CONNECTED", "ONLINE", "LIVE", "SHARING")
    val negotiating = fast || state in setOf(
        "CONNECTING", "WAITING", "LINKING", "APPROVED", "SIGNALING", "FINDING PATH", "NEGOTIATING"
    )
    val flowing = active || incomingFlow || negotiating

    val rotation by transition.animateFloat(
        0f,
        360f,
        infiniteRepeatable(
            tween(if (negotiating) 4200 else 9000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "ring_rotation",
    )
    val orbitRotation by transition.animateFloat(
        0f,
        360f,
        infiniteRepeatable(
            tween(if (flowing) 2600 else 6500, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "network_orbit",
    )
    val pulse by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "ring_pulse",
    )
    val packets by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(if (active) 700 else 1050, easing = LinearEasing), RepeatMode.Restart),
        label = "packet_flow",
    )

    // Reference-inspired LINKO palette: cyan orbital structure + green network paths.
    val cyan = if (iceOcean) Color(0xFF72E8FF) else Color(0xFF8DEBFF)
    val electricBlue = color.copy(alpha = 0.95f)
    val networkGreen = Color(0xFF4BE38A)
    val white = Color.White

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val center = Offset(cx, cy)
            val maxRadius = this.size.minDimension / 2f - 4.dp.toPx()
            val coreRadius = maxRadius * 0.66f
            val glowRadius = maxRadius * (1.08f + pulse * 0.03f)

            // Soft atmospheric glow around the entire network.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyan.copy(alpha = 0.12f + pulse * 0.035f),
                        electricBlue.copy(alpha = 0.055f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )

            // Concentric ring structure — the main visual language from the reference.
            val ringRadii = listOf(
                maxRadius * 0.98f,
                maxRadius * 0.84f,
                maxRadius * 0.68f,
                maxRadius * 0.53f,
            )
            ringRadii.forEachIndexed { index, radius ->
                val alpha = when (index) {
                    0 -> 0.78f
                    1 -> 0.42f
                    2 -> 0.22f
                    else -> 0.14f
                }
                drawCircle(
                    cyan.copy(alpha = alpha),
                    radius,
                    center,
                    style = Stroke(
                        width = if (index == 0) 1.8.dp.toPx() else 1.0.dp.toPx(),
                        pathEffect = if (index == 0) {
                            PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 8.dp.toPx()), 0f)
                        } else null,
                    ),
                )
            }

            // A second very thin halo reinforces the outer boundary without looking heavy.
            drawCircle(cyan.copy(alpha = 0.20f), maxRadius * 0.92f, center, style = Stroke(0.8.dp.toPx()))

            // Rotating elliptical orbit gives the ring a 3D / planetary feel.
            val orbitWidth = maxRadius * 1.78f
            val orbitHeight = maxRadius * 0.66f
            val orbitTopLeft = Offset(cx - orbitWidth / 2f, cy - orbitHeight / 2f)
            drawOval(
                cyan.copy(alpha = 0.30f),
                orbitTopLeft,
                androidx.compose.ui.geometry.Size(orbitWidth, orbitHeight),
                style = Stroke(1.2.dp.toPx()),
            )
            drawArc(
                cyan.copy(alpha = 0.95f),
                orbitRotation,
                32f,
                false,
                orbitTopLeft,
                androidx.compose.ui.geometry.Size(orbitWidth, orbitHeight),
                style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round),
            )

            val tiltedOrbitTopLeft = Offset(cx - maxRadius * 0.78f, cy - maxRadius * 0.78f)
            val tiltedOrbitSize = androidx.compose.ui.geometry.Size(maxRadius * 1.56f, maxRadius * 1.56f)
            drawArc(
                networkGreen.copy(alpha = if (flowing) 0.48f else 0.24f),
                -28f + rotation * 0.55f,
                205f,
                false,
                tiltedOrbitTopLeft,
                tiltedOrbitSize,
                style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round),
            )

            // Subtle globe lattice stays inside the ring so the existing LINKO globe identity remains.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyan.copy(alpha = 0.18f),
                        color.copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                    center = Offset(cx - coreRadius * 0.22f, cy - coreRadius * 0.28f),
                    radius = coreRadius * 1.35f,
                ),
                radius = coreRadius * 0.82f,
                center = center,
            )
            drawCircle(cyan.copy(alpha = 0.56f), coreRadius * 0.82f, center, style = Stroke(1.3.dp.toPx()))

            // Latitude / longitude curves provide depth without overpowering the network shell.
            for (i in -2..2) {
                val longitude = rotation + i * 36f
                val rad = Math.toRadians(longitude.toDouble())
                val squeeze = kotlin.math.abs(cos(rad)).toFloat().coerceIn(0.12f, 1f)
                val path = Path()
                path.moveTo(cx, cy - coreRadius * 0.82f)
                path.cubicTo(
                    cx + coreRadius * squeeze * 0.72f,
                    cy - coreRadius * 0.44f,
                    cx - coreRadius * squeeze * 0.72f,
                    cy + coreRadius * 0.44f,
                    cx,
                    cy + coreRadius * 0.82f,
                )
                drawPath(path, cyan.copy(alpha = 0.075f), style = Stroke(0.7.dp.toPx()))
            }
            for (i in -1..1) {
                val y = cy + i * coreRadius * 0.27f
                val half = coreRadius * (0.90f - kotlin.math.abs(i) * 0.12f)
                drawOval(
                    cyan.copy(alpha = 0.07f),
                    Offset(cx - half, y - coreRadius * 0.035f),
                    androidx.compose.ui.geometry.Size(half * 2f, coreRadius * 0.07f),
                    style = Stroke(0.7.dp.toPx()),
                )
            }

            // Network nodes sit across the inner rings, matching the reference structure.
            val nodes = listOf(
                -0.70f to -0.08f,
                -0.43f to 0.34f,
                -0.15f to -0.48f,
                0.22f to -0.34f,
                0.63f to -0.02f,
                0.42f to 0.44f,
                -0.02f to 0.51f,
            )
            val routePairs = listOf(
                0 to 2,
                2 to 3,
                3 to 4,
                1 to 3,
                1 to 6,
                6 to 5,
                5 to 4,
                0 to 1,
            )

            routePairs.forEach { (a, b) ->
                val p1 = Offset(cx + nodes[a].first * coreRadius, cy + nodes[a].second * coreRadius)
                val p2 = Offset(cx + nodes[b].first * coreRadius, cy + nodes[b].second * coreRadius)
                val bend = coreRadius * 0.15f
                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    cubicTo(
                        p1.x + (p2.x - p1.x) * 0.28f,
                        (p1.y + p2.y) / 2f - bend,
                        p2.x - (p2.x - p1.x) * 0.28f,
                        (p1.y + p2.y) / 2f + bend,
                        p2.x,
                        p2.y,
                    )
                }
                drawPath(
                    path,
                    networkGreen.copy(alpha = if (flowing) 0.60f else 0.34f),
                    style = Stroke(1.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // Moving packet riding the same route.
                if (flowing) {
                    val t = packets + a * 0.13f + b * 0.07f
                    val p = t - floor(t.toDouble()).toFloat()
                    val x = p1.x + (p2.x - p1.x) * p
                    val y = p1.y + (p2.y - p1.y) * p
                    drawCircle(cyan.copy(alpha = 0.28f), 5.dp.toPx(), Offset(x, y))
                    drawCircle(white, 1.8.dp.toPx(), Offset(x, y))
                }
            }

            nodes.forEachIndexed { index, node ->
                val point = Offset(cx + node.first * coreRadius, cy + node.second * coreRadius)
                val glow = if (index % 2 == 0) networkGreen else cyan
                drawCircle(glow.copy(alpha = 0.12f + pulse * 0.04f), 8.dp.toPx(), point)
                drawCircle(glow.copy(alpha = if (flowing) 0.95f else 0.70f), 3.3.dp.toPx(), point)
                drawCircle(white.copy(alpha = 0.90f), 1.35.dp.toPx(), point)
            }

            // A few orbit markers sit on the outer ring, like the reference's satellite points.
            val orbitNodes = listOf(-38f, 56f, 145f, 218f)
            orbitNodes.forEachIndexed { index, degrees ->
                val theta = Math.toRadians((degrees + rotation * 0.30f).toDouble())
                val r = maxRadius * 0.84f
                val point = Offset(
                    cx + cos(theta).toFloat() * r,
                    cy + sin(theta).toFloat() * r,
                )
                drawCircle(cyan.copy(alpha = 0.11f), 9.dp.toPx(), point)
                drawCircle(
                    if (index % 2 == 0) networkGreen.copy(alpha = 0.85f) else electricBlue,
                    3.dp.toPx(),
                    point,
                )
            }

            // Active pulse arcs — stronger when a connection is being negotiated or carrying traffic.
            if (flowing) {
                repeat(6) { index ->
                    drawArc(
                        cyan.copy(alpha = 0.68f),
                        orbitRotation + index * 60f,
                        9f,
                        false,
                        Offset(cx - maxRadius * 0.99f, cy - maxRadius * 0.99f),
                        androidx.compose.ui.geometry.Size(maxRadius * 1.98f, maxRadius * 1.98f),
                        style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }

        if (!label.isNullOrBlank()) {
            Text(
                label,
                color = white.copy(alpha = 0.94f),
                fontSize = 8.5.sp,
                fontFamily = JetBrainsMono,
            )
        }
    }
}
