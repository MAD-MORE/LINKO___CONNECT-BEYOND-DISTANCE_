package com.linkshare.app.ui

import androidx.compose.runtime.Composable
import com.linkshare.app.viewmodel.LinkShareViewModel

/** Entry point for the production implementation of the frozen LINKO prototype. */
@Composable
fun LinkoProductionApp(
    viewModel: LinkShareViewModel,
    onRequestVpnPermission: () -> Unit
) {
    ProductionLinkoApp(
        viewModel = viewModel,
        onRequestVpnPermission = onRequestVpnPermission
    )
}
