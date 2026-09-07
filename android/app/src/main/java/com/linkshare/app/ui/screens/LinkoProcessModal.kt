package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineConnectionState
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.delay

private enum class ProcessModalKind { Progress, Success, Error }

private data class ProcessModalModel(
    val kind: ProcessModalKind,
    val title: String,
    val message: String,
    val canRetry: Boolean,
    val logLines: List<String>,
    val eventKey: String,
)

private fun LinkoEngineConnectionState.toProcessModalModel(): ProcessModalModel? {
    val phase = phase
    val detail = detail.trim().ifBlank { "Working…" }
    val error = error?.trim().orEmpty()
    val session = sessionId?.trim().orEmpty()
    val logLines = buildList {
        add("phase=${phase.name}")
        add("detail=${detail.take(220)}")
        if (error.isNotBlank()) add("error=${error.take(220)}")
        if (peerDisplayName?.isNotBlank() == true) add("peer=${peerDisplayName!!.take(120)}")
        add("role=${if (isProvider) "provider" else "receiver"}")
        add("session=${if (session.isBlank()) "none" else session.take(80)}")
    }

    return when (phase) {
        LinkoConnectionPhase.Idle -> null
        LinkoConnectionPhase.Connected -> ProcessModalModel(
            kind = ProcessModalKind.Success,
            title = "CONNECTED",
            message = detail.ifBlank { "LINKO connection is active." },
            canRetry = false,
            logLines = logLines,
            eventKey = "connected|$session|$detail",
        )
        LinkoConnectionPhase.Failed -> ProcessModalModel(
            kind = ProcessModalKind.Error,
            title = "CONNECTION FAILED",
            message = error.ifBlank { detail.ifBlank { "LINKO could not complete the connection." } },
            canRetry = !isProvider,
            logLines = logLines,
            eventKey = "failed|$session|$error|$detail",
        )
        else -> ProcessModalModel(
            kind = ProcessModalKind.Progress,
            title = when (phase) {
                LinkoConnectionPhase.Connecting -> "CONNECTING…"
                LinkoConnectionPhase.Authenticating -> "AUTHORIZING…"
                LinkoConnectionPhase.Signaling -> "SIGNALING…"
                LinkoConnectionPhase.Establishing -> "ESTABLISHING…"
                LinkoConnectionPhase.Securing -> "SECURING…"
                LinkoConnectionPhase.Routing -> "STARTING ROUTE…"
                LinkoConnectionPhase.Connected -> "CONNECTED"
                LinkoConnectionPhase.Failed -> "CONNECTION FAILED"
                LinkoConnectionPhase.Idle -> "LINKO"
            },
            message = detail,
            canRetry = false,
            logLines = logLines,
            eventKey = "progress|${phase.name}|$session|$detail",
        )
    }
}

@Composable
fun LinkoProcessModalHost(modifier: Modifier = Modifier) {
    val state by LinkoEngineBridge.connection.collectAsStateCompat()
    val model = remember(state) { state.toProcessModalModel() }
    var dismissedKey by remember { mutableStateOf<String?>(null) }
    var expandedLogs by remember { mutableStateOf(false) }

    LaunchedEffect(model?.eventKey) {
        expandedLogs = false
        val current = model ?: return@LaunchedEffect
        dismissedKey = null
        if (current.kind == ProcessModalKind.Success) {
            delay(1_700L)
            dismissedKey = current.eventKey
        }
    }

    if (model != null && model.eventKey != dismissedKey) {
        LinkoProcessModal(
            model = model,
            expandedLogs = expandedLogs,
            onViewLogs = { expandedLogs = !expandedLogs },
            onDismiss = { dismissedKey = model.eventKey },
            onRetry = {
                dismissedKey = model.eventKey
                LinkoEngineBridge.reconnect()
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun LinkoProcessModal(
    model: ProcessModalModel,
    expandedLogs: Boolean,
    onViewLogs: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (model.kind) {
        ProcessModalKind.Error -> Red
        ProcessModalKind.Success -> Green
        ProcessModalKind.Progress -> Blue
    }

    Dialog(
        onDismissRequest = { if (model.kind != ProcessModalKind.Progress) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = model.kind != ProcessModalKind.Progress,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = GradientMid.copy(alpha = 0.985f),
                tonalElevation = 8.dp,
                shadowElevation = 22.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (model.kind == ProcessModalKind.Progress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = accent,
                                )
                            } else {
                                Text(
                                    text = if (model.kind == ProcessModalKind.Success) "✓" else "!",
                                    color = accent,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMono,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMono,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                model.message,
                                color = TextSub,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                fontFamily = JetBrainsMono,
                            )
                        }
                    }

                    if (model.kind == ProcessModalKind.Error || expandedLogs) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.07f), Blue.copy(alpha = 0.04f))))
                                .border(1.dp, accent.copy(alpha = 0.13f), RoundedCornerShape(14.dp))
                                .padding(10.dp),
                        ) {
                            Column {
                                Text(
                                    "PROCESS LOG",
                                    color = accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMono,
                                )
                                Spacer(Modifier.height(6.dp))
                                model.logLines.forEach { line ->
                                    Text(
                                        line,
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        lineHeight = 12.sp,
                                        fontFamily = JetBrainsMono,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (model.kind == ProcessModalKind.Error) {
                            TextAction("VIEW LOGS", accent, onViewLogs)
                            Spacer(Modifier.width(8.dp))
                            if (model.canRetry) {
                                FilledAction("RETRY", accent, onRetry)
                            }
                            Spacer(Modifier.width(8.dp))
                            TextAction("CLOSE", TextSub, onDismiss)
                        } else if (model.kind == ProcessModalKind.Success) {
                            TextAction("CLOSE", TextSub, onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono)
    }
}

@Composable
private fun FilledAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.24f), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono)
    }
}

@Composable
private fun LinkoEngineBridge.connection.collectAsStateCompat(): androidx.compose.runtime.State<LinkoEngineConnectionState> {
    return androidx.lifecycle.compose.collectAsStateWithLifecycle(LinkoEngineBridge.connection)
}
