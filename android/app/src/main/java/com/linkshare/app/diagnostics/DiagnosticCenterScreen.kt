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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshare.app.update.LinkoUpdateManager
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.LinkoCard

/** LINKO Diagnostic Center with live diagnostics and updater telemetry. */
@Composable
fun DiagnosticCenterScreen(results: List<DiagnosticResult>, onRunDiagnostics: () -> Unit, updateManager: LinkoUpdateManager, modifier: Modifier = Modifier) {
    val viewModel: DiagnosticCenterViewModel = viewModel()
    val liveResults by viewModel.results.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val completedChecks by viewModel.completedChecks.collectAsStateWithLifecycle()
    val currentCheck by viewModel.currentCheck.collectAsStateWithLifecycle()
    val complete by viewModel.complete.collectAsStateWithLifecycle()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val pulseTransition = rememberInfiniteTransition(label = "diagnostic-network")
    val pulse by pulseTransition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "network-pulse")
    val displayedResults = liveResults.ifEmpty { results }
    val overall = LinkoDiagnosticCenter.reduce(displayedResults)
    val progress = (completedChecks.toFloat() / DiagnosticCenterViewModel.TOTAL_CHECKS).coerceIn(0f, 1f)
    val passed = displayedResults.count { it.status == DiagnosticStatus.PASS }
    val failed = displayedResults.count { it.status == DiagnosticStatus.FAIL }
    val networkColor = MaterialTheme.colorScheme.primary
    val latestIsNewer = (updateState.latestVersionCode ?: updateState.installedVersionCode) > updateState.installedVersionCode
    val updateActive = updateState.status in setOf(LinkoUpdateManager.UpdateStatus.Checking, LinkoUpdateManager.UpdateStatus.UpdateAvailable, LinkoUpdateManager.UpdateStatus.Downloading, LinkoUpdateManager.UpdateStatus.DownloadComplete, LinkoUpdateManager.UpdateStatus.Verifying, LinkoUpdateManager.UpdateStatus.Installing)
    val updateFailed = updateState.status == LinkoUpdateManager.UpdateStatus.Error
    val updateRateLimited = updateState.status == LinkoUpdateManager.UpdateStatus.RateLimited
    val showUpdateSection = latestIsNewer || updateActive || updateFailed || updateRateLimited

    Column(modifier.fillMaxSize().drawBehind {
        val nodes = listOf(0.12f to 0.14f, 0.50f to 0.08f, 0.88f to 0.16f, 0.20f to 0.42f, 0.78f to 0.48f, 0.48f to 0.78f)
        val points = nodes.map { (x, y) -> androidx.compose.ui.geometry.Offset(size.width * x, size.height * y) }
        listOf(0 to 1, 1 to 2, 0 to 3, 1 to 4, 2 to 4, 3 to 5, 4 to 5).forEach { (a, b) -> drawLine(networkColor.copy(alpha = 0.08f * pulse), points[a], points[b], strokeWidth = 2f) }
        points.forEachIndexed { index, point -> drawCircle(networkColor.copy(alpha = 0.08f + 0.07f * pulse), if (index == 5) 14f else 9f, point); drawCircle(networkColor.copy(alpha = 0.20f * pulse), if (index == 5) 22f else 15f, point, style = Stroke(width = 2f)) }
    }.background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.Top) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("LINKO", style = MaterialTheme.typography.labelLarge); Text("Diagnostic Center", style = MaterialTheme.typography.headlineSmall); Text("LIVE SYSTEM MONITOR", style = MaterialTheme.typography.labelSmall) }
            Crossfade(targetState = running, label = "header-live") { active -> Icon(if (active) Icons.Default.Refresh else Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth().animateContentSize()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(targetState = running to complete, label = "diagnostic-header") { (isRunning, isComplete) -> Text(if (isRunning) "System scan in progress" else if (isComplete) "System scan complete" else "System ready", style = MaterialTheme.typography.titleLarge) }
                        Spacer(Modifier.height(4.dp))
                        Text(if (running) currentCheck.ifBlank { "Preparing system checks…" } else if (complete) "$passed passed • $failed failed" else "Run a live verification of LINKO", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp)); Spacer(Modifier.height(8.dp))
                Text(if (running) "$completedChecks / ${DiagnosticCenterViewModel.TOTAL_CHECKS} checks" else "${DiagnosticCenterViewModel.TOTAL_CHECKS} checks ready", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(visible = showUpdateSection, modifier = Modifier.animateContentSize()) {
            Column(Modifier.fillMaxWidth()) {
                StartupStyledUpdateCard(updateState, { updateManager.checkAndOfferUpdate() }, { updateManager.startUpdate() }, { updateManager.retry() })
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(displayedResults, key = { it.name }) { DiagnosticRow(it) } }
        Spacer(Modifier.height(10.dp))
        Crossfade(targetState = running to complete, label = "diagnostic-summary") { (isRunning, isComplete) -> Text(if (isRunning) "○ CHECKING SYSTEM — $completedChecks/${DiagnosticCenterViewModel.TOTAL_CHECKS}" else if (isComplete && overall is DiagnosticOverallState.CONNECTED) "✓ DIAGNOSTICS PASSED — LINKO IS READY" else if (isComplete) "✕ DIAGNOSTICS COMPLETE — $failed CHECK(S) NEED ATTENTION" else "○ READY FOR SYSTEM CHECK", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onRunDiagnostics(); viewModel.runDiagnostics() }, enabled = !running, modifier = Modifier.fillMaxWidth()) { if (running) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.size(10.dp)); Text("CHECKING…") } else Text(if (complete) "RUN AGAIN" else "RUN DIAGNOSTICS") }
    }
}

