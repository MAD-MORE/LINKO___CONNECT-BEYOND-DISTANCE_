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
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GlobeRadar(color: Color, size: Dp = 190.dp, label: String? = "ONLINE") {
    val transition = rememberInfiniteTransition(label = "globe_radar")
    val rotation by transition.animateFloat(-180f, 180f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "globe_rotation")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "radar_sweep")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.minDimension / 2f - 9.dp.toPx()
            val center = Offset(cx, cy)
            val gridStroke = 1.1.dp.toPx()

            // Atmospheric shell and polished spherical rim.
            drawCircle(color.copy(alpha = 0.045f), radius * 1.08f, center)
            drawCircle(color.copy(alpha = 0.10f), radius, center, style = Stroke(1.5.dp.toPx()))
            drawCircle(color.copy(alpha = 0.20f), radius * 0.94f, center, style = Stroke(0.8.dp.toPx()))

            // Curved latitude lines create the 3D globe depth.
            floatArrayOf(-0.72f, -0.42f, -0.18f, 0.18f, 0.42f, 0.72f).forEach { latitude ->
                val y = cy + radius * latitude
                val rx = radius * sqrt((1f - latitude * latitude).coerceAtLeast(0.08f))
                val ry = radius * 0.13f
                drawOval(
                    color.copy(alpha = if (abs(latitude) < 0.2f) 0.27f else 0.15f),
                    topLeft = Offset(cx - rx, y - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(gridStroke)
                )
            }

            // Rotating longitude curves make the sphere visibly spin.
            intArrayOf(-72, -36, 0, 36, 72).forEach { longitude ->
                val phase = Math.toRadians((longitude + rotation).toDouble())
                val x = cx + radius * sin(phase).toFloat()
                val squeeze = abs(cos(phase)).toFloat().coerceIn(0.06f, 1f)
                val path = Path()
                path.moveTo(x, cy - radius)
                path.cubicTo(
                    cx + (x - cx) * squeeze * 0.38f, cy - radius * 0.48f,
                    cx + (x - cx) * squeeze * 0.38f, cy + radius * 0.48f,
                    x, cy + radius
                )
                drawPath(path, color.copy(alpha = if (longitude == 0) 0.27f else 0.14f), style = Stroke(gridStroke, cap = StrokeCap.Round))
            }

            // Bright equator.
            drawOval(
                color.copy(alpha = 0.30f),
                topLeft = Offset(cx - radius, cy - radius * 0.055f),
                size = Size(radius * 2f, radius * 0.11f),
                style = Stroke(1.2.dp.toPx())
            )

            // Global internet nodes and connection traces.
            val nodes = arrayOf(
                -0.62f to -0.18f, -0.38f to 0.42f, -0.08f to -0.52f,
                0.18f to 0.20f, 0.42f to -0.30f, 0.62f to 0.18f,
                -0.16f to 0.64f, 0.34f to 0.62f
            )
            nodes.forEachIndexed { index, node ->
                val nx = cx + radius * node.first
                val ny = cy + radius * node.second
                val depth = (1f - node.first * node.first - node.second * node.second).coerceAtLeast(0.12f)
                drawCircle(color.copy(alpha = 0.18f * depth), 7.dp.toPx() * depth, Offset(nx, ny))
                drawCircle(color.copy(alpha = 0.78f * depth), 2.1.dp.toPx(), Offset(nx, ny))
                if (index % 2 == 0) drawLine(color.copy(alpha = 0.18f), center, Offset(nx, ny), 0.7.dp.toPx(), StrokeCap.Round)
            }

            // Radar beam scans around the globe for reachable LINKO peers.
            val sweepRad = Math.toRadians(sweep.toDouble())
            val sx = cx + radius * cos(sweepRad).toFloat()
            val sy = cy + radius * sin(sweepRad).toFloat()
            drawLine(color.copy(alpha = 0.10f), center, Offset(sx, sy), 11.dp.toPx(), StrokeCap.Round)
            drawLine(color.copy(alpha = 0.88f), center, Offset(sx, sy), 2.2.dp.toPx(), StrokeCap.Round)
            drawCircle(color.copy(alpha = 0.30f), 6.dp.toPx(), Offset(sx, sy))
            drawCircle(color, 2.4.dp.toPx(), Offset(sx, sy))

            // Rotating highlight gives the edge a glassy 3D finish.
            drawArc(
                color.copy(alpha = 0.60f), rotation - 48f, 105f, false,
                Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        label?.let { Text(it, color = color, fontSize = 9.5.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
    }
}
