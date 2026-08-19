package com.linkshare.app.tunnel

import android.content.Context
import android.content.Intent
import com.linkshare.app.vpn.LinkShareVpnService

class TunnelCoordinator(private val context: Context) {
    fun startVpnTunnel() {
        // TODO: Inject real peer keys, session token, relay endpoint, and allowed IPs.
        context.startService(Intent(context, LinkShareVpnService::class.java))
    }

    fun stopVpnTunnel() {
        context.stopService(Intent(context, LinkShareVpnService::class.java))
    }
}
