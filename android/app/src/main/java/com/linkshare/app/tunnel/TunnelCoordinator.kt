package com.linkshare.app.tunnel

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.linkshare.app.vpn.LinkShareVpnService

class TunnelCoordinator(private val context: Context) {
    fun startVpnTunnel(sessionId: String, peerId: String, sessionKey: ByteArray, relayEndpoint: String, relayToken: String) {
        require(sessionId.isNotBlank())
        require(peerId.isNotBlank())
        require(sessionKey.size == 32) { "LINKO session key must be 256 bits" }
        require(relayEndpoint.startsWith("wss://")) { "Relay endpoint must use WSS" }
        require(relayToken.isNotBlank())

        context.startService(Intent(context, LinkShareVpnService::class.java).apply {
            putExtra(LinkShareVpnService.EXTRA_SESSION_ID, sessionId)
            putExtra(LinkShareVpnService.EXTRA_PEER_ID, peerId)
            putExtra(LinkShareVpnService.EXTRA_SESSION_KEY, Base64.encodeToString(sessionKey, Base64.NO_WRAP))
            putExtra(LinkShareVpnService.EXTRA_RELAY_ENDPOINT, relayEndpoint)
            putExtra(LinkShareVpnService.EXTRA_RELAY_TOKEN, relayToken)
        })
    }

    fun stopVpnTunnel() {
        context.stopService(Intent(context, LinkShareVpnService::class.java))
    }
}
