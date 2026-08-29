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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standalone diagnostics UI. It intentionally does not modify existing LINKO
 * connection screens or networking behavior; production adapters can feed
 * DiagnosticResult values into this screen incrementally.
 */
@Composable
fun DiagnosticCenterScreen(
    results: List<DiagnosticResult>,
    onRunDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overall = LinkoDiagnosticCenter.reduce(results)

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
            items(results, key = { it.name }) { result ->
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
        Button(onClick = onRunDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("RUN DIAGNOSTICS")
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
