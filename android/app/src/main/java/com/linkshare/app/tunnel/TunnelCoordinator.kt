package com.linkshare.app.tunnel

import android.content.Context
import android.content.Intent
import com.linkshare.app.vpn.LinkShareVpnService

class TunnelCoordinator(private val context: Context) {
    fun startVpnTunnel(
        peerHost: String,
        peerPort: Int,
        sessionId: String,
        sessionKey: ByteArray
    ) {
        require(peerHost.isNotBlank()) { "peerHost is required" }
        require(peerPort in 1..65535) { "peerPort is invalid" }
        require(sessionId.isNotBlank()) { "sessionId is required" }
        require(sessionKey.size == 32) { "sessionKey must be 32 bytes" }

        val intent = Intent(context, LinkShareVpnService::class.java)
            .putExtra(LinkShareVpnService.EXTRA_PEER_HOST, peerHost)
            .putExtra(LinkShareVpnService.EXTRA_PEER_PORT, peerPort)
            .putExtra(LinkShareVpnService.EXTRA_SESSION_ID, sessionId)
            .putExtra(LinkShareVpnService.EXTRA_ROLE, LinkShareVpnService.ROLE_RECEIVER)
            .putExtra(LinkShareVpnService.EXTRA_SESSION_KEY, sessionKey.copyOf())
        context.startService(intent)
    }

    fun stopVpnTunnel() {
        context.stopService(Intent(context, LinkShareVpnService::class.java))
    }
}
