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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.model.ConnectionUiState

private val PrototypeBg = Color(0xFF080808)
private val PrototypeCard = Color(0xFF181818)
private val PrototypeBorder = Color(0xFF242424)
private val PrototypeGreen = Color(0xFF22C55E)
private val PrototypeRed = Color(0xFFEF4444)
private val PrototypeBlue = Color(0xFF3B7EF6)
private val PrototypeText = Color(0xFFF2F2F2)
private val PrototypeSub = Color(0xFFA0A0A0)
private val PrototypeMuted = Color(0xFF505050)

@Composable
fun PrototypeProviderActiveScreen(state: ConnectionUiState, onStopSharing: () -> Unit) {
    var showUsage by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(PrototypeBg).padding(horizontal = 22.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("LINKO", color = PrototypeText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(if (showUsage) "Live Usage" else "Sharing Active", color = PrototypeSub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(22.dp))
        if (!showUsage) {
            Column(Modifier.weight(1f), Alignment.CenterHorizontally, Arrangement.Center) {
                PrototypeRing(PrototypeGreen, 190.dp, "SHARING")
                Spacer(Modifier.height(20.dp))
                Text("Sharing Active", color = PrototypeGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (state.activeFriend != null) "Your connection is being shared with" else "Your connection is ready to be shared", color = PrototypeSub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text(state.activeFriend?.name ?: "Waiting for a receiver", color = PrototypeText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Text(if (state.activeFriend != null) "CONNECTED • TRUSTED" else "AVAILABLE • PROTECTED", color = PrototypeMuted, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 6.dp))
            }
            PrototypeButton("LIVE USAGE", PrototypeBg, PrototypeText, true) { showUsage = true }
            Spacer(Modifier.height(10.dp))
            PrototypeButton("STOP SHARING", PrototypeRed, Color.White) { onStopSharing() }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Live Usage", color = PrototypeText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(state.activeFriend?.name?.let { "$it • Current session" } ?: "Current sharing session", color = PrototypeSub, fontSize = 13.sp)
                UsageCard("DATA USED", formatBytes(state.usageStats.bytesSent + state.usageStats.bytesReceived), "Current session", PrototypeBlue)
                UsageCard("DURATION", formatDuration(state.usageStats.sessionSeconds), "Current session", Color(0xFFF59E0B))
                UsageCard("CONNECTED", state.usageStats.connectedClients.toString(), "Active receivers", PrototypeGreen)
                Spacer(Modifier.weight(1f))
                PrototypeButton("BACK TO SHARING", PrototypeBg, PrototypeText, true) { showUsage = false }
                PrototypeButton("KILL SESSION", PrototypeRed, Color.White) { onStopSharing() }
            }
        }
    }
}

@Composable
private fun PrototypeButton(label: String, background: Color, content: Color, outlined: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp).then(if (outlined) Modifier.border(1.dp, PrototypeBorder, RoundedCornerShape(10.dp)) else Modifier), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content), elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun UsageCard(label: String, value: String, sub: String, accent: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PrototypeCard).border(1.dp, PrototypeBorder, RoundedCornerShape(12.dp)).padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = PrototypeMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(value, color = accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = PrototypeSub, fontSize = 12.sp)
    }
}

@Composable
private fun PrototypeRing(color: Color, ringSize: androidx.compose.ui.unit.Dp, label: String) {
    val transition = rememberInfiniteTransition(label = "linko-prototype-ring")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "ring-rotation")
    val pulse by transition.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "ring-pulse")
    Box(Modifier.size(ringSize), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val canvasCenter = Offset(this.size.width / 2f, this.size.height / 2f)
            val outer = this.size.minDimension / 2f - 10.dp.toPx()
            drawCircle(PrototypeBorder, radius = outer, style = Stroke(width = 1.5.dp.toPx()))
            val fractions = listOf(0.58f, 0.44f, 0.30f, 0.17f)
            val arcs = listOf(198f, 162f, 216f, 137f)
            fractions.forEachIndexed { index, fraction ->
                val r = outer * fraction
                val alpha = listOf(0.70f, 0.55f, 0.45f, 0.35f)[index]
                drawArc(color.copy(alpha = alpha * 0.22f), rotation + index * 70f, arcs[index], false, topLeft = Offset(canvasCenter.x - r, canvasCenter.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color.copy(alpha = alpha), rotation + index * 70f, arcs[index], false, topLeft = Offset(canvasCenter.x - r, canvasCenter.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            drawArc(color.copy(alpha = 0.13f * pulse), rotation, 260f, false, topLeft = Offset(canvasCenter.x - outer, canvasCenter.y - outer), size = androidx.compose.ui.geometry.Size(outer * 2, outer * 2), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color, rotation, 260f, false, topLeft = Offset(canvasCenter.x - outer, canvasCenter.y - outer), size = androidx.compose.ui.geometry.Size(outer * 2, outer * 2), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            val radians = Math.toRadians((rotation - 90f).toDouble())
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(canvasCenter.x + outer * kotlin.math.cos(radians).toFloat(), canvasCenter.y + outer * kotlin.math.sin(radians).toFloat()))
        }
        Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.9.sp)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val mb = bytes / 1024f / 1024f
    return if (mb >= 1f) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024f)
}

private fun formatDuration(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
