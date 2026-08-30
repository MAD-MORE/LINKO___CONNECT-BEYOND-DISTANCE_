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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** LINKO Diagnostic Center with an explicit build marker for update testing. */
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

    val displayedResults = liveResults.ifEmpty { results }
    val overall = LinkoDiagnosticCenter.reduce(displayedResults)
    val progress = (completedChecks.toFloat() / DiagnosticCenterViewModel.TOTAL_CHECKS).coerceIn(0f, 1f)
    val passed = displayedResults.count { it.status == DiagnosticStatus.PASS }
    val failed = displayedResults.count { it.status == DiagnosticStatus.FAIL }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("MAD-MORE", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Text("LINKO DIAGNOSTIC CENTER", style = MaterialTheme.typography.headlineSmall)
        Text("REAL SYSTEM VERIFICATION", style = MaterialTheme.typography.labelMedium)
        Text("UPDATE TEST • BUILD 1261", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when {
                                running -> "SCANNING LINKO"
                                complete -> "DIAGNOSTICS COMPLETE"
                                else -> "SYSTEM READY"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
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
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(7.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (running) "$completedChecks / ${DiagnosticCenterViewModel.TOTAL_CHECKS} checks complete"
                    else if (complete) "${DiagnosticCenterViewModel.TOTAL_CHECKS} / ${DiagnosticCenterViewModel.TOTAL_CHECKS} checks processed"
                    else "9 system checks ready",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(displayedResults, key = { it.name }) { result -> DiagnosticRow(result) }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            when {
                running -> "○ CHECKING SYSTEM — $completedChecks/${DiagnosticCenterViewModel.TOTAL_CHECKS}"
                complete && overall is DiagnosticOverallState.CONNECTED -> "✓ DIAGNOSTICS PASSED — LINKO IS READY"
                complete -> "✕ DIAGNOSTICS COMPLETE — ${failed} CHECK(S) NEED ATTENTION"
                overall is DiagnosticOverallState.NOT_READY -> "✕ NOT READY — ${overall.failedComponent}"
                else -> "○ READY FOR SYSTEM CHECK"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onRunDiagnostics()
                viewModel.runDiagnostics()
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("CHECKING…")
            } else {
                Text(if (complete) "RUN AGAIN" else "RUN DIAGNOSTICS")
            }
        }
    }
}

@Composable
private fun DiagnosticRow(result: DiagnosticResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.titleSmall)
                if (result.detail.isNotBlank()) Text(result.detail, style = MaterialTheme.typography.bodySmall)
                result.latencyMs?.let { Text("${it} ms", style = MaterialTheme.typography.labelSmall) }
            }
            Text(
                when (result.status) {
                    DiagnosticStatus.PASS -> "✓"
                    DiagnosticStatus.FAIL -> "✕"
                    DiagnosticStatus.CHECKING -> "…"
                    DiagnosticStatus.WAITING -> "○"
                    DiagnosticStatus.SKIPPED -> "–"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
