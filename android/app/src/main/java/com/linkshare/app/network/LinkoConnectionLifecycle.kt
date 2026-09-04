package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.vpn.LinkShareVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Authoritative UI-facing stop boundary for LINKO connection/session teardown. */
object LinkoConnectionLifecycle {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun stop(context: Context) {
        val app = context.applicationContext
        val sessionId = LinkoEngineBridge.connection.value.sessionId

        // Prevent the safety monitor from racing the explicit user stop.
        LinkoSessionWatchdog.stop()

        // Stop the actual data-plane services immediately. UI teardown must never wait on REST.
        runCatching { app.stopService(Intent(app, LinkShareVpnService::class.java)) }
        runCatching { app.stopService(Intent(app, LinkoProviderService::class.java)) }

        // Publish the session terminal state independently so local service teardown cannot
        // block behind Supabase or realtime.
        if (!sessionId.isNullOrBlank()) {
            ioScope.launch {
                runCatching {
                    val api = LinkoDeviceControlApi(app)
                    val current = api.session(sessionId).state.trim().lowercase()
                    if (current !in TERMINAL_STATES) api.transition(sessionId, "disconnected")
                }
            }
        }

        // Reset local control state and recreate its realtime collector. The data plane is fully
        // stopped before the next connection generation can start.
        LinkoEngineBridge.configure(app)
        LinkoSessionWatchdog.start(app)
    }

    private val TERMINAL_STATES = setOf("failed", "denied", "expired", "revoked", "disconnected")
}