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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.update.LinkoUpdateManager

/** Real LINKO system diagnostics: evidence, dependency blocking, and live runtime telemetry. */
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
    val pulseTransition = rememberInfiniteTransition(label = "diagnostic-network")
    val pulse by pulseTransition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "network-pulse")

    val displayedResults = LinkoDiagnosticCenter.blockedResults(liveResults.ifEmpty { results })
    val overall = LinkoDiagnosticCenter.overall(displayedResults)
    val firstFailure = LinkoDiagnosticCenter.firstFailure(displayedResults)
    val progress = (completedChecks.toFloat() / DiagnosticCenterViewModel.TOTAL_CHECKS).coerceIn(0f, 1f)
    val passed = displayedResults.count { it.status == DiagnosticStatus.PASS }
    val failed = displayedResults.count { it.status == DiagnosticStatus.FAIL }
    val blocked = displayedResults.count { it.status == DiagnosticStatus.BLOCKED }
    val pending = displayedResults.count { it.status == DiagnosticStatus.WAITING || it.status == DiagnosticStatus.CHECKING }
    val networkColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val nodes = listOf(0.08f to 0.12f, 0.50f to 0.07f, 0.92f to 0.13f, 0.16f to 0.44f, 0.84f to 0.47f, 0.50f to 0.82f)
                val points = nodes.map { (x, y) -> androidx.compose.ui.geometry.Offset(size.width * x, size.height * y) }
                listOf(0 to 1, 1 to 2, 0 to 3, 1 to 4, 2 to 4, 3 to 5, 4 to 5).forEach { (a, b) -> drawLine(networkColor.copy(alpha = 0.07f * pulse), points[a], points[b], strokeWidth = 2f) }
                points.forEachIndexed { index, point ->
                    drawCircle(networkColor.copy(alpha = 0.07f + 0.06f * pulse), if (index == 5) 14f else 8f, point)
                    drawCircle(networkColor.copy(alpha = 0.18f * pulse), if (index == 5) 23f else 14f, point, style = Stroke(width = 2f))
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("LINKO", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 12.sp)
                Text("Diagnostic Center", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
                Text("LIVE SYSTEM TRACE", color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp)
            }
            Crossfade(targetState = running, label = "header-state") { active ->
                Surface(shape = MaterialTheme.shapes.large, color = if (active) Blue.copy(alpha = 0.12f) else Green.copy(alpha = 0.12f)) {
                    Icon(if (active) Icons.Default.Refresh else Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.padding(10.dp).size(26.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        DiagnosticOverviewCard(
            running = running,
            complete = complete,
            currentCheck = currentCheck,
            completedChecks = completedChecks,
            progress = progress,
            passed = passed,
            failed = failed,
            blocked = blocked,
            pending = pending,
            overall = overall,
        )

        Spacer(Modifier.height(10.dp))
        LiveTelemetryCard(telemetry)

        AnimatedVisibility(visible = firstFailure != null) {
            firstFailure?.let {
                Spacer(Modifier.height(10.dp))
                FailureFocusCard(it)
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(displayedResults, key = { it.name }) { result ->
                DiagnosticResultRow(
                    result = result,
                    expanded = expanded.value.contains(result.name),
                    onToggle = {
                        expanded.value = expanded.value.toMutableSet().apply {
                            if (!add(result.name)) remove(result.name)
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        StartupStyledUpdateCard(updateState, updateManager)
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            if (running) "SCANNING LINKO" else if (complete) "RUN DIAGNOSTICS AGAIN" else "RUN FULL DIAGNOSTICS",
            onClick = { onRunDiagnostics(); viewModel.runDiagnostics() },
            color = Blue,
            enabled = !running,
            loading = running,
        )
    }
}

@Composable
private fun DiagnosticOverviewCard(
    running: Boolean,
    complete: Boolean,
    currentCheck: String,
    completedChecks: Int,
    progress: Float,
    passed: Int,
    failed: Int,
    blocked: Int,
    pending: Int,
    overall: DiagnosticOverallState,
) {
    val headline = when (overall) {
        DiagnosticOverallState.PASSED -> "SYSTEM HEALTHY"
        DiagnosticOverallState.BLOCKED -> "SYSTEM CHECK BLOCKED"
        is DiagnosticOverallState.FAILED -> "FAILURE AT ${overall.component.uppercase()}"
        DiagnosticOverallState.CHECKING -> if (running) "SYSTEM SCAN IN PROGRESS" else "READY TO SCAN"
    }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(targetState = headline, label = "diagnostic-headline") { Text(it, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            running -> currentCheck.ifBlank { "Preparing diagnostic probes…" }
                            complete -> "$passed passed · $failed failed · $blocked blocked · $pending pending"
                            else -> "The scan follows LINKO's real runtime dependency chain."
                        },
                        color = TextSub,
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                    )
                }
                Text("${(progress * 100).toInt()}%", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp))
            Spacer(Modifier.height(6.dp))
            Text("$completedChecks / ${DiagnosticCenterViewModel.TOTAL_CHECKS} probes", color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LiveTelemetryCard(snapshot: DiagnosticTelemetrySnapshot) {
    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        Text("LIVE RUNTIME TELEMETRY", color = Green, fontFamily = JetBrainsMono, fontSize = 10.sp)
        Spacer(Modifier.height(7.dp))
        Text("ENGINE  ${snapshot.enginePhase} · ${snapshot.engineDetail}", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp)
        Text("REALTIME ${if (snapshot.realtimeConnected) "CONNECTED" else "DISCONNECTED"}${snapshot.realtimeError?.let { " · $it" } ?: ""}", color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp)
        Text("CHANNELS  ${snapshot.realtimeChannels.ifEmpty { listOf("none") }.joinToString()}", color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp)
        Text("VPN       ${if (snapshot.vpnRunning) "RUNNING" else "STOPPED"} · TX ${snapshot.vpnTxPackets}/${snapshot.vpnTxBytes}B · RX ${snapshot.vpnRxPackets}/${snapshot.vpnRxBytes}B", color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp)
        snapshot.vpnError?.let { Text("VPN ERROR $it", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp) }
        if (snapshot.engineTrace.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text("TRACE", color = TextSub, fontFamily = JetBrainsMono, fontSize = 9.sp)
            snapshot.engineTrace.takeLast(6).forEach { Text("› $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
        }
    }
}

@Composable
private fun FailureFocusCard(result: DiagnosticResult) {
    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(25.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("FIRST FAILURE", color = MaterialTheme.colorScheme.error, fontFamily = JetBrainsMono, fontSize = 10.sp)
                Text(result.name, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(result.detail, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp)
        result.errorType?.let { Text("TYPE  $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
        result.errorMessage?.let { Text("ERROR $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
        result.latencyMs?.let { Text("TIME  ${it} ms", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
    }
}

@Composable
private fun DiagnosticResultRow(result: DiagnosticResult, expanded: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(result.status)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.name, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 10.sp)
                    result.latencyMs?.let { Text("  ${it}ms", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                }
                Text(result.detail.ifBlank { "No diagnostic evidence yet" }, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
                result.blockedBy?.let { Text("BLOCKED BY  $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
            }
            IconButton(onClick = onToggle) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 46.dp, end = 15.dp, bottom = 12.dp)) {
                result.errorType?.let { Text("ERROR TYPE  $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
                result.errorMessage?.let { Text("ERROR MSG   $it", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
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
    Icon(icon, contentDescription = status.name, modifier = Modifier.size(20.dp))
}

@Composable
private fun StartupStyledUpdateCard(state: LinkoUpdateManager.UpdateState, manager: LinkoUpdateManager) {
    val active = state.status in setOf(LinkoUpdateManager.UpdateStatus.Checking, LinkoUpdateManager.UpdateStatus.Downloading, LinkoUpdateManager.UpdateStatus.Verifying, LinkoUpdateManager.UpdateStatus.Installing)
    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(9.dp))
            Column(Modifier.weight(1f)) {
                Text("UPDATE NETWORK", color = Green, fontFamily = JetBrainsMono, fontSize = 9.sp)
                Text(state.statusMessage.ifBlank { "Automatic update detection ready" }, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 9.sp)
                Text("Current ${state.installedVersionName} (${state.installedVersionCode})", color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp)
            }
        }
        if (active) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { state.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        state.errorMessage?.let { Text(it, color = TextSub, fontFamily = JetBrainsMono, fontSize = 8.sp) }
        Spacer(Modifier.height(6.dp))
        when (state.status) {
            LinkoUpdateManager.UpdateStatus.UpdateAvailable -> PrimaryButton("UPDATE NOW", manager::startUpdate, color = Blue)
            LinkoUpdateManager.UpdateStatus.Error, LinkoUpdateManager.UpdateStatus.RateLimited -> PrimaryButton("RETRY UPDATE CHECK", manager::retry, color = Blue)
            else -> PrimaryButton("CHECK FOR UPDATES", manager::checkAndOfferUpdate, color = Blue, enabled = !active, loading = state.status == LinkoUpdateManager.UpdateStatus.Checking)
        }
    }
}
