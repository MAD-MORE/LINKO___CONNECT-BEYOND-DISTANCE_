package com.linkshare.app.diagnostics

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Diagnostic center UI backed by the real diagnostic controller.
 * The existing callback is retained for compatibility, while the ViewModel
 * now executes the real device probes when the button is pressed.
 */
@Composable
fun DiagnosticCenterScreen(
    results: List<DiagnosticResult>,
    onRunDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DiagnosticCenterViewModel = viewModel()
    val liveResults by viewModel.results.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val displayedResults = liveResults.ifEmpty { results }
    val overall = LinkoDiagnosticCenter.reduce(displayedResults)

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("MAD-MORE", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        Text("LINKO DIAGNOSTIC CENTER", style = MaterialTheme.typography.headlineSmall)
        Text("REAL SYSTEM VERIFICATION", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(20.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(displayedResults, key = { it.name }) { result ->
                DiagnosticRow(result)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when (overall) {
                DiagnosticOverallState.CHECKING -> "○ CHECKING SYSTEM"
                DiagnosticOverallState.CONNECTED -> "✓ CONNECTED — LINKO IS READY"
                is DiagnosticOverallState.NOT_READY -> "✕ NOT READY — ${overall.failedComponent}"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                onRunDiagnostics()
                viewModel.runDiagnostics()
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (running) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text("RUN DIAGNOSTICS")
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
