package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoSessionHistoryStore
import com.linkshare.app.ui.components.GlassCard
import com.linkshare.app.ui.components.InfoRow
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.StatusChip
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RealSessionHistoryScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { LinkoSessionHistoryStore.start(context) }
    val entries by LinkoSessionHistoryStore.entries.collectAsStateWithLifecycle()
    var expandedId by remember { mutableStateOf<String?>(null) }

    val successful = entries.count { it.status == "Connected" }
    val totalBytes = entries.sumOf { it.totalBytes }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Session History", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Every LINKO session, kept on this device", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
            }
            if (entries.isNotEmpty()) {
                IconButton(onClick = { LinkoSessionHistoryStore.clear(); expandedId = null }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history", tint = Red)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(accentColor = Blue) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCell("SESSIONS", entries.size.toString(), Blue, Modifier.weight(1f))
                SummaryCell("SUCCESSFUL", successful.toString(), Green, Modifier.weight(1f))
                SummaryCell("DATA", formatHistoryBytes(totalBytes), Yellow, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(14.dp))

        if (entries.isEmpty()) {
            LinkoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Blue, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NO SESSION HISTORY", color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Your next real connection will appear here automatically with its peer, role, timing, result and data usage.", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    val expanded = expandedId == entry.id
                    HistoryEntryCard(entry, expanded) {
                        expandedId = if (expanded) null else entry.id
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: LinkoSessionHistoryStore.HistoryEntry, expanded: Boolean, onClick: () -> Unit) {
    val statusColor = when (entry.status) {
        "Connected" -> Green
        "Failed", "Declined", "Revoked" -> Red
        "Expired", "Stopped", "Disconnected", "Interrupted" -> Yellow
        else -> Blue
    }
    val icon = when (entry.status) {
        "Connected" -> Icons.Default.CheckCircle
        "Failed", "Declined", "Revoked" -> Icons.Default.Error
        else -> Icons.Default.Link
    }

    LinkoCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.width(30.dp))
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.peerDisplayName, color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(entry.peerLinkoId?.let { "@${it.removePrefix("@")}" } ?: "LINKO peer", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.role.uppercase(), color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Text("  •  ${formatDate(entry.startedAt)}", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                }
            }
            StatusChip(entry.status.uppercase(), statusColor)
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HistoryMetric("DURATION", formatDuration(entry.durationMs))
            HistoryMetric("DATA", formatHistoryBytes(entry.totalBytes))
            HistoryMetric("ROLE", entry.role)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            InfoRow("STARTED", formatDateTime(entry.startedAt), "Local device time", Blue)
            Spacer(Modifier.height(9.dp))
            entry.endedAt?.let {
                InfoRow("ENDED", formatDateTime(it), "Session terminal timestamp", TextPrimary)
                Spacer(Modifier.height(9.dp))
            }
            InfoRow("DOWNLOADED", formatHistoryBytes(entry.bytesIn), "Inbound session traffic", Blue, true)
            Spacer(Modifier.height(9.dp))
            InfoRow("UPLOADED", formatHistoryBytes(entry.bytesOut), "Outbound session traffic", Green, true)
            entry.reason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(9.dp))
                InfoRow("OUTCOME DETAIL", it, "Engine-reported result", statusColor)
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryMetric(label: String, value: String) {
    Column {
        Text(label, color = TextSub, fontSize = 8.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextPrimary, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

private fun formatHistoryBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
        else -> "${seconds}s"
    }
}

private fun formatDate(timestamp: Long): String = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDateTime(timestamp: Long): String = SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
