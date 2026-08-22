package com.linkshare.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.Border
import com.linkshare.app.ui.theme.JetBrainsMono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SpiralConfig(val radiusFraction: Float, val arcFraction: Float, val clockwise: Boolean, val baseDurationMs: Int, val opacity: Float)
private val SPIRALS = listOf(
    SpiralConfig(0.58f, 0.55f, true, 1800, 0.70f),
    SpiralConfig(0.44f, 0.45f, false, 1250, 0.55f),
    SpiralConfig(0.30f, 0.60f, true, 900, 0.45f),
    SpiralConfig(0.17f, 0.38f, false, 600, 0.35f),
)

@Composable
fun Ring(color: Color, size: Dp = 160.dp, pulse: Boolean = false, idle: Boolean = false, label: String? = null, onClick: (() -> Unit)? = null) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val speedMultiplier = when { idle -> 2.4f; pulse -> 0.7f; else -> 1.0f }
    val outerDurationMs = ((if (idle) 2800 else 1100) * speedMultiplier).toInt()
    val outerAngle by infiniteTransition.animateFloat(-70f, 220f, infiniteRepeatable(tween(outerDurationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "outerAngle")
    val pulseAlpha by infiniteTransition.animateFloat(1f, if (pulse) 0.35f else 1f, infiniteRepeatable(tween(outerDurationMs, easing = LinearEasing), RepeatMode.Reverse), label = "pulseAlpha")
    val spiralAngles = listOf(
        spiralAngle(infiniteTransition, SPIRALS[0], speedMultiplier),
        spiralAngle(infiniteTransition, SPIRALS[1], speedMultiplier),
        spiralAngle(infiniteTransition, SPIRALS[2], speedMultiplier),
        spiralAngle(infiniteTransition, SPIRALS[3], speedMultiplier),
    ).map { it.value }
    val ripples = remember { mutableStateListOf<Float>() }
    val scope = rememberCoroutineScope()
    val handleClick: () -> Unit = {
        scope.launch {
            ripples.add(0f); val idx = ripples.lastIndex
            repeat(30) { delay(25); if (idx < ripples.size) ripples[idx] = it / 30f }
            if (ripples.isNotEmpty()) ripples.removeAt(0)
        }
        onClick?.invoke()
    }
    val textMeasurer = rememberTextMeasurer()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = handleClick) else Modifier)) {
        Canvas(modifier = Modifier.size(size)) {
            val outerR = this.size.minDimension / 2f - 10.dp.toPx(); val cx = this.size.width / 2f; val cy = this.size.height / 2f; val center = Offset(cx, cy)
            ripples.forEach { progress ->
                val r = outerR * 0.9f + (outerR * 1.55f - outerR * 0.9f) * progress
                drawCircle(color.copy(alpha = (1f - progress) * 0.6f), r, center, style = Stroke(2.dp.toPx()))
            }
            drawCircle(Border, outerR, center, style = Stroke(1.5.dp.toPx()))
            SPIRALS.forEachIndexed { i, s ->
                val r = outerR * s.radiusFraction; val arcSweep = 360f * s.arcFraction
                val topLeft = Offset(cx - r, cy - r); val arcSize = Size(r * 2, r * 2)
                drawArc(color.copy(alpha = s.opacity * 0.18f), spiralAngles[i], arcSweep, false, topLeft, arcSize, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color.copy(alpha = s.opacity), spiralAngles[i], arcSweep, false, topLeft, arcSize, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
            val outerSweep = 360f * 0.72f; val outerRect = Offset(cx - outerR, cy - outerR); val outerSize = Size(outerR * 2, outerR * 2)
            drawArc(color.copy(alpha = 0.13f * pulseAlpha), outerAngle, outerSweep, false, outerRect, outerSize, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color.copy(alpha = pulseAlpha), outerAngle, outerSweep, false, outerRect, outerSize, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            val dotAngleRad = Math.toRadians(outerAngle.toDouble()); val dotX = cx + outerR * Math.cos(dotAngleRad).toFloat(); val dotY = cy + outerR * Math.sin(dotAngleRad).toFloat()
            drawCircle(color, 4.dp.toPx(), Offset(dotX, dotY))
        }
        if (label != null) {
            Canvas(modifier = Modifier.size(size)) {
                val measured = textMeasurer.measure(label, style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.2.sp, color = color))
                drawText(measured, topLeft = Offset((this.size.width - measured.size.width) / 2f, (this.size.height - measured.size.height) / 2f))
            }
        }
    }
}

@Composable
private fun spiralAngle(transition: InfiniteTransition, config: SpiralConfig, speedMultiplier: Float): State<Float> {
    val durationMs = (config.baseDurationMs * speedMultiplier).toInt()
    return if (config.clockwise) transition.animateFloat(0f, 360f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart), label = "spiralCW_${config.baseDurationMs}")
    else transition.animateFloat(360f, 0f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart), label = "spiralCCW_${config.baseDurationMs}")
}
