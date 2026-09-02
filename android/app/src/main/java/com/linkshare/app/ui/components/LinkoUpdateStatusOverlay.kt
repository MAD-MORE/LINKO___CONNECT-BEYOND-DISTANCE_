package com.linkshare.app.ui.components

import androidx.compose.runtime.Composable
import com.linkshare.app.update.LinkoUpdateManager

/**
 * Update progress is intentionally kept out of the main app UI.
 * StartupUpdateGate handles mandatory startup updates, while Settings owns
 * the user-facing update controls.
 */
@Composable
fun LinkoUpdateStatusOverlay(updateManager: LinkoUpdateManager) {
    // Intentionally empty. Update controls now live in Settings -> Updates.
}
