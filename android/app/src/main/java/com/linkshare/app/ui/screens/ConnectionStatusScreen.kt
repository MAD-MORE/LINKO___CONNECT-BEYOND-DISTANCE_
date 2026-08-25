package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.linkshare.app.ui.theme.JetBrainsMono

@Composable
fun ConnectionStatusScreen(
    onConnected: () -> Unit,
    onFailed: () -> Unit,
) {
    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase == LinkoConnectionPhase.Connected) onConnected()
    }

    val color = when (state.phase) {
        LinkoConnectionPhase.Connected -> Green
        LinkoConnectionPhase.Failed -> Red
        LinkoConnectionPhase.Idle -> TextSub
        else -> Blue
    }
    val label = when (state.phase) {
        LinkoConnectionPhase.Idle -> "READY"
        LinkoConnectionPhase.Connecting -> "CONNECTING"
        LinkoConnectionPhase.Authenticating -> "AUTH"
        LinkoConnectionPhase.Signaling -> "SIGNALING"
        LinkoConnectionPhase.Establishing -> "ESTABLISHING"
        LinkoConnectionPhase.Securing -> "SECURING"
        LinkoConnectionPhase.Routing -> "ROUTING"
        LinkoConnectionPhase.Connected -> "CONNECTED"
        LinkoConnectionPhase.Failed -> "FAILED"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ring(color, 190.dp, pulse = state.phase != LinkoConnectionPhase.Idle && state.phase != LinkoConnectionPhase.Connected, label = label)
        Spacer(Modifier.height(22.dp))
        Text("LINKO CONNECTION", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(state.detail, color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)

        if (state.phase == LinkoConnectionPhase.Failed) {
            Spacer(Modifier.height(18.dp))
            LinkoCard {
                Text("FAILED AT THIS STAGE", color = Red, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(state.error ?: state.detail, color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono)
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("RETURN TO CONNECTION", onFailed, color = Red, outline = true)
        }
    }
}
