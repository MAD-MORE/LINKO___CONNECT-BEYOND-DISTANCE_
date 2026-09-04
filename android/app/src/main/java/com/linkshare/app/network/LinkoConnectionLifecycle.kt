package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.vpn.LinkShareVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single authoritative UI-facing stop/restart boundary.
 *
 * LinkoEngineBridge currently tears down its realtime collector as part of its generic
 * connection reset. This wrapper deliberately stops the actual services first, publishes
 * the server-side terminal state independently, then reinitializes the engine so a subsequent
 * connection starts from a clean local state instead of inheriting a dead/stale lifecycle.
 */
object LinkoConnectionLifecycle {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun stop(context: Context) {
        val app = context.applicationContext
        val sessionId = LinkoEngineBridge.connection.value.sessionId

        runCatching { app.stopService(Intent(app, LinkShareVpnService::class.java)) }
        runCatching { app.stopService(Intent(app, LinkoProviderService::class.java)) }

        if (!sessionId.isNullOrBlank()) {
            ioScope.launch {
                runCatching {
                    val api = LinkoDeviceControlApi(app)
                    val current = api.session(sessionId).state.trim().lowercase()
                    if (current !in setOf("failed", "denied", "expired", "revoked", "disconnected")) {
                        api.transition(sessionId, "disconnected")
                    }
                }
            }
        }

        // Rebuild the engine after the concrete tunnel/services are stopped. This restores
        // realtime + presence for the next connection attempt and clears stale UI state.
        LinkoEngineBridge.configure(app)
    }

    fun retry(context: Context) {
        val app = context.applicationContext
        val state = LinkoEngineBridge.connection.value
        val friendUserId = state.peerLinkoId?.takeIf { it.isNotBlank() }
        if (friendUserId.isNullOrBlank()) {
            LinkoEngineBridge.configure(app)
            return
        }
        LinkoEngineBridge.configure(app)
        LinkoEngineBridge.connectToFriend(
            friendUserId = friendUserId,
            friendName = state.peerDisplayName,
            friendId = state.peerLinkoId,
        )
    }
}
