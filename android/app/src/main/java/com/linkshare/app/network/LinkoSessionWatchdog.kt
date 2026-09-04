package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.vpn.LinkShareVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the local data plane synchronized with the shared session state.
 * Realtime is the fast path; this REST watchdog is the safety net when realtime delivery
 * is delayed or a peer ends the session without a local UI action.
 */
object LinkoSessionWatchdog {
    private const val POLL_MS = 1_500L

    private val started = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var lastObservedSession: String? = null
    private var stoppingSession: String? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = engineScope
        job = engineScope.launch {
            while (isActive && started.get()) {
                try {
                    tick(app)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    LinkoEngineBridge.reportConnectionDiagnostic(
                        ConnectionStage.CONNECTED,
                        "SESSION_WATCHDOG_POLL_FAILED",
                        error.message ?: "session_watchdog_poll_failed",
                        ConnectionSeverity.WARNING,
                    )
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        started.set(false)
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
        lastObservedSession = null
        stoppingSession = null
    }

    private suspend fun tick(context: Context) {
        val sessionId = LinkoEngineBridge.connection.value.sessionId?.takeIf { it.isNotBlank() } ?: run {
            lastObservedSession = null
            return
        }

        if (sessionId != lastObservedSession) {
            lastObservedSession = sessionId
            stoppingSession = null
        }

        val session = runCatching { LinkoDeviceControlApi(context).session(sessionId) }.getOrNull() ?: return
        when (session.state.trim().lowercase()) {
            "failed", "denied", "expired", "revoked", "disconnected" -> {
                if (stoppingSession != sessionId) {
                    stoppingSession = sessionId
                    LinkoEngineBridge.reportConnectionDiagnostic(
                        ConnectionStage.CONNECTED,
                        "REMOTE_SESSION_TERMINAL",
                        "Peer session changed to ${session.state}; stopping local data plane",
                        ConnectionSeverity.WARNING,
                        metadata = mapOf("sessionState" to session.state),
                    )
                    stopLocalDataPlane(context, session.state)
                }
            }
        }
    }

    private fun stopLocalDataPlane(context: Context, reason: String) {
        runCatching { context.stopService(Intent(context, LinkShareVpnService::class.java)) }
        runCatching { context.stopService(Intent(context, LinkoProviderService::class.java)) }
        LinkoEngineBridge.reportTunnelState("stopped", "Connection ended: $reason")
    }
}
