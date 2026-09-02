package com.linkshare.app.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.linkshare.app.provider.LinkoProviderService

/**
 * Android Quick Settings Tile allowing 1-tap toggling of Provider sharing
 * directly from the Android pull-down notification shade.
 */
class LinkoTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isSharing = LinkoProviderService.isRunning

        if (isSharing) {
            // Stop sharing
            val stopIntent = Intent(this, LinkoProviderService::class.java).apply {
                action = LinkoProviderService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            // Start sharing
            val startIntent = Intent(this, LinkoProviderService::class.java)
            startForegroundService(startIntent)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isSharing = LinkoProviderService.isRunning

        if (isSharing) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "LINKO Sharing"
            tile.subtitle = "Active"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "LINKO Share"
            tile.subtitle = "Tap to Share"
        }
        tile.updateTile()
    }
}
