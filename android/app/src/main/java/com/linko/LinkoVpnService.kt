package com.linko

import android.content.Intent
import android.net.VpnService

/**
 * Linko's Android VPN entry point.
 *
 * This foundation intentionally does not route traffic yet. Packet handling,
 * tunnel establishment, authorization and provider forwarding are implemented
 * only in their approved Linko phases.
 */
class LinkoVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Phase foundation: no traffic interception or forwarding yet.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Future phases will close the tunnel and release resources here.
        super.onDestroy()
    }
}
