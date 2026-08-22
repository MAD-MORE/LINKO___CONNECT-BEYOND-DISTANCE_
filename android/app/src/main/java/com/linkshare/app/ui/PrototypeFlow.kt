package com.linkshare.app.ui

import androidx.compose.runtime.Composable
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.PrototypeScreen
import com.linkshare.app.viewmodel.LinkShareViewModel

/** Production router: maps state to the existing frozen prototype screens. */
@Composable
fun PrototypeFlow(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onBack: () -> Unit,
    onRequestVpnPermission: () -> Unit
) {
    when (state.screen) {
        PrototypeScreen.RxSelectFriend,
        PrototypeScreen.Friends,
        PrototypeScreen.FindFriends,
        PrototypeScreen.FriendProfile -> ReceiverHomeScreen(
            state = state,
            onFriendSelected = viewModel::connectToFriend,
            onUsage = { viewModel.openUsage() },
            onSettings = { viewModel.openSettings() }
        )

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
        PrototypeScreen.SessionExpired -> ReceiverProgressScreen(
            state = state,
            onBack = onBack,
            onDisconnect = viewModel::disconnect
        )

        PrototypeScreen.ProviderIncoming,
        PrototypeScreen.IncomingRequest -> ProviderSharingScreen(
            state = state,
            onToggle = viewModel::toggleHostSharing,
            onApprove = viewModel::approveIncomingRequest,
            onDeny = viewModel::denyIncomingRequest
        )

        PrototypeScreen.ProviderAuthorization,
        PrototypeScreen.ProviderSharingSetup,
        PrototypeScreen.ProviderSharingActive,
        PrototypeScreen.ProviderLiveUsage -> ProviderSharingScreen(
            state = state,
            onToggle = viewModel::toggleHostSharing,
            onApprove = viewModel::approveIncomingRequest,
            onDeny = viewModel::denyIncomingRequest
        )

        PrototypeScreen.Usage,
        PrototypeScreen.NetworkQuality,
        PrototypeScreen.SessionDetails,
        PrototypeScreen.SessionHistory,
        PrototypeScreen.DeviceIdentity,
        PrototypeScreen.SecurityEngine,
        PrototypeScreen.KeyRevoked,
        PrototypeScreen.Privacy,
        PrototypeScreen.DataRetention,
        PrototypeScreen.Settings,
        PrototypeScreen.DeleteAccount,
        PrototypeScreen.Welcome,
        PrototypeScreen.CreateAccount,
        PrototypeScreen.Verify,
        PrototypeScreen.Profile,
        PrototypeScreen.RegisterDevice,
        PrototypeScreen.Permissions,
        PrototypeScreen.RequestSent,
        PrototypeScreen.BlockedRemoved,
        PrototypeScreen.HomeEngine -> ReceiverProgressScreen(
            state = state,
            onBack = onBack,
            onDisconnect = viewModel::disconnect
        )
    }
}
