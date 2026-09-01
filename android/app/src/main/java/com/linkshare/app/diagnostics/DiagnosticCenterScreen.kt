package com.linkshare.app.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.BG
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Card as LinkoCardColor
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.update.LinkoUpdateManager

/** Live LINKO diagnostics dashboard. Presentation-only redesign; diagnostic engine is unchanged. */
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
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var copied by remember { mutableStateOf(false) }

    val displayedResults = LinkoDiagnosticCenter.blockedResults(liveResults.ifEmpty { results })
    val overall = LinkoDiagnosticCenter.overall(displayedResults)
    val firstFailure = LinkoDiagnosticCenter.firstFailure(displayedResults)
    val progress = (completedChecks.toFloat() / DiagnosticCenterViewModel.TOTAL_CHECKS).coerceIn(0f, 1f)
    val passed = displayedResults.count { it.status == DiagnosticStatus.PASS }
    val failed = displayedResults.count { it.status == DiagnosticStatus.FAIL }
    val blocked = displayedResults.count { it.status == DiagnosticStatus.BLOCKED }
    val pending = displayedResults.count { it.status == DiagnosticStatus.WAITING || it.status == DiagnosticStatus.CHECKING }
    val logText = diagnosticLog(displayedResults, telemetry, overall)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val pulse = rememberInfiniteTransition(label = "diagnostic-pulse")
        .animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
            label = "diagnostic-pulse-alpha",
        ).value

    Column(
        modifier = modifier.fillMaxSize().background(BG).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DiagnosticHeaderCard(
                    running = running,
                    complete = complete,
                    overall = overall,
                    completedChecks = completedChecks,
                    totalChecks = DiagnosticCenterViewModel.TOTAL_CHECKS,
                    progress = progress,
                    passed = passed,
                    failed = failed,
                    blocked = blocked,
                    pending = pending,
                    currentCheck = currentCheck,
                    pulse = pulse,
                )
            }
            item { LiveActivityCard(telemetry = telemetry, running = running, pulse = pulse) }
            item {
                DiagnosticSectionTitle(
                    title = "SYSTEM CHECKS",
                    detail = "Live subsystem evidence",
                    icon = Icons.Outlined.NetworkCheck,
                )
            }
            items(displayedResults, key = { it.name }) { result ->
                DiagnosticSubsystemCard(
                    result = result,
                    expanded = expanded.contains(result.name),
                    onToggle = {
                        expanded = expanded.toMutableSet().apply {
                            if (!add(result.name)) remove(result.name)
                        }
                    },
                    pulse = pulse,
                )
            }
            item {
                DiagnosticLogCard(
                    logText = logText,
                    copied = copied,
                    onCopy = {
                        clipboard.setText(AnnotatedString(logText))
                        copyDiagnosticLog(context, logText)
                        copied = true
                    },
                )
            }
            item {
                AnimatedVisibility(firstFailure != null) {
                    firstFailure?.let { DiagnosticFailureCard(it) }
                }
            }
            item { StartupStyledUpdateCard(updateState) }
        }

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = when {
                running -> "RUNNING DIAGNOSTICS…"
                complete -> "RUN DIAGNOSTICS AGAIN"
                else -> "RUN FULL DIAGNOSTICS"
            },
            onClick = {
                copied = false
                onRunDiagnostics()
                viewModel.runDiagnostics()
            },
            color = Blue,
            enabled = !running,
            loading = running,
        )
    }
}

