package com.linkshare.app.ui

import androidx.compose.runtime.Composable
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.viewmodel.LinkShareViewModel

/**
 * Single production entry into the frozen prototype UI.
 * Visual decisions live in FrozenPrototypeScreens.kt; transport/state stays in the ViewModel.
 */
@Composable
fun PrototypeFlow(
    state: ConnectionUiState,
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    FrozenPrototypeApp(
        state = state,
        viewModel = viewModel,
        onRequestVpnPermission = onRequestVpnPermission
    )
}
