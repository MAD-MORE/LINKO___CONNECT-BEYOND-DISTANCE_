package com.linkshare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.PrototypeScreen
import com.linkshare.app.viewmodel.LinkShareViewModel

/**
 * Production navigation shell for the frozen LINKO prototype.
 *
 * The production flow must use the dedicated prototype screen implementations
 * rather than the former generic debug/state card. Business logic remains in
 * LinkShareViewModel; this layer only maps state to the frozen UI screens.
 */
@Composable
fun PrototypeFlow(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onBack: () -> Unit,
    onRequestVpnPermission: () -> Unit
) {
    when (state.screen) {
        PrototypeScreen.RxSelectFriend,
        PrototypeScreen.RxRequest,
        PrototypeScreen.RxWaiting,
        PrototypeScreen.RxApproved,
        PrototypeScreen.RxConnecting,
        PrototypeScreen.RxDirectPath,
        PrototypeScreen.RxRelayFallback,
        PrototypeScreen.Connected,
        PrototypeScreen.ConnectionLost,
        PrototypeScreen.Reconnecting,
        PrototypeScreen.NetworkSwitching,
        PrototypeScreen.SessionExpired -> ReceiverPrototypeScreen(
            state = state,
            viewModel = viewModel,
            onBack = onBack,
            onRequestVpnPermission = onRequestVpnPermission
        )

        PrototypeScreen.ProviderIncoming,
        PrototypeScreen.ProviderAuthorization,
        PrototypeScreen.ProviderSharingSetup,
        PrototypeScreen.ProviderSharingActive,
        PrototypeScreen.ProviderLiveUsage -> ProviderPrototypeScreen(
            state = state,
            viewModel = viewModel,
            onBack = onBack
        )

        PrototypeScreen.Welcome,
        PrototypeScreen.CreateAccount,
        PrototypeScreen.Verify,
        PrototypeScreen.Profile,
        PrototypeScreen.RegisterDevice,
        PrototypeScreen.Permissions,
        PrototypeScreen.Friends,
        PrototypeScreen.FindFriends,
        PrototypeScreen.FriendProfile,
        PrototypeScreen.RequestSent,
        PrototypeScreen.IncomingRequest,
        PrototypeScreen.BlockedRemoved,
        PrototypeScreen.HomeEngine,
        PrototypeScreen.NetworkQuality,
        PrototypeScreen.Usage,
        PrototypeScreen.SessionDetails,
        PrototypeScreen.SessionHistory,
        PrototypeScreen.DeviceIdentity,
        PrototypeScreen.SecurityEngine,
        PrototypeScreen.KeyRevoked,
        PrototypeScreen.Privacy,
        PrototypeScreen.DataRetention,
        PrototypeScreen.Settings,
        PrototypeScreen.DeleteAccount -> ExistingPrototypeScreen(
            state = state,
            viewModel = viewModel,
            onBack = onBack
        )
    }
}

@Composable
private fun ReceiverPrototypeScreen(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onBack: () -> Unit,
    onRequestVpnPermission: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = LinkoParchment) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("LINKO", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF66BFB5))
            Text(
                when (state.screen) {
                    PrototypeScreen.Connected -> "Connected"
                    PrototypeScreen.RxConnecting -> "Connecting"
                    PrototypeScreen.RxWaiting -> "Waiting for approval"
                    PrototypeScreen.RxRequest -> "Requesting access"
                    PrototypeScreen.RxDirectPath -> "Direct connection"
                    PrototypeScreen.RxRelayFallback -> "Relay connection"
                    else -> "Connect beyond distance"
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF101417)
            )
            Text(
                state.eventMessage ?: "Your protected LINKO connection is managed securely.",
                fontSize = 16.sp,
                color = Color(0xFF101417).copy(alpha = .72f)
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBack, Modifier.weight(1f)) { Text("Back") }
                Button(
                    onClick = {
                        when (state.screen) {
                            PrototypeScreen.Connected -> viewModel.disconnect()
                            PrototypeScreen.RxRequest,
                            PrototypeScreen.RxWaiting -> state.activeFriend?.let(viewModel::connectToFriend)
                            else -> {
                                if (!state.hasVpnPermission) onRequestVpnPermission()
                                else state.activeFriend?.let(viewModel::connectToFriend)
                            }
                        }
                    },
                    Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BFB5), contentColor = Color(0xFF101417))
                ) { Text(if (state.screen == PrototypeScreen.Connected) "Disconnect" else "Connect") }
            }
        }
    }
}

@Composable
private fun ProviderPrototypeScreen(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onBack: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = LinkoParchment) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("LINKO", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF66BFB5))
            Text("Share your connection", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF101417))
            Text(
                state.eventMessage ?: "Only approved friends can use your connection.",
                fontSize = 16.sp,
                color = Color(0xFF101417).copy(alpha = .72f)
            )
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF101417), RoundedCornerShape(28.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Provider", color = LinkoParchment, fontWeight = FontWeight.Bold)
                Text(
                    if (state.screen == PrototypeScreen.ProviderSharingActive) "Sharing active" else "Ready to share",
                    color = Color(0xFF66BFB5), fontSize = 20.sp, fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBack, Modifier.weight(1f)) { Text("Back") }
                Button(
                    onClick = viewModel::toggleHostSharing,
                    Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BFB5), contentColor = Color(0xFF101417))
                ) { Text("Share Connection") }
            }
        }
    }
}

@Composable
private fun ExistingPrototypeScreen(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onBack: () -> Unit
) {
    // Preserve the existing prototype screen implementation for non-connection
    // flows. This wrapper intentionally does not expose debug state as the UI.
    PrototypeConnectionScreens(state = state, viewModel = viewModel, onBack = onBack)
}
