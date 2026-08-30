package com.linkshare.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.update.LinkoUpdateManager
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub

@Composable
fun LinkoUpdateStatusOverlay(updateManager: LinkoUpdateManager) {
    val state by updateManager.state.collectAsStateWithLifecycle()
    val status = state.status
    val active = status == LinkoUpdateManager.UpdateStatus.Checking || status == LinkoUpdateManager.UpdateStatus.UpdateAvailable || status == LinkoUpdateManager.UpdateStatus.Downloading || status == LinkoUpdateManager.UpdateStatus.DownloadComplete || status == LinkoUpdateManager.UpdateStatus.Verifying || status == LinkoUpdateManager.UpdateStatus.Installing
    LinkoCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkTransferAnimation(active, state.progressPercent / 100f, status == LinkoUpdateManager.UpdateStatus.Installed || status == LinkoUpdateManager.UpdateStatus.UpToDate, status == LinkoUpdateManager.UpdateStatus.Error, Modifier.size(72.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("LINKO UPDATE NETWORK", color = Green, fontSize = 12.sp, fontFamily = JetBrainsMono)
                Text(
                    when (status) {
                        LinkoUpdateManager.UpdateStatus.Checking -> "CONNECTING TO LINKO UPDATE NETWORK"
                        LinkoUpdateManager.UpdateStatus.UpdateAvailable -> "NEW LINKO BUILD FOUND"
                        LinkoUpdateManager.UpdateStatus.Downloading -> "DOWNLOADING LINKO UPDATE"
                        LinkoUpdateManager.UpdateStatus.DownloadComplete -> "UPDATE RECEIVED"
                        LinkoUpdateManager.UpdateStatus.Verifying -> "VERIFYING LINKO PACKAGE"
                        LinkoUpdateManager.UpdateStatus.Installing -> "INSTALLING LINKO"
                        LinkoUpdateManager.UpdateStatus.Installed -> "LINKO UPDATED"
                        LinkoUpdateManager.UpdateStatus.Error -> "UPDATE CHECK FAILED"
                        LinkoUpdateManager.UpdateStatus.UpToDate -> "LINKO IS UP TO DATE"
                        LinkoUpdateManager.UpdateStatus.Idle -> "AUTOMATIC UPDATE DETECTION READY"
                    }, color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono
                )
                Text("Current: ${state.installedVersionName} (${state.installedVersionCode})", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
                state.latestVersionCode?.let { Text("Latest: ${state.latestVersionName ?: "unknown"} ($it)", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono) }
            }
        }
        if (status == LinkoUpdateManager.UpdateStatus.Downloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = state.progressPercent / 100f, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("${state.progressPercent}%  ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
        }
        state.errorMessage?.let { Text(it, color = TextPrimary, fontSize = 10.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when (status) {
                LinkoUpdateManager.UpdateStatus.UpdateAvailable -> PrimaryButton("UPDATE NOW", { updateManager.startUpdate() }, color = Blue, modifier = Modifier.fillMaxWidth())
                LinkoUpdateManager.UpdateStatus.Downloading -> PrimaryButton("CANCEL", { updateManager.cancelUpdate() }, color = Blue, modifier = Modifier.fillMaxWidth())
                LinkoUpdateManager.UpdateStatus.Error -> PrimaryButton("RETRY", { updateManager.retry() }, color = Blue, modifier = Modifier.fillMaxWidth())
                LinkoUpdateManager.UpdateStatus.Installing -> Unit
                else -> PrimaryButton("CHECK FOR UPDATES", { updateManager.checkAndOfferUpdate() }, color = Blue, modifier = Modifier.fillMaxWidth(), enabled = !active)
            }
        }
    }
}

@Composable
private fun NetworkTransferAnimation(active: Boolean, progress: Float, success: Boolean, failure: Boolean, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "linko-network")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(if (active) 900 else 1800), RepeatMode.Restart), label = "packet-phase")
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.34f
        val nodes = listOf(Offset(center.x, center.y - radius), Offset(center.x - radius * 0.9f, center.y + radius * 0.55f), Offset(center.x + radius * 0.9f, center.y + radius * 0.55f))
        nodes.forEach { drawLine(Blue.copy(alpha = if (failure) 0.25f else 0.75f), center, it, strokeWidth = 2.2f, cap = StrokeCap.Round) }
        val pulse = if (success) 1f else 0.7f + 0.3f * kotlin.math.sin(phase * Math.PI * 2).toFloat().coerceAtLeast(0f)
        drawCircle(Blue.copy(alpha = 0.18f), radius = radius * 0.52f * pulse, center = center)
        val nodeColor = if (failure) TextSub else if (success) Green else Blue
        drawCircle(nodeColor, radius = 6f, center = center)
        nodes.forEach { drawCircle(nodeColor, radius = 4f, center = it) }
        if (active && !success && !failure) {
            val t = ((phase + progress * 0.35f) % 1f)
            val from = nodes[(phase * nodes.size).toInt().coerceIn(0, nodes.lastIndex)]
            drawCircle(Blue, radius = 3.5f, center = Offset(from.x + (center.x - from.x) * t, from.y + (center.y - from.y) * t))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format("%.1f MB", bytes / 1048576.0)
}
