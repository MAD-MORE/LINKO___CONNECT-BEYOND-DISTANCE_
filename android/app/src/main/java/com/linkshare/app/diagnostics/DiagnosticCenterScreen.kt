package com.linkshare.app.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshare.app.ui.theme.BG
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.update.LinkoUpdateManager

/** Live diagnostic dashboard backed by DiagnosticProbe. */
@Composable
fun DiagnosticCenterScreen(
    results: List<DiagnosticResult>,
    onRunDiagnostics: () -> Unit,
    updateManager: LinkoUpdateManager,
    modifier: Modifier = Modifier,
) {
    val diagnosticViewModel: DiagnosticCenterViewModel = viewModel()
    val liveResults by diagnosticViewModel.results.collectAsStateWithLifecycle()
    val running by diagnosticViewModel.running.collectAsStateWithLifecycle()
    val completedChecks by diagnosticViewModel.completedChecks.collectAsStateWithLifecycle()
    val currentCheck by diagnosticViewModel.currentCheck.collectAsStateWithLifecycle()
    val complete by diagnosticViewModel.complete.collectAsStateWithLifecycle()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    val displayed = LinkoDiagnosticCenter.blockedResults(liveResults.ifEmpty { results })
    val passed = displayed.count { it.status == DiagnosticStatus.PASS }
    val failed = displayed.count { it.status == DiagnosticStatus.FAIL }
    val blocked = displayed.count { it.status == DiagnosticStatus.BLOCKED }
    val progress = (completedChecks.toFloat() / DiagnosticCenterViewModel.TOTAL_CHECKS).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxSize().background(BG).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LINKO Diagnostic Center", color = TextPrimary)
                        Text(
                            when {
                                running -> currentCheck
                                complete -> "$passed passed · $failed failed · $blocked blocked"
                                else -> "Real subsystem checks. No simulated connection results."
                            },
                            color = TextSub,
                        )
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("$completedChecks / ${DiagnosticCenterViewModel.TOTAL_CHECKS}", color = TextSub)
                    }
                }
            }
            items(displayed, key = { it.name }) { result ->
                val open = expanded.contains(result.name)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(result.name, color = TextPrimary)
                            Text(statusLabel(result.status), color = statusColor(result.status))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(result.detail.ifBlank { "No diagnostic evidence yet" }, color = TextSub)
                        if (result.errorMessage != null) Text(result.errorMessage, color = Red)
                        Button(onClick = { expanded = expanded.toMutableSet().apply { if (open) remove(result.name) else add(result.name) } }) {
                            Text(if (open) "Hide details" else "Details")
                        }
                        if (open) {
                            result.errorType?.let { Text("Error type: $it", color = TextSub) }
                            result.blockedBy?.let { Text("Blocked by: $it", color = TextSub) }
                            result.latencyMs?.let { Text("Latency: ${it} ms", color = TextSub) }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("UPDATE NETWORK", color = Green)
                        Text(updateState.statusMessage.ifBlank { "Automatic update detection ready" }, color = TextPrimary)
                        Text("Current ${updateState.installedVersionName} (${updateState.installedVersionCode})", color = TextSub)
                        updateState.latestVersionName?.let { Text("Latest $it (${updateState.latestVersionCode ?: "?"})", color = Blue) }
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !running,
            onClick = { onRunDiagnostics(); diagnosticViewModel.runDiagnostics() },
        ) {
            Text(if (running) "RUNNING DIAGNOSTICS…" else "RUN FULL DIAGNOSTICS")
        }
    }
}

private fun statusLabel(status: DiagnosticStatus): String = when (status) {
    DiagnosticStatus.PASS -> "PASS"
    DiagnosticStatus.FAIL -> "FAILED"
    DiagnosticStatus.BLOCKED -> "BLOCKED"
    DiagnosticStatus.CHECKING -> "CHECKING"
    DiagnosticStatus.WAITING -> "WAITING"
}

private fun statusColor(status: DiagnosticStatus) = when (status) {
    DiagnosticStatus.PASS -> Green
    DiagnosticStatus.FAIL -> Red
    DiagnosticStatus.BLOCKED -> Red
    DiagnosticStatus.CHECKING -> Blue
    DiagnosticStatus.WAITING -> TextSub
}
