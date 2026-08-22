package com.linkshare.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LinkoBackground = Color(0xFF080808)
val LinkoSurface = Color(0xFF111111)
val LinkoCard = Color(0xFF181818)
val LinkoCardAlt = Color(0xFF1E1E1E)
val LinkoBorder = Color(0xFF242424)
val LinkoInk = Color(0xFFF2F2F2)
val LinkoSub = Color(0xFFA0A0A0)
val LinkoMuted = Color(0xFF505050)
val LinkoBlue = Color(0xFF3B7EF6)
val LinkoGreen = Color(0xFF22C55E)
val LinkoYellow = Color(0xFFF59E0B)
val LinkoRed = Color(0xFFEF4444)

// Compatibility aliases used by existing production files.
val LinkoParchment = LinkoBackground
val LinkoSeaGlass = LinkoBlue
val LinkoCopper = LinkoYellow
val LinkoPlum = LinkoCardAlt
val LinkoAlert = LinkoRed

@Composable
fun LinkoScreenHeader(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("LINKO", color = LinkoBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Text(title, color = LinkoInk, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(detail, color = LinkoSub, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun LinkoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LinkoCard, RoundedCornerShape(18.dp))
            .border(1.dp, LinkoBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
fun LinkoPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LinkoBlue, contentColor = Color.White)
    ) { Text(text.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = .5.sp) }
}

@Composable
fun LinkoSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LinkoCardAlt, contentColor = LinkoInk)
    ) { Text(text.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = .5.sp) }
}

@Composable
fun LinkoDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LinkoRed, contentColor = Color.White)
    ) { Text(text.uppercase(), fontWeight = FontWeight.Bold) }
}

@Composable
fun LinkoStatusDot(active: Boolean, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .size(9.dp)
            .background(if (active) LinkoGreen else LinkoYellow, RoundedCornerShape(50))
    )
}

@Composable
fun LinkoStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = LinkoSub, fontSize = 14.sp)
        Text(value, color = LinkoInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LinkoRing(
    modifier: Modifier = Modifier,
    color: Color = LinkoBlue,
    size: Dp = 190.dp,
    label: String,
    pulse: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "linko-ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (pulse) 1100 else 1800, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val r = size.minDimension / 2f - 12.dp.toPx()
            drawCircle(color = LinkoBorder, radius = r, style = Stroke(1.5.dp.toPx()))
            rotate(rotation) {
                drawArc(color = color.copy(alpha = .16f), startAngle = 0f, sweepAngle = 220f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - r, size.height / 2f - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color = color, startAngle = 0f, sweepAngle = 220f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - r, size.height / 2f - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color = color, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f - r))
            }
            for (i in 1..3) {
                val rr = r * (0.30f + i * .14f)
                drawArc(color = color.copy(alpha = .45f / i), startAngle = rotation * (if (i % 2 == 0) -1f else 1f), sweepAngle = 150f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - rr, size.height / 2f - rr), size = androidx.compose.ui.geometry.Size(rr * 2, rr * 2), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
    }
}
