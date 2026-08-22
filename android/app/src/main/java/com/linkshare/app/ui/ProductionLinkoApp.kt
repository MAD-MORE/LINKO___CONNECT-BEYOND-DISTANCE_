package com.linkshare.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.viewmodel.LinkShareViewModel

@Composable
fun ProductionLinkoApp(
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = LinkoBackground) {
        when (state.mode) {
            AppMode.Client -> ProductionReceiverRoot(
                state = state,
                viewModel = viewModel,
                onRequestVpnPermission = onRequestVpnPermission
            )
            AppMode.Host -> ProductionProviderRoot(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun ProductionReceiverRoot(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    when {
        state.connectionPhase.name == "Connected" ||
            state.connectionPhase.name == "Requesting" ||
            state.connectionPhase.name == "Handshaking" ||
            state.connectionPhase.name == "Retrying" -> {
            ReceiverProgressScreen(
                state = state,
                onBack = { viewModel.disconnect() },
                onDisconnect = { viewModel.disconnect() }
            )
        }
        else -> ReceiverHomeScreen(
            state = state,
            onFriendSelected = { friend ->
                if (!state.hasVpnPermission) onRequestVpnPermission()
                else viewModel.connectToFriend(friend)
            },
            onUsage = viewModel::openUsage,
            onSettings = viewModel::openSettings
        )
    }
}

@Composable
private fun ProductionProviderRoot(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel
) {
    ProviderSharingScreen(
        state = state,
        onToggle = viewModel::toggleHostSharing,
        onApprove = viewModel::approveIncomingRequest,
        onDeny = viewModel::denyIncomingRequest
    )
}
