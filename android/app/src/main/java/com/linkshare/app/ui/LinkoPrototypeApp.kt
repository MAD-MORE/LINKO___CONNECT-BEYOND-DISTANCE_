package com.linkshare.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.viewmodel.LinkShareViewModel

/**
 * Prototype-first shell. The production ViewModel remains the source of truth;
 * only the presentation of the provider's active-sharing state changes.
 */
@Composable
fun LinkoPrototypeApp(
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.mode.name == "Host" && state.hostSharingEnabled) {
        PrototypeProviderActiveScreen(
            state = state,
            onStopSharing = viewModel::toggleHostSharing
        )
    } else {
        LinkShareApp(
            viewModel = viewModel,
            onRequestVpnPermission = onRequestVpnPermission
        )
    }
}
