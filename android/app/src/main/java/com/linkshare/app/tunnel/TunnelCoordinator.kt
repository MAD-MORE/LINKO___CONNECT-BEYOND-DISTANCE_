package com.linkshare.app.tunnel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.linkshare.app.vpn.LinkShareVpnService

class TunnelCoordinator(private val context: Context) {
    /**
     * Starts the receiver VPN. The Connect action originates from LINKO's foreground UI, so use
     * the service start path directly; the VpnService promotes itself to foreground immediately.
     * This avoids a startForegroundService lifecycle race before the receiver can publish its
     * direct UDP candidates.
     */
    fun startDirectVpnTunnel(sessionId: String, sessionKey: ByteArray) {
        require(sessionId.isNotBlank()) { "sessionId is required" }
        require(sessionKey.size == 32) { "sessionKey must be 32 bytes" }

        val intent = Intent(context, LinkShareVpnService::class.java)
            .putExtra(LinkShareVpnService.EXTRA_SESSION_ID, sessionId)
            .putExtra(LinkShareVpnService.EXTRA_ROLE, LinkShareVpnService.ROLE_RECEIVER)
            .putExtra(LinkShareVpnService.EXTRA_SESSION_KEY, sessionKey.copyOf())

        Log.i(TAG, "START_RECEIVER_VPN session=$sessionId api=${Build.VERSION.SDK_INT}")
        context.startService(intent)
    }

    /** Legacy endpoint-based entry point retained for source compatibility; direct mode rejects server endpoints. */
    @Deprecated("Use startDirectVpnTunnel; LINKO no longer uses relay/server endpoints")
    fun startVpnTunnel(peerHost: String, peerPort: Int, sessionId: String, sessionKey: ByteArray) {
        Log.w(TAG, "Ignoring legacy endpoint $peerHost:$peerPort; LINKO uses direct P2P negotiation")
        startDirectVpnTunnel(sessionId, sessionKey)
    }

    @Deprecated("Use startDirectVpnTunnel")
    fun startVpnTunnel() {
        Log.w(TAG, "Ignoring legacy VPN start request: session credentials are not available")
    }

    fun stopVpnTunnel() {
        context.stopService(Intent(context, LinkShareVpnService::class.java))
    }

    private companion object { const val TAG = "TunnelCoordinator" }
}
