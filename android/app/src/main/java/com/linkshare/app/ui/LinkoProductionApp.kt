package com.linkshare.app.ui

import androidx.compose.runtime.Composable
import com.linkshare.app.viewmodel.LinkShareViewModel

/** Production entry point for the frozen LINKO prototype. */
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
