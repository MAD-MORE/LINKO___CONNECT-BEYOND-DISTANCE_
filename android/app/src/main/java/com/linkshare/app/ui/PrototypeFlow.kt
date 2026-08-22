package com.linkshare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Production state router for the frozen 41-screen prototype contract. */
@Composable
fun PrototypeFlow(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onBack: () -> Unit,
    onRequestVpnPermission: () -> Unit
) {
    val screen = state.screen
    val title = when (screen) {
        PrototypeScreen.Welcome -> "Welcome"
        PrototypeScreen.CreateAccount -> "Create account"
        PrototypeScreen.Verify -> "Verify your account"
        PrototypeScreen.Profile -> "Your profile"
        PrototypeScreen.RegisterDevice -> "Register device"
        PrototypeScreen.Permissions -> "Permissions"
        PrototypeScreen.Friends -> "Friends"
        PrototypeScreen.FindFriends -> "Find friends"
        PrototypeScreen.FriendProfile -> "Friend profile"
        PrototypeScreen.RequestSent -> "Request sent"
        PrototypeScreen.IncomingRequest -> "Incoming request"
        PrototypeScreen.BlockedRemoved -> "Friend removed"
        PrototypeScreen.HomeEngine -> "LINKO"
        PrototypeScreen.RxSelectFriend -> "Choose a friend"
        PrototypeScreen.RxRequest -> "Requesting access"
        PrototypeScreen.RxWaiting -> "Waiting for approval"
        PrototypeScreen.RxApproved -> "Approved"
        PrototypeScreen.RxConnecting -> "Connecting"
        PrototypeScreen.RxDirectPath -> "Direct path"
        PrototypeScreen.RxRelayFallback -> "Relay fallback"
        PrototypeScreen.Connected -> "Connected"
        PrototypeScreen.NetworkQuality -> "Network quality"
        PrototypeScreen.Usage -> "Usage"
        PrototypeScreen.ProviderIncoming -> "Incoming request"
        PrototypeScreen.ProviderAuthorization -> "Authorization"
        PrototypeScreen.ProviderSharingSetup -> "Sharing setup"
        PrototypeScreen.ProviderSharingActive -> "Sharing active"
        PrototypeScreen.ProviderLiveUsage -> "Live usage"
        PrototypeScreen.SessionDetails -> "Session details"
        PrototypeScreen.SessionHistory -> "Session history"
        PrototypeScreen.ConnectionLost -> "Connection lost"
        PrototypeScreen.Reconnecting -> "Reconnecting"
        PrototypeScreen.NetworkSwitching -> "Switching network"
        PrototypeScreen.SessionExpired -> "Session expired"
        PrototypeScreen.DeviceIdentity -> "Device identity"
        PrototypeScreen.SecurityEngine -> "Security"
        PrototypeScreen.KeyRevoked -> "Key revoked"
        PrototypeScreen.Privacy -> "Privacy"
        PrototypeScreen.DataRetention -> "Data retention"
        PrototypeScreen.Settings -> "Settings"
        PrototypeScreen.DeleteAccount -> "Delete account"
    }

    val detail = when (screen) {
        PrototypeScreen.RxRequest -> state.eventMessage ?: "Your request is being sent securely."
        PrototypeScreen.RxWaiting -> "Waiting for your friend to approve the connection."
        PrototypeScreen.RxApproved -> "Approval received. Preparing the protected tunnel."
        PrototypeScreen.RxConnecting -> "Negotiating the best available connection path."
        PrototypeScreen.RxDirectPath -> "A direct path is available."
        PrototypeScreen.RxRelayFallback -> state.eventMessage ?: "Direct path unavailable. Relay fallback is active."
        PrototypeScreen.Connected -> state.eventMessage ?: "Traffic is routed through your approved friend."
        PrototypeScreen.ProviderSharingActive -> "Your device is available to approved friends."
        PrototypeScreen.ProviderLiveUsage -> state.eventMessage ?: "A protected session is currently using your connection."
        PrototypeScreen.ConnectionLost -> "The tunnel was interrupted. No new traffic will be sent until it recovers."
        PrototypeScreen.Reconnecting -> "LINKO is attempting to restore the protected path."
        PrototypeScreen.NetworkSwitching -> "The underlying network changed. Re-establishing the tunnel."
        PrototypeScreen.SessionExpired -> "This connection session has expired and must be requested again."
        PrototypeScreen.KeyRevoked -> "The session key was revoked. A new authorized session is required."
        else -> state.eventMessage ?: "This screen is part of the frozen LINKO prototype flow."
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F2E8)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text("LINKO", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF66BFB5))
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF101417))
            Text(detail, fontSize = 16.sp, lineHeight = 23.sp, color = Color(0xFF101417).copy(alpha = .72f))
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF101417), RoundedCornerShape(28.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Prototype state", color = Color(0xFFF7F2E8), fontWeight = FontWeight.Bold)
                Text(screen.name, color = Color(0xFF66BFB5), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("Connection: ${state.connectionPhase.name}", color = Color(0xFFF7F2E8).copy(alpha = .7f))
            }
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2A15F), contentColor = Color(0xFF101417))
                ) { Text("Back") }
                Button(
                    onClick = {
                        when (screen) {
                            PrototypeScreen.RxRequest, PrototypeScreen.RxWaiting -> {
                                state.activeFriend?.let { viewModel.connectToFriend(it) }
                            }
                            PrototypeScreen.Connected -> viewModel.disconnect()
                            PrototypeScreen.ProviderSharingSetup, PrototypeScreen.ProviderSharingActive -> viewModel.toggleHostSharing()
                            PrototypeScreen.IncomingRequest, PrototypeScreen.ProviderIncoming, PrototypeScreen.ProviderAuthorization -> viewModel.approveIncomingRequest()
                            else -> viewModel.setMode(if (state.mode == AppMode.Host) AppMode.Client else AppMode.Host)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BFB5), contentColor = Color(0xFF101417))
                ) { Text(if (screen == PrototypeScreen.Connected) "Disconnect" else "Continue") }
            }
        }
    }
}