@Composable
private fun DiagnosticHeaderCard(
    running: Boolean,
    complete: Boolean,
    overall: DiagnosticOverallState,
    completedChecks: Int,
    totalChecks: Int,
    progress: Float,
    passed: Int,
    failed: Int,
    blocked: Int,
    pending: Int,
    currentCheck: String,
    pulse: Float,
) {
    val statusText = when (overall) {
        DiagnosticOverallState.PASSED -> "SYSTEM HEALTHY"
        DiagnosticOverallState.BLOCKED -> "SYSTEM CHECK BLOCKED"
        is DiagnosticOverallState.FAILED -> "ATTENTION: ${overall.component.uppercase()}"
        DiagnosticOverallState.CHECKING -> if (running) "SYSTEM SCAN IN PROGRESS" else "READY TO SCAN"
    }
    val statusIcon = when (overall) {
        DiagnosticOverallState.PASSED -> Icons.Filled.CheckCircle
        DiagnosticOverallState.BLOCKED -> Icons.Filled.Info
        is DiagnosticOverallState.FAILED -> Icons.Filled.Error
        DiagnosticOverallState.CHECKING -> Icons.Filled.Refresh
    }
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = LinkoCardColor),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("LINKO", color = TextSub, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Text("Diagnostic Center", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("LIVE SYSTEM HEALTH", color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Surface(shape = MaterialTheme.shapes.large, color = statusTint(overall).copy(alpha = 0.14f)) {
                    Icon(statusIcon, contentDescription = statusText, tint = statusTint(overall), modifier = Modifier.padding(12.dp).size(26.dp).alpha(if (running) pulse else 1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(MaterialTheme.shapes.small).background(statusTint(overall).copy(alpha = if (running) pulse else 1f))
                )
                Spacer(Modifier.size(8.dp))
                Text(statusText, color = statusTint(overall), fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    running -> currentCheck.ifBlank { "Running live LINKO probes…" }
                    complete -> "$passed passed · $failed failed · $blocked blocked · $pending pending"
                    else -> "Real runtime checks, presented as a live dashboard."
                },
                color = TextSub,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$completedChecks / $totalChecks probes", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
                Text("${(progress * 100).toInt()}%", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveActivityCard(telemetry: DiagnosticTelemetrySnapshot, running: Boolean, pulse: Float) {
    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LIVE ACTIVITY", color = Green, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(if (running) "Diagnostic engine is active" else "Runtime telemetry is being observed", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Filled.Terminal, contentDescription = "Live activity", tint = Green, modifier = Modifier.size(24.dp).alpha(if (running) pulse else 1f))
        }
        Spacer(Modifier.height(10.dp))
        LiveMetricRow("ENGINE", telemetry.enginePhase, telemetry.engineDetail)
        LiveMetricRow("REALTIME", if (telemetry.realtimeConnected) "CONNECTED" else "DISCONNECTED", telemetry.realtimeError ?: "")
        LiveMetricRow("VPN", if (telemetry.vpnRunning) "RUNNING" else "STOPPED", "TX ${telemetry.vpnTxPackets}/${telemetry.vpnTxBytes}B · RX ${telemetry.vpnRxPackets}/${telemetry.vpnRxBytes}B")
        if (telemetry.engineTrace.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("TRACE", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
            telemetry.engineTrace.takeLast(4).forEach { line ->
                Text("› $line", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun LiveMetricRow(label: String, value: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp, modifier = Modifier.size(width = 72.dp, height = 18.dp))
        Text(value.ifBlank { "—" }, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (detail.isNotBlank()) Text(detail, color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp)
    }
}

@Composable
private fun DiagnosticSectionTitle(title: String, detail: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Column {
            Text(title, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(detail, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
        }
    }
}

@Composable
private fun DiagnosticSubsystemCard(
    result: DiagnosticResult,
    expanded: Boolean,
    onToggle: () -> Unit,
    pulse: Float,
) {
    val accent = statusTint(result.status)
    val icon = subsystemIcon(result.name)
    Card(
        Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = LinkoCardColor),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = accent.copy(alpha = 0.12f)) {
                    Icon(icon, contentDescription = result.name, tint = accent, modifier = Modifier.padding(9.dp).size(22.dp).alpha(if (result.status == DiagnosticStatus.CHECKING) pulse else 1f))
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(result.detail.ifBlank { "No diagnostic evidence yet" }, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(statusLabel(result.status), color = accent, fontFamily = JetBrainsMono, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    result.latencyMs?.let { Text("${it} ms", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                }
                Icon(Icons.Filled.Info, contentDescription = if (expanded) "Hide details" else "Show details", tint = TextSub, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, start = 41.dp)) {
                    result.errorType?.let { DetailLine("ERROR TYPE", it) }
                    result.errorMessage?.let { DetailLine("ERROR", it) }
                    result.blockedBy?.let { DetailLine("BLOCKED BY", it) }
                    result.measuredAtMs.let { DetailLine("MEASURED", it.toString()) }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp, modifier = Modifier.size(width = 82.dp, height = 16.dp))
        Text(value, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 8.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DiagnosticLogCard(logText: String, copied: Boolean, onCopy: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = LinkoCardColor),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, tint = Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Column {
                        Text("DIAGNOSTIC LOG", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                        Text("Evidence captured from the current run", color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp)
                    }
                }
                IconButton(onClick = onCopy) {
                    Icon(if (copied) Icons.Filled.CheckCircle else Icons.Outlined.ContentCopy, contentDescription = "Copy diagnostic log", tint = if (copied) Green else TextSub)
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(shape = MaterialTheme.shapes.medium, color = BG.copy(alpha = 0.65f)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    logText.lines().takeLast(12).forEach { line ->
                        Text(line, color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Crossfade(copied, label = "copy-state") { done ->
                Text(if (done) "COPIED TO CLIPBOARD" else "Tap copy to export the full diagnostic report", color = if (done) Green else TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun DiagnosticFailureCard(result: DiagnosticResult) {
    LinkoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(21.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text("FIRST FAILURE", color = MaterialTheme.colorScheme.error, fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(result.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(result.detail, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 8.sp)
        result.errorMessage?.let { Text(it, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun StartupStyledUpdateCard(state: LinkoUpdateManager.UpdateState) {
    val active = state.status in setOf(
        LinkoUpdateManager.UpdateStatus.Checking,
        LinkoUpdateManager.UpdateStatus.Downloading,
        LinkoUpdateManager.UpdateStatus.Verifying,
        LinkoUpdateManager.UpdateStatus.Installing,
    )
    LinkoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, contentDescription = "Update status", tint = Blue, modifier = Modifier.size(21.dp))
            Spacer(Modifier.size(9.dp))
            Column(Modifier.weight(1f)) {
                Text("UPDATE NETWORK", color = Green, fontFamily = JetBrainsMono, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(state.statusMessage.ifBlank { "Automatic update detection ready" }, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 8.sp)
                Text("Current ${state.installedVersionName} (${state.installedVersionCode})", color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp)
            }
            if (active) {
                LinearProgressIndicator(progress = { state.progressPercent / 100f }, modifier = Modifier.size(width = 48.dp, height = 5.dp))
            }
        }
        state.errorMessage?.let { Text(it, color = TextSub, fontFamily = JetBrainsMono, fontSize = 7.sp, modifier = Modifier.padding(top = 5.dp)) }
    }
}

private fun statusTint(status: DiagnosticStatus): Color = when (status) {
    DiagnosticStatus.PASS -> Green
    DiagnosticStatus.FAIL -> Color(0xFFFF5D5D)
    DiagnosticStatus.BLOCKED -> Color(0xFFFFB84D)
    DiagnosticStatus.CHECKING -> Blue
    DiagnosticStatus.WAITING -> TextSub
    DiagnosticStatus.SKIPPED -> TextSub
}

private fun statusLabel(status: DiagnosticStatus): String = when (status) {
    DiagnosticStatus.PASS -> "PASS"
    DiagnosticStatus.FAIL -> "FAILED"
    DiagnosticStatus.BLOCKED -> "BLOCKED"
    DiagnosticStatus.CHECKING -> "CHECKING"
    DiagnosticStatus.WAITING -> "WAITING"
    DiagnosticStatus.SKIPPED -> "SKIPPED"
}

private fun subsystemIcon(name: String): ImageVector = when (name.lowercase()) {
    in listOf("network", "connectivity", "internet") -> Icons.Outlined.NetworkCheck
    in listOf("device", "device identity", "registration", "registered device") -> Icons.Outlined.Smartphone
    in listOf("presence", "heartbeat", "online status") -> Icons.Outlined.Person
    in listOf("relay", "udp relay") -> Icons.Outlined.Hub
    in listOf("backend", "control plane", "api") -> Icons.Outlined.Cloud
    in listOf("security", "authentication", "auth") -> Icons.Outlined.Security
    else -> Icons.Outlined.Info
}

private fun diagnosticLog(
    results: List<DiagnosticResult>,
    telemetry: DiagnosticTelemetrySnapshot,
    overall: DiagnosticOverallState,
): String {
    val builder = StringBuilder()
    builder.appendLine("LINKO DIAGNOSTIC REPORT")
    builder.appendLine("=======================")
    builder.appendLine("OVERALL   ${overallLabel(overall)}")
    builder.appendLine("ENGINE    ${telemetry.enginePhase} · ${telemetry.engineDetail}")
    builder.appendLine("REALTIME  ${if (telemetry.realtimeConnected) "CONNECTED" else "DISCONNECTED"}")
    builder.appendLine("VPN       ${if (telemetry.vpnRunning) "RUNNING" else "STOPPED"}")
    builder.appendLine()
    results.forEach { result ->
        builder.appendLine("${statusLabel(result.status).padEnd(8)} ${result.name} — ${result.detail}")
    }
    if (telemetry.engineTrace.isNotEmpty()) {
        builder.appendLine()
        builder.appendLine("TRACE")
        telemetry.engineTrace.takeLast(10).forEach { builder.appendLine("› $it") }
    }
    return builder.toString().trimEnd()
}

private fun overallLabel(overall: DiagnosticOverallState): String = when (overall) {
    DiagnosticOverallState.PASSED -> "SYSTEM HEALTHY"
    DiagnosticOverallState.BLOCKED -> "SYSTEM CHECK BLOCKED"
    is DiagnosticOverallState.FAILED -> "FAILURE AT ${overall.component}"
    DiagnosticOverallState.CHECKING -> "CHECKING"
}

private fun copyDiagnosticLog(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("LINKO Diagnostic Report", text))
}
