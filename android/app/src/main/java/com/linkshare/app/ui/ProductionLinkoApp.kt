package com.linkshare.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.viewmodel.LinkShareViewModel

@Composable
fun ProductionLinkoApp(
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = LinkoBackground) {
        PrototypeFlow(
            state = state,
            viewModel = viewModel,
            onRequestVpnPermission = onRequestVpnPermission
        )
    }
}
