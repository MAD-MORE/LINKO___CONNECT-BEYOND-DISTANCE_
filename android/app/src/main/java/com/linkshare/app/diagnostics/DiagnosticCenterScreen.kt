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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshare.app.update.LinkoUpdateManager

/** LINKO Diagnostic Center with live diagnostics and updater telemetry. */
@Composable
fun DiagnosticCenterScreen(
    results: List<DiagnosticResult>,
    onRunDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DiagnosticCenterViewModel = viewModel()
    val liveResults by viewModel.results.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val completedChecks by viewModel.completedChecks.collectAsStateWithLifecycle()
    val currentCheck by viewModel.currentCheck.collectAsStateWithLifecycle()
    val complete by viewModel.complete.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateManager = remember(context) { LinkoUpdateManager(context) }
    val updateState by updateManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(updateManager) { updateManager.checkAndOfferUpdate() }

    val pulseTransition = rememberInfiniteTransition(label = "diagnostic-network")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "network-pulse"
    )
    val displayedResults = liveResults.ifEmpty { results }
    val overall = LinkoDiagnosticCenter.reduce(displayedResults)
    val progress = (completedChecks.toFloat() / DiagnosticCenterViewModel.TOTAL_CHECKS).coerceIn(0f, 1f)
    val passed = displayedResults.count { it.status == DiagnosticStatus.PASS }
    val failed = displayedResults.count { it.status == DiagnosticStatus.FAIL }
    val networkColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val nodes = listOf(0.12f to 0.14f, 0.50f to 0.08f, 0.88f to 0.16f, 0.20f to 0.42f, 0.78f to 0.48f, 0.48f to 0.78f)
                val points = nodes.map { (x, y) -> androidx.compose.ui.geometry.Offset(size.width * x, size.height * y) }
                val links = listOf(0 to 1, 1 to 2, 0 to 3, 1 to 4, 2 to 4, 3 to 5, 4 to 5)
                links.forEach { (a, b) -> drawLine(networkColor.copy(alpha = 0.08f * pulse), points[a], points[b], strokeWidth = 2f) }
                points.forEachIndexed { index, point ->
                    drawCircle(networkColor.copy(alpha = 0.08f + 0.07f * pulse), if (index == 5) 14f else 9f, point)
                    drawCircle(networkColor.copy(alpha = 0.20f * pulse), if (index == 5) 22f else 15f, point, style = Stroke(width = 2f))
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("MAD-MORE", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Text("LINKO DIAGNOSTIC CENTER", style = MaterialTheme.typography.headlineSmall)
        Text("REAL SYSTEM VERIFICATION", style = MaterialTheme.typography.labelMedium)
        Text("LIVE DIAGNOSTICS • UPDATE TELEMETRY", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedContent(targetState = running to complete, label = "diagnostic-header") { (isRunning, isComplete) ->
                            Text(
                                when { isRunning -> "SCANNING LINKO"; isComplete -> "DIAGNOSTICS COMPLETE"; else -> "SYSTEM READY" },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                running -> currentCheck.ifBlank { "Preparing system checks…" }
                                complete -> "Scan finished — $passed verified, $failed failed"
                                else -> "Run a full verification of LINKO services"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (running) "$completedChecks / ${DiagnosticCenterViewModel.TOTAL_CHECKS} checks complete" else if (complete) "${DiagnosticCenterViewModel.TOTAL_CHECKS} / ${DiagnosticCenterViewModel.TOTAL_CHECKS} checks processed" else "9 system checks ready",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        UpdateStatusCard(updateState, onCheck = { updateManager.checkAndOfferUpdate() }, onUpdate = { updateManager.startUpdate() }, onRetry = { updateManager.retry() })

        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(displayedResults, key = { it.name }) { result -> DiagnosticRow(result) }
        }

        Spacer(Modifier.height(10.dp))
        Crossfade(targetState = running to complete, label = "diagnostic-summary") { (isRunning, isComplete) ->
            Text(
                when {
                    isRunning -> "○ CHECKING SYSTEM — $completedChecks/${DiagnosticCenterViewModel.TOTAL_CHECKS}"
                    isComplete && overall is DiagnosticOverallState.CONNECTED -> "✓ DIAGNOSTICS PASSED — LINKO IS READY"
                    isComplete -> "✕ DIAGNOSTICS COMPLETE — $failed CHECK(S) NEED ATTENTION"
                    overall is DiagnosticOverallState.NOT_READY -> "✕ NOT READY — ${overall.failedComponent}"
                    else -> "○ READY FOR SYSTEM CHECK"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onRunDiagnostics(); viewModel.runDiagnostics() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("CHECKING…")
            } else Text(if (complete) "RUN AGAIN" else "RUN DIAGNOSTICS")
        }
    }
}

@Composable
private fun UpdateStatusCard(
    state: LinkoUpdateManager.UpdateState,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
    onRetry: () -> Unit
) {
    val active = state.status in setOf(LinkoUpdateManager.UpdateStatus.Checking, LinkoUpdateManager.UpdateStatus.Downloading, LinkoUpdateManager.UpdateStatus.Verifying, LinkoUpdateManager.UpdateStatus.Installing)
    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text("UPDATE STATUS", style = MaterialTheme.typography.titleMedium)
                        Text(state.statusMessage.ifBlank { "UPDATE CHECK READY" }, style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onCheck, enabled = !active) { Icon(Icons.Default.Refresh, contentDescription = "Check for updates") }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current ${state.installedVersionName} (${state.installedVersionCode})", style = MaterialTheme.typography.bodySmall)
                Text("Latest ${state.latestVersionName ?: "—"} (${state.latestVersionCode ?: "—"})", style = MaterialTheme.typography.bodySmall)
            }
            AnimatedVisibility(visible = state.status == LinkoUpdateManager.UpdateStatus.Downloading || state.status == LinkoUpdateManager.UpdateStatus.Verifying || state.status == LinkoUpdateManager.UpdateStatus.Installing) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    if (state.status == LinkoUpdateManager.UpdateStatus.Downloading) {
                        LinearProgressIndicator(progress = { state.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${state.progressPercent}% downloaded", style = MaterialTheme.typography.labelSmall)
                    } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            AnimatedVisibility(visible = state.errorMessage != null) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(state.errorMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.status == LinkoUpdateManager.UpdateStatus.UpdateAvailable) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("UPDATE LINKO")
                }
            }
            if (state.status == LinkoUpdateManager.UpdateStatus.Error) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("RETRY UPDATE CHECK") }
            }
            if (state.status == LinkoUpdateManager.UpdateStatus.UpToDate) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("LINKO IS UP TO DATE", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(result: DiagnosticResult) {
    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.titleSmall)
                if (result.detail.isNotBlank()) Text(result.detail, style = MaterialTheme.typography.bodySmall)
                result.latencyMs?.let { Text("${it} ms", style = MaterialTheme.typography.labelSmall) }
            }
            Crossfade(targetState = result.status, label = "diagnostic-icon-${result.name}") { status ->
                val icon = when (status) {
                    DiagnosticStatus.PASS -> Icons.Default.CheckCircle
                    DiagnosticStatus.FAIL -> Icons.Default.Close
                    DiagnosticStatus.CHECKING -> Icons.Default.Warning
                    DiagnosticStatus.WAITING -> Icons.Default.Refresh
                    DiagnosticStatus.SKIPPED -> Icons.Default.Close
                }
                Icon(icon, contentDescription = status.name, modifier = Modifier.size(24.dp))
            }
        }
    }
}
