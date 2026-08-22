package com.linkshare.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.PrototypeScreen
import com.linkshare.app.viewmodel.LinkShareViewModel

/** Routes the existing production shell into the frozen prototype state flow. */
@Composable
fun LinkoProductionApp(
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detailedFlow = state.screen != PrototypeScreen.Welcome && state.screen != PrototypeScreen.HomeEngine

    if (detailedFlow) {
        PrototypeFlow(
            state = state,
            viewModel = viewModel,
            onBack = { viewModel.setMode(state.mode) },
            onRequestVpnPermission = onRequestVpnPermission
        )
    } else {
        LinkShareApp(
            viewModel = viewModel,
            onRequestVpnPermission = onRequestVpnPermission
        )
    }
}
