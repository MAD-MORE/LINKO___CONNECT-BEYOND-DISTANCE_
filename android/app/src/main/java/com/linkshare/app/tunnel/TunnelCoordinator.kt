package com.linkshare.app.tunnel

import android.content.Context
import android.content.Intent
import android.util.Log
import com.linkshare.app.vpn.LinkShareVpnService

class TunnelCoordinator(private val context: Context) {
    /**
     * Starts the receiver VPN only when the signaling layer has supplied
     * authenticated endpoint and session credentials.
     */
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

    /**
     * Legacy UI compatibility. The old mock UI does not have authenticated
     * tunnel credentials yet, so it must not start a VPN with invented values.
     * The real signaling success path must call the credentialed overload.
     */
    @Deprecated("Use the credentialed startVpnTunnel overload after signaling completes")
    fun startVpnTunnel() {
        Log.w(TAG, "Ignoring legacy VPN start request: session credentials are not available")
    }

    fun stopVpnTunnel() {
        context.stopService(Intent(context, LinkShareVpnService::class.java))
    }

    private companion object {
        const val TAG = "TunnelCoordinator"
    }
}
