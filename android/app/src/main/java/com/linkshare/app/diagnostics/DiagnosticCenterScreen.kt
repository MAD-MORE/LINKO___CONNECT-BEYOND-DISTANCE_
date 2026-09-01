package com.linkshare.app.diagnostics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.BG
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Card2
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.update.LinkoUpdateManager
import kotlin.math.sin
import kotlin.math.cos

/** Responsive, animated LINKO diagnostics dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticCenterScreen(
    results: List<DiagnosticResult>,
    onRunDiagnostics: () -> Unit,
    updateManager: LinkoUpdateManager,
    modifier: Modifier = Modifier,
) {
    val viewModel: DiagnosticCenterViewModel = viewModel()
    val liveResults by viewModel.results.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val completedChecks by viewModel.completedChecks.collectAsStateWithLifecycle()
    val currentCheck by viewModel.currentCheck.collectAsStateWithLifecycle()
    val complete by viewModel.complete.collectAsStateWithLifecycle()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val telemetry by LinkoDiagnosticTelemetry.snapshot.collectAsStateWithLifecycle()
    val expanded = remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLogs by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val displayedResults = LinkoDiagnosticCenter.blockedResults(liveResults.ifEmpty { results })
    val overall = LinkoDiagnosticCenter.overall(displayedResults)
    val firstFailure = LinkoDiagnosticCenter.firstFailure(displayedResults)
    val progress = (completedChecks.toFloat() / DiagnosticCenterViewModel.TOTAL_CHECKS).coerceIn(0f, 1f)
    val passed = displayedResults.count { it.status == DiagnosticStatus.PASS }
    val failed = displayedResults.count { it.status == DiagnosticStatus.FAIL }
    val blocked = displayedResults.count { it.status == DiagnosticStatus.BLOCKED }
    val pending = displayedResults.count { it.status == DiagnosticStatus.WAITING || it.status == DiagnosticStatus.CHECKING }
    val logText = buildDiagnosticLog(displayedResults, telemetry)

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val tablet = maxWidth >= 600.dp
        val pulseTransition = rememberInfiniteTransition(label = "diagnostic-network")
        val pulse by pulseTransition.animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "network-pulse"
        )

        Column(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val nodes = listOf(
                        0.04f to 0.10f, 0.52f to 0.05f, 0.96f to 0.12f,
                        0.10f to 0.46f, 0.86f to 0.44f, 0.50f to 0.88f
                    ).map { (x, y) ->
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
                    }
                    listOf(0 to 1, 1 to 2, 0 to 3, 1 to 4, 2 to 4, 3 to 5, 4 to 5)
                        .forEach { (a, b) -> drawLine(Blue.copy(alpha = 0.055f * pulse), nodes[a], nodes[b], 2f) }
                    nodes.forEachIndexed { index, point ->
                        drawCircle(Blue.copy(alpha = 0.035f + 0.045f * pulse), if (index == 5) 15f else 7f, point)
                        drawCircle(Blue.copy(alpha = 0.10f * pulse), if (index == 5) 24f else 13f, point, style = Stroke(2f))
                    }
                }
        ) {
            DiagnosticsHeader(running, overall, tablet)

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = if (tablet) 28.dp else 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DiagnosticHealthCard(
                        running, complete, currentCheck, completedChecks, progress,
                        passed, failed, blocked, pending, overall
                    )
                }
                item { ConnectionJourney(telemetry, tablet) }
                item { TelemetryGrid(telemetry, tablet) }
                firstFailure?.let { item { FailureFocusCard(it) } }
                item { SectionHeader("LIVE EVENTS", "Tap an event to inspect evidence") }
                items(displayedResults, key = { it.name }) { result ->
                    DiagnosticResultRow(
                        result,
                        expanded.value.contains(result.name),
                        {
                            expanded.value = expanded.value.toMutableSet().apply {
                                if (!add(result.name)) remove(result.name)
                            }
                        }
                    )
                }
                item { StartupStyledUpdateCard(updateState, updateManager) }
            }

            DiagnosticsActionBar(
                running = running,
                complete = complete,
                copied = copied,
                onScan = { onRunDiagnostics(); viewModel.runDiagnostics() },
                onLogs = { showLogs = true },
                onCopy = {
                    clipboard.setText(AnnotatedString(logText))
                    copied = true
                }
            )
        }
    }

    if (showLogs) {
        DiagnosticsLogsSheet(displayedResults, telemetry) { showLogs = false }
    }
}


@Composable
private fun DiagnosticsHeader(running: Boolean, overall: DiagnosticOverallState, tablet: Boolean) {
    val transition = rememberInfiniteTransition(label = "startup-style-diagnostic")
    val rotation by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(8000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "diagnostic-orbit"
    )
    val corePulse by transition.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "diagnostic-core"
    )
    val wave by transition.animateFloat(
        0.2f, 1f,
        infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "diagnostic-wave"
    )
    val healthy = overall == DiagnosticOverallState.PASSED
    val tint = if (running) Blue else if (healthy) Green else TextSub
    val title = when {
        running -> "LIVE DIAGNOSTICS"
        healthy -> "SYSTEM HEALTHY"
        overall == DiagnosticOverallState.BLOCKED -> "CHECK BLOCKED"
        overall is DiagnosticOverallState.FAILED -> "ATTENTION REQUIRED"
        else -> "READY TO SCAN"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (tablet) 28.dp else 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (tablet) 104.dp else 82.dp)
                .scale(corePulse),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val base = size.minDimension / 2.55f
                val waveRadius = base * wave
                drawCircle(tint.copy(alpha = (1f - wave) * 0.35f), waveRadius, center, style = Stroke(width = 1.5.dp.toPx()))
                drawCircle(tint.copy(alpha = 0.16f), base * 1.05f, center, style = Stroke(width = 1.dp.toPx()))
                drawCircle(
                    tint.copy(alpha = 0.24f),
                    base * 1.55f,
                    center,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 9f)))
                )
                val positions = (0 until 6).map { i ->
                    val angle = Math.toRadians(rotation.toDouble()) + i * (2.0 * Math.PI / 6.0)
                    val radius = if (i % 2 == 0) base * 1.55f else base * 1.18f
                    Offset(
                        (center.x + radius * cos(angle)).toFloat(),
                        (center.y + radius * sin(angle)).toFloat()
                    )
                }
                positions.forEachIndexed { i, point ->
                    drawLine(tint.copy(alpha = 0.22f), center, point, strokeWidth = 1.dp.toPx())
                    drawCircle(tint.copy(alpha = 0.16f), 8.dp.toPx(), point)
                    drawCircle(if (i % 2 == 0) tint else Blue, 3.5.dp.toPx(), point)
                }
                drawCircle(tint.copy(alpha = 0.20f), base * 0.72f, center)
                drawCircle(tint, 5.dp.toPx(), center)
            }
        }

        Spacer(Modifier.width(if (tablet) 16.dp else 11.dp))

        Column(Modifier.weight(1f)) {
            Text(
                "LINKO",
                color = Color.White,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (tablet) 24.sp else 20.sp,
                letterSpacing = 3.sp
            )
            Text(
                "DIAGNOSTIC CENTER",
                color = TextSub,
                fontFamily = JetBrainsMono,
                fontSize = 8.sp,
                letterSpacing = 1.7.sp
            )
            Spacer(Modifier.height(5.dp))
            AnimatedContent(targetState = title, label = "diagnostic-title") { value ->
                Text(value, color = tint, fontFamily = JetBrainsMono, fontSize = 9.sp)
            }
            Text(
                if (running) "Tracing engine → provider → relay → tunnel → VPN"
                else "LIVE SYSTEM TRACE · evidence only",
                color = TextMuted,
                fontFamily = JetBrainsMono,
                fontSize = 7.sp
            )
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = tint.copy(alpha = 0.12f)
        ) {
            Row(
                Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = tint)
                } else {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = tint)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (running) "LIVE" else if (healthy) "OK" else "TRACE",
                    color = TextPrimary,
                    fontFamily = JetBrainsMono,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun DiagnosticHealthCard(
    running: Boolean, complete: Boolean, currentCheck: String, completedChecks: Int, progress: Float,
    passed: Int, failed: Int, blocked: Int, pending: Int, overall: DiagnosticOverallState
) {
    val ring by rememberInfiniteTransition(label = "health-ring").animateFloat(
        0.92f, 1.04f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "health-pulse"
    )
    val healthy = overall == DiagnosticOverallState.PASSED
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size((72 * ring).dp).drawBehind {
                        val tint = if (healthy) Green else Blue
                        drawCircle(tint.copy(alpha = 0.09f), size.minDimension / 2f)
                        drawCircle(tint.copy(alpha = 0.55f), size.minDimension / 2f, style = Stroke(3f))
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${(progress * 100).toInt()}", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 19.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = when (overall) {
                            DiagnosticOverallState.PASSED -> "SYSTEM HEALTHY"
                            DiagnosticOverallState.BLOCKED -> "CHECK BLOCKED"
                            is DiagnosticOverallState.FAILED -> "FAILURE AT ${overall.component.uppercase()}"
                            DiagnosticOverallState.CHECKING -> if (running) "SYSTEM SCAN IN PROGRESS" else "READY TO SCAN"
                        },
                        label = "health-headline"
                    ) { headline -> Text(headline, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (running) currentCheck.ifBlank { "Tracing LINKO's runtime chain…" }
                        else if (complete) "${passed} passed · ${failed} failed · ${blocked} blocked · ${pending} pending"
                        else "Live runtime evidence, dependency state and connection path.",
                        color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Metric("PASS", passed, Green)
                Metric("FAIL", failed, Red)
                Metric("BLOCK", blocked, TextSub)
                Metric("PENDING", pending, TextSub)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, tint: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = tint, fontFamily = JetBrainsMono, fontSize = 15.sp)
        Text(label, color = TextMuted, fontFamily = JetBrainsMono, fontSize = 7.sp)
    }
}

@Composable
private fun ConnectionJourney(snapshot: DiagnosticTelemetrySnapshot, tablet: Boolean) {
    val stages = listOf("READY", "PROVIDER", "RELAY", "SIGNAL", "TUNNEL", "VPN", "CONNECTED")
    val current = journeyIndex(snapshot)
    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        SectionHeader("CONNECTION JOURNEY", "Live path · stage ${current + 1}/${stages.size}")
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tablet) 18.dp else 10.dp)
        ) {
            items(stages.size) { index ->
                JourneyStage(stages[index], index, index == current, index < current)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            when {
                snapshot.vpnRunning -> "Secure path active · packet routing enabled"
                snapshot.enginePhase.contains("Connected", true) -> "Tunnel connected · waiting for VPN routing"
                snapshot.enginePhase.contains("Signaling", true) -> "Finding and preparing a secure relay path…"
                else -> snapshot.engineDetail
            },
            color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp
        )
    }
}

@Composable
private fun JourneyStage(label: String, index: Int, active: Boolean, done: Boolean) {
    val pulse by rememberInfiniteTransition(label = "stage-${index}").animateFloat(
        0.70f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "stage-pulse"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier.size(34.dp).drawBehind {
                val tint = when {
                    done -> Green
                    active -> Blue
                    else -> TextMuted
                }
                drawCircle(tint.copy(alpha = if (active) 0.20f * pulse else 0.10f), size.minDimension / 2f)
                drawCircle(tint.copy(alpha = if (active) 0.75f else 0.45f), size.minDimension / 2f, style = Stroke(2f))
            },
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = Green)
            else Text("${index + 1}", color = if (active) TextPrimary else TextSub, fontFamily = JetBrainsMono, fontSize = 10.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (active) TextPrimary else TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp)
    }
}

private fun journeyIndex(snapshot: DiagnosticTelemetrySnapshot): Int {
    if (snapshot.vpnRunning) return 6
    if (snapshot.enginePhase.contains("Connected", true)) return 5
    if (snapshot.enginePhase.contains("Tunnel", true) || snapshot.engineDetail.contains("tunnel", true)) return 4
    if (snapshot.enginePhase.contains("Signaling", true)) return 3
    if (snapshot.enginePhase.contains("Relay", true) || snapshot.engineDetail.contains("relay", true)) return 2
    if (snapshot.enginePhase.contains("Provider", true) || snapshot.engineDetail.contains("provider", true)) return 1
    return 0
}

@Composable
private fun TelemetryGrid(snapshot: DiagnosticTelemetrySnapshot, tablet: Boolean) {
    val cards = listOf(
        Triple("ENGINE", if (snapshot.enginePhase.isBlank()) "IDLE" else snapshot.enginePhase.uppercase(), snapshot.engineDetail),
        Triple("REALTIME", if (snapshot.realtimeConnected) "CONNECTED" else "DISCONNECTED", snapshot.realtimeChannels.ifEmpty { listOf("no channels") }.joinToString()),
        Triple("TUNNEL", if (snapshot.engineDetail.contains("tunnel", true)) "ACTIVE" else "CLOSED", snapshot.engineError ?: "Secure transport state"),
        Triple("VPN", if (snapshot.vpnRunning) "RUNNING" else "STOPPED", "TX ${snapshot.vpnTxPackets}/${snapshot.vpnTxBytes}B · RX ${snapshot.vpnRxPackets}/${snapshot.vpnRxBytes}B")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                pair.forEach { TelemetryCard(it.first, it.second, it.third, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TelemetryCard(title: String, value: String, detail: String, modifier: Modifier) {
    LinkoCard(modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Wifi, null, Modifier.size(18.dp), tint = Blue)
            Spacer(Modifier.width(7.dp))
            Text(title, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 10.sp)
        Text(detail, color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp, maxLines = 2)
    }
}

@Composable
private fun FailureFocusCard(result: DiagnosticResult) {
    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Close, null, Modifier.size(23.dp), tint = Red)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("FIRST FAILURE", color = Red, fontFamily = JetBrainsMono, fontSize = 9.sp)
                Text(result.name, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(result.detail, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp)
        result.errorType?.let { Text("TYPE  $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
        result.errorMessage?.let { Text("ERROR  ${sanitizeDiagnosticText(it)}", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 10.sp)
        Text(subtitle, color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp)
    }
}

@Composable
private fun DiagnosticResultRow(result: DiagnosticResult, expanded: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(result.status)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.name, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 10.sp)
                    result.latencyMs?.let { Text("  ${it}ms", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                }
                Text(result.detail.ifBlank { "No diagnostic evidence yet" }, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
                result.blockedBy?.let { Text("BLOCKED BY  $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
            }
            IconButton(onClick = onToggle) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse" else "Expand")
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 46.dp, end = 15.dp, bottom = 12.dp)) {
                result.errorType?.let { Text("ERROR TYPE  $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                result.errorMessage?.let { Text("ERROR MSG   ${sanitizeDiagnosticText(it)}", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                result.blockedBy?.let { Text("BLOCKER     $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                Text("MEASURED    ${result.measuredAtMs}", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun StatusIcon(status: DiagnosticStatus) {
    val icon = when (status) {
        DiagnosticStatus.PASS -> Icons.Default.CheckCircle
        DiagnosticStatus.FAIL -> Icons.Default.Close
        DiagnosticStatus.BLOCKED -> Icons.Default.Info
        DiagnosticStatus.CHECKING, DiagnosticStatus.WAITING -> Icons.Default.Refresh
        DiagnosticStatus.SKIPPED -> Icons.Default.Warning
    }
    val tint = when (status) {
        DiagnosticStatus.PASS -> Green
        DiagnosticStatus.FAIL -> Red
        DiagnosticStatus.BLOCKED -> TextSub
        DiagnosticStatus.CHECKING, DiagnosticStatus.WAITING -> Blue
        DiagnosticStatus.SKIPPED -> TextSub
    }
    Icon(icon, status.name, Modifier.size(20.dp), tint = tint)
}

@Composable
private fun StartupStyledUpdateCard(state: LinkoUpdateManager.UpdateState, manager: LinkoUpdateManager) {
    val active = state.status in setOf(
        LinkoUpdateManager.UpdateStatus.Checking,
        LinkoUpdateManager.UpdateStatus.Downloading,
        LinkoUpdateManager.UpdateStatus.Verifying,
        LinkoUpdateManager.UpdateStatus.Installing
    )
    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, Modifier.size(22.dp), tint = Blue)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("UPDATE NETWORK", color = Green, fontFamily = JetBrainsMono, fontSize = 9.sp)
                Text(state.statusMessage.ifBlank { "Automatic update detection ready" }, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp)
                Text("Current ${state.installedVersionName} (${state.installedVersionCode})", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
            }
        }
        if (active) {
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(progress = { state.progressPercent / 100f }, Modifier.fillMaxWidth())
        }
        state.errorMessage?.let { Text(sanitizeDiagnosticText(it), color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
        Spacer(Modifier.height(6.dp))
        when (state.status) {
            LinkoUpdateManager.UpdateStatus.UpdateAvailable -> PrimaryButton("UPDATE NOW", manager::startUpdate, color = Blue)
            LinkoUpdateManager.UpdateStatus.Error, LinkoUpdateManager.UpdateStatus.RateLimited -> PrimaryButton("RETRY UPDATE CHECK", manager::retry, color = Blue)
            else -> PrimaryButton("CHECK FOR UPDATES", manager::checkAndOfferUpdate, color = Blue, enabled = !active, loading = state.status == LinkoUpdateManager.UpdateStatus.Checking)
        }
    }
}

@Composable
private fun DiagnosticsActionBar(
    running: Boolean,
    complete: Boolean,
    copied: Boolean,
    onScan: () -> Unit,
    onLogs: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(color = Card2.copy(alpha = 0.97f), tonalElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                if (running) "SCANNING" else if (complete) "SCAN AGAIN" else "SCAN",
                onScan, color = Blue, enabled = !running, loading = running, modifier = Modifier.weight(1f)
            )
            ActionButton("LOGS", Icons.Default.FilterList, onLogs, Modifier.weight(1f))
            ActionButton(if (copied) "COPIED" else "COPY", Icons.Default.ContentCopy, onCopy, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontFamily = JetBrainsMono, fontSize = 8.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsLogsSheet(
    results: List<DiagnosticResult>,
    telemetry: DiagnosticTelemetrySnapshot,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("ALL") }
    val logText = buildDiagnosticLog(results, telemetry)
    val lines = logText.lines().filter { query.isBlank() || it.contains(query, true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LIVE LOGS", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 14.sp)
                    Text("Privacy-safe · searchable · copyable", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(logText)) }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("COPY ALL", fontFamily = JetBrainsMono, fontSize = 8.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search logs", fontSize = 11.sp) }
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(7.dp)) {
                listOf("ALL", "PASS", "FAIL", "INFO").forEach { filter ->
                    FilterChip(
                        selected = selected == filter,
                        onClick = { selected = filter },
                        label = { Text(filter, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.fillMaxWidth().height(360.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(lines.filter { selected == "ALL" || it.contains(selected, true) }) { line ->
                    Text(
                        line,
                        color = if (line.contains("FAIL", true)) Red else TextSub,
                        fontFamily = JetBrainsMono,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun buildDiagnosticLog(results: List<DiagnosticResult>, telemetry: DiagnosticTelemetrySnapshot): String {
    val builder = StringBuilder()
    builder.appendLine("LINKO DIAGNOSTIC REPORT")
    builder.appendLine("Generated: ${System.currentTimeMillis()}")
    builder.appendLine()
    results.forEach { result ->
        builder.appendLine(
            "[${result.status.name}] ${result.name} | ${sanitizeDiagnosticText(result.detail)}" +
                (result.errorType?.let { " | type=$it" } ?: "") +
                (result.latencyMs?.let { " | latency_ms=$it" } ?: "")
        )
    }
    builder.appendLine()
    builder.appendLine("RUNTIME")
    builder.appendLine("engine=${sanitizeDiagnosticText(telemetry.enginePhase)} detail=${sanitizeDiagnosticText(telemetry.engineDetail)}")
    builder.appendLine("realtime=${telemetry.realtimeConnected} channels=${telemetry.realtimeChannels.joinToString(",")}")
    builder.appendLine("vpn=${telemetry.vpnRunning} tx_packets=${telemetry.vpnTxPackets} tx_bytes=${telemetry.vpnTxBytes} rx_packets=${telemetry.vpnRxPackets} rx_bytes=${telemetry.vpnRxBytes}")
    telemetry.engineTrace.takeLast(20).forEach { builder.appendLine("TRACE ${sanitizeDiagnosticText(it)}") }
    return builder.toString()
}

private fun sanitizeDiagnosticText(value: String): String {
    return value
        .replace(Regex("(?i)(token|authorization|secret|password)\\\\s*[:=]\\\\s*\\\\S+"), "$1=<redacted>")
        .replace(Regex("(?i)bearer\\\\s+\\\\S+"), "Bearer <redacted>")
}