@Composable
private fun StartupStyledUpdateCard(state: LinkoUpdateManager.UpdateState, onCheck: () -> Unit, onUpdate: () -> Unit, onRetry: () -> Unit) {
    val status = state.status
    val active = status in setOf(LinkoUpdateManager.UpdateStatus.Checking, LinkoUpdateManager.UpdateStatus.Downloading, LinkoUpdateManager.UpdateStatus.Verifying, LinkoUpdateManager.UpdateStatus.Installing)
    val success = status == LinkoUpdateManager.UpdateStatus.UpToDate || status == LinkoUpdateManager.UpdateStatus.Installed
    val failure = status == LinkoUpdateManager.UpdateStatus.Error || status == LinkoUpdateManager.UpdateStatus.RateLimited
    val transition = rememberInfiniteTransition(label = "diagnostic-update-pulse")
    val pulse by transition.animateFloat(0.65f, 1f, infiniteRepeatable(tween(if (active) 850 else 1600), RepeatMode.Reverse), label = "diagnostic-update-pulse-value")

    LinkoCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.30f
                val nodeColor = when { failure -> TextSub; success -> Green; else -> Blue }
                drawCircle(nodeColor.copy(alpha = 0.10f * pulse), radius = radius * 1.8f, center = center)
                drawCircle(nodeColor.copy(alpha = 0.18f), radius = radius, center = center)
                drawCircle(nodeColor, radius = 6f, center = center)
                drawLine(nodeColor.copy(alpha = 0.55f), center, androidx.compose.ui.geometry.Offset(center.x, center.y - radius * 1.9f), strokeWidth = 2f)
                drawLine(nodeColor.copy(alpha = 0.55f), center, androidx.compose.ui.geometry.Offset(center.x - radius * 1.6f, center.y + radius), strokeWidth = 2f)
                drawLine(nodeColor.copy(alpha = 0.55f), center, androidx.compose.ui.geometry.Offset(center.x + radius * 1.6f, center.y + radius), strokeWidth = 2f)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("LINKO UPDATE NETWORK", color = Green, fontSize = 12.sp, fontFamily = JetBrainsMono)
                Text(
                    when (status) {
                        LinkoUpdateManager.UpdateStatus.Checking -> "CHECKING FOR LINKO UPDATES"
                        LinkoUpdateManager.UpdateStatus.UpdateAvailable -> "NEW LINKO BUILD FOUND"
                        LinkoUpdateManager.UpdateStatus.Downloading -> "DOWNLOADING LINKO UPDATE"
                        LinkoUpdateManager.UpdateStatus.DownloadComplete -> "UPDATE RECEIVED"
                        LinkoUpdateManager.UpdateStatus.Verifying -> "VERIFYING LINKO PACKAGE"
                        LinkoUpdateManager.UpdateStatus.Installing -> "INSTALLING LINKO"
                        LinkoUpdateManager.UpdateStatus.Installed -> "LINKO UPDATED"
                        LinkoUpdateManager.UpdateStatus.UpToDate -> "LINKO IS UP TO DATE"
                        LinkoUpdateManager.UpdateStatus.RateLimited -> "GITHUB TEMPORARILY RATE LIMITED"
                        LinkoUpdateManager.UpdateStatus.Error -> "UPDATE CHECK FAILED"
                        LinkoUpdateManager.UpdateStatus.Idle -> "AUTOMATIC UPDATE DETECTION READY"
                    },
                    color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono
                )
                Text("Current: ${state.installedVersionName} (${state.installedVersionCode})", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                state.latestVersionCode?.let {
                    Text(
                        if (state.usingCachedData) "Last confirmed: ${state.latestVersionName ?: "unknown"} ($it)" else "Latest: ${state.latestVersionName ?: "unknown"} ($it)",
                        color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono
                    )
                }
            }
        }
        if (active) {
            Spacer(Modifier.height(10.dp))
            if (status == LinkoUpdateManager.UpdateStatus.Downloading) {
                LinearProgressIndicator(progress = { state.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("${state.progressPercent}% downloaded", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(state.statusMessage.ifBlank { "Synchronizing with the LINKO update network…" }, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
            }
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = TextPrimary, fontSize = 10.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.height(10.dp))
        when (status) {
            LinkoUpdateManager.UpdateStatus.UpdateAvailable -> PrimaryButton("UPDATE NOW", onUpdate, color = Blue)
            LinkoUpdateManager.UpdateStatus.Error, LinkoUpdateManager.UpdateStatus.RateLimited -> PrimaryButton("TRY AGAIN", onRetry, color = Blue)
            else -> PrimaryButton("CHECK FOR UPDATES", onCheck, color = Blue, enabled = !active, loading = status == LinkoUpdateManager.UpdateStatus.Checking)
        }
    }
}

@Composable
private fun DiagnosticRow(result: DiagnosticResult) { Card(Modifier.fillMaxWidth().animateContentSize()) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(result.name, style = MaterialTheme.typography.titleSmall); if (result.detail.isNotBlank()) Text(result.detail, style = MaterialTheme.typography.bodySmall); result.latencyMs?.let { Text("${it} ms", style = MaterialTheme.typography.labelSmall) } }; Crossfade(targetState = result.status, label = "diagnostic-icon-${result.name}") { status -> Icon(when (status) { DiagnosticStatus.PASS -> Icons.Default.CheckCircle; DiagnosticStatus.FAIL -> Icons.Default.Close; DiagnosticStatus.CHECKING -> Icons.Default.Warning; DiagnosticStatus.WAITING -> Icons.Default.Refresh; DiagnosticStatus.SKIPPED -> Icons.Default.Close }, contentDescription = status.name, modifier = Modifier.size(24.dp)) } } } }
