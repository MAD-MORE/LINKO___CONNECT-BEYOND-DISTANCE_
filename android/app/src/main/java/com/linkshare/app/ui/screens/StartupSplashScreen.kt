package com.linkshare.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.components.GhostButton
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StartupSplashScreen(
    failed: Boolean = false,
    onRetry: () -> Unit = {},
    onContinueOffline: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshAnimation")

    // Rotation angle for the outer orbiting network nodes
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotation"
    )

    // Pulse scale for central node
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    // Wave ring radius expander
    val waveRadius by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveRadius"
    )

    // Animated status messages cycling
    var stepIndex by remember { mutableStateOf(0) }
    val stepMessages = listOf(
        "Initializing Secure Cryptographic Keys…",
        "Connecting to Supabase Mesh Cloud…",
        "Synchronizing Realtime Peer Network…",
        "Preparing Ultra-Low Latency Tunnel…",
        "System Ready • Entering LINKO"
    )

    LaunchedEffect(failed) {
        if (!failed) {
            while (true) {
                delay(600)
                stepIndex = (stepIndex + 1) % stepMessages.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D1B2A), BG, Color(0xFF05080E)),
                    center = Offset(500f, 600f),
                    radius = 1200f
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP: MAD-MORE STUDIO HEADER
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF132238).copy(alpha = 0.8f))
                        .border(1.dp, Blue.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "M A D - M O R E",
                        color = Green,
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "BEYOND DISTANCE PROTOCOLS",
                    color = TextSub.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMono,
                    letterSpacing = 1.8.sp
                )
            }

            // CENTER: ANIMATED NETWORK CANVAS & LINKO BRAND
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .scale(corePulse)
            ) {
                // Background Dynamic Network Graph Animation
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension / 2.3f

                    // 1. Expanding Pulse Wave Ring
                    val currentWaveR = baseRadius * waveRadius
                    val waveAlpha = (1f - waveRadius).coerceIn(0f, 1f) * 0.45f
                    drawCircle(
                        color = Blue.copy(alpha = waveAlpha),
                        radius = currentWaveR,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // 2. Concentric Orbit Rings
                    drawCircle(
                        color = Blue.copy(alpha = 0.25f),
                        radius = baseRadius * 0.85f,
                        center = center,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    )
                    drawCircle(
                        color = Green.copy(alpha = 0.2f),
                        radius = baseRadius * 0.55f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // 3. Orbiting Mesh Nodes and Dynamic Interconnect Lines
                    val nodeCount = 6
                    val radOffset = Math.toRadians(rotationAngle.toDouble())
                    val nodePositions = (0 until nodeCount).map { i ->
                        val angle = radOffset + (i * (2 * Math.PI / nodeCount))
                        val r = if (i % 2 == 0) baseRadius * 0.85f else baseRadius * 0.65f
                        Offset(
                            x = (center.x + r * cos(angle)).toFloat(),
                            y = (center.y + r * sin(angle)).toFloat()
                        )
                    }

                    // Draw connection lines to center and adjacent nodes
                    for (i in nodePositions.indices) {
                        val pos = nodePositions[i]
                        drawLine(
                            color = Blue.copy(alpha = 0.35f),
                            start = center,
                            end = pos,
                            strokeWidth = 1.dp.toPx()
                        )
                        val nextPos = nodePositions[(i + 1) % nodeCount]
                        drawLine(
                            color = Green.copy(alpha = 0.25f),
                            start = pos,
                            end = nextPos,
                            strokeWidth = 1.dp.toPx()
                        )
                        // Node circle dot
                        drawCircle(
                            color = if (i % 2 == 0) Blue else Green,
                            radius = if (i % 2 == 0) 5.dp.toPx() else 4.dp.toPx(),
                            center = pos
                        )
                    }

                    // Central Glowing Core Hub
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Blue.copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 0.45f
                        ),
                        radius = baseRadius * 0.45f,
                        center = center
                    )
                }

                // Central Typography (LINKO)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LINKO",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "CONNECT BEYOND DISTANCE",
                        color = Green,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            // BOTTOM: INITIALIZATION STATUS & PULSATING DOTS
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                if (!failed) {
                    // Pulsating Animated 3-Dots
                    PulsatingDotsRow()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stepMessages[stepIndex],
                        color = TextSub,
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Putting everything in place…",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMono
                    )
                } else {
                    Text(
                        text = "SYNC DELAYED OR OFFLINE",
                        color = Yellow,
                        fontSize = 14.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Unable to contact the cloud mesh. You can continue offline using your cached profile.",
                        color = TextSub,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("CONTINUE OFFLINE", onContinueOffline, color = Blue)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("RETRY CONNECTION", onRetry, outline = true)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Sign Out", onSignOut)
                }
            }
        }
    }
}

@Composable
private fun PulsatingDotsRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsTransition")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).scale(dot1Scale).clip(CircleShape).background(Blue))
        Box(Modifier.size(8.dp).scale(dot2Scale).clip(CircleShape).background(Green))
        Box(Modifier.size(8.dp).scale(dot3Scale).clip(CircleShape).background(Blue))
    }
}
