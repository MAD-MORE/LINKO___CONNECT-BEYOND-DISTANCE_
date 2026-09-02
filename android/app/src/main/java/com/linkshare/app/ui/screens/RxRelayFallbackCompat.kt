package com.linkshare.app.ui.screens

import androidx.compose.runtime.Composable

/**
 * Compile-time compatibility shim for the legacy navigation symbol.
 * The active LINKO transport remains direct-only; no relay transport is started here.
 */
@Composable
fun RxRelayFallbackScreen(onContinue: () -> Unit) {
    ConnectionLostScreen(
        onReconnect = onContinue,
        onHome = onContinue
    )
}
