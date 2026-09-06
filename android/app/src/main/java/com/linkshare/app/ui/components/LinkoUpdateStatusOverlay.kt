package com.linkshare.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.update.LinkoUpdateManager
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub

/**
 * Lightweight update center for startup/background checks.
 * It never blocks the main LINKO experience and gives the user explicit control
 * over downloading an available update.
 */
@Composable
fun LinkoUpdateStatusOverlay(updateManager: LinkoUpdateManager) {
    val state by updateManager.state.collectAsStateWithLifecycle()
    var dismissedVersion by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.latestVersionCode) {
        if (state.latestVersionCode != dismissedVersion) {
            // A newly discovered build is always allowed to surface again.
            if (state.latestVersionCode != null) dismissedVersion = null
        }
    }

    val visible = when (state.status) {
        LinkoUpdateManager.UpdateStatus.Checking,
        LinkoUpdateManager.UpdateStatus.UpdateAvailable,
        LinkoUpdateManager.UpdateStatus.Downloading,
        LinkoUpdateManager.UpdateStatus.Verifying,
        LinkoUpdateManager.UpdateStatus.Installing,
        LinkoUpdateManager.UpdateStatus.Installed,
        LinkoUpdateManager.UpdateStatus.Error,
        LinkoUpdateManager.UpdateStatus.RateLimited -> state.latestVersionCode != dismissedVersion || state.status in setOf(
            LinkoUpdateManager.UpdateStatus.Downloading,
            LinkoUpdateManager.UpdateStatus.Verifying,
            LinkoUpdateManager.UpdateStatus.Installing
        )
        else -> false
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 5.dp,
            shadowElevation = 8.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (state.status) {
                            LinkoUpdateManager.UpdateStatus.Installed -> Icons.Filled.CheckCircle
                            LinkoUpdateManager.UpdateStatus.Error,
                            LinkoUpdateManager.UpdateStatus.RateLimited -> Icons.Filled.Refresh
                            else -> Icons.Filled.Download
                        },
                        contentDescription = null,
                        tint = when (state.status) {
                            LinkoUpdateManager.UpdateStatus.Installed -> Green
                            LinkoUpdateManager.UpdateStatus.Error,
                            LinkoUpdateManager.UpdateStatus.RateLimited -> Red
                            else -> Blue
                        }
                    )
                    Spacer(Modifier.padding(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LINKO UPDATE", color = TextPrimary, fontSize = 13.sp)
                        Text(
                            headline(state),
                            color = TextSub,
                            fontSize = 11.sp
                        )
                    }
                    if (state.status == LinkoUpdateManager.UpdateStatus.UpdateAvailable ||
                        state.status == LinkoUpdateManager.UpdateStatus.Installed ||
                        state.status == LinkoUpdateManager.UpdateStatus.Error ||
                        state.status == LinkoUpdateManager.UpdateStatus.RateLimited
                    ) {
                        IconButton(onClick = {
                            dismissedVersion = state.latestVersionCode
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextMuted)
                        }
                    }
                }

                when (state.status) {
                    LinkoUpdateManager.UpdateStatus.Checking -> {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Checking quietly in the background…", color = TextMuted, fontSize = 10.sp)
                    }
                    LinkoUpdateManager.UpdateStatus.UpdateAvailable -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Version ${state.latestVersionName ?: "new"} is ready. You can keep using LINKO while it downloads.",
                            color = TextSub,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = updateManager::startUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text("UPDATE LINKO")
                        }
                    }
                    LinkoUpdateManager.UpdateStatus.Downloading -> {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progressPercent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${state.progressPercent.coerceIn(0, 100)}%", color = Blue, fontSize = 11.sp)
                            if (state.totalBytes > 0) Text(
                                "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = updateManager::cancelUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text("CANCEL DOWNLOAD")
                        }
                    }
                    LinkoUpdateManager.UpdateStatus.Verifying -> {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Checking package integrity before installation…", color = TextMuted, fontSize = 10.sp)
                    }
                    LinkoUpdateManager.UpdateStatus.Installing -> {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Android is opening the installer. Your LINKO data stays in place.", color = TextMuted, fontSize = 10.sp)
                    }
                    LinkoUpdateManager.UpdateStatus.Installed -> {
                        Spacer(Modifier.height(8.dp))
                        Text("Update complete. LINKO is ready.", color = Green, fontSize = 11.sp)
                    }
                    LinkoUpdateManager.UpdateStatus.Error,
                    LinkoUpdateManager.UpdateStatus.RateLimited -> {
                        Spacer(Modifier.height(6.dp))
                        Text(state.errorMessage.orEmpty(), color = TextSub, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = updateManager::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("CHECK AGAIN")
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun headline(state: LinkoUpdateManager.UpdateState): String = when (state.status) {
    LinkoUpdateManager.UpdateStatus.Checking -> "Looking for a newer build"
    LinkoUpdateManager.UpdateStatus.UpdateAvailable -> "A newer build is available"
    LinkoUpdateManager.UpdateStatus.Downloading -> "Downloading safely"
    LinkoUpdateManager.UpdateStatus.Verifying -> "Verifying the package"
    LinkoUpdateManager.UpdateStatus.Installing -> "Installing update"
    LinkoUpdateManager.UpdateStatus.Installed -> "Update installed"
    LinkoUpdateManager.UpdateStatus.Error -> "Update check needs attention"
    LinkoUpdateManager.UpdateStatus.RateLimited -> "Update service is temporarily busy"
    else -> ""
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024L
    if (kb < 1024L) return "$kb KB"
    return "${kb / 1024L} MB"
}
