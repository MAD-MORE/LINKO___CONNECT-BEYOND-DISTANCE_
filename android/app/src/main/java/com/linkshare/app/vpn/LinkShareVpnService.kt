package com.linkshare.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class LinkShareVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: Replace this placeholder with the real WireGuard-style tunnel adapter.
        // The backend team will provide signaling, relay, and NAT traversal endpoints.
        tunnelInterface = Builder()
            .setSession("LinkShare tunnel")
            .addAddress("10.48.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .establish()

        return START_STICKY
    }

    override fun onDestroy() {
        tunnelInterface?.close()
        tunnelInterface = null
        super.onDestroy()
    }
}
