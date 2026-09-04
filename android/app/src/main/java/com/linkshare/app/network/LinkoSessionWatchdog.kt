package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.vpn.LinkShareVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the local data plane and shared session state honest.
 * Realtime is the fast path; this REST watchdog is the safety net when realtime delivery,
 * a service process, or a network path disappears.
 */
object LinkoSessionWatchdog {
    private const val POLL_MS = 1_500L
    private const val CONNECTED_GRACE_MS = 5_000L

    private val started = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var lastObservedSession: String? = null
    private var connectedSince: Long = 0L
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
        connectedSince = 0L
        stoppingSession = null
    }

    private suspend fun tick(context: Context) {
        val state = LinkoEngineBridge.connection.value
        val sessionId = state.sessionId?.takeIf { it.isNotBlank() } ?: run {
            lastObservedSession = null
            connectedSince = 0L
            return
        }

        if (sessionId != lastObservedSession) {
            lastObservedSession = sessionId
            connectedSince = if (state.phase == LinkoConnectionPhase.Connected) System.currentTimeMillis() else 0L
            stoppingSession = null
        }

        if (state.phase == LinkoConnectionPhase.Connected) {
            if (connectedSince == 0L) connectedSince = System.currentTimeMillis()
        } else {
            connectedSince = 0L
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
            "connected" -> {
                if (System.currentTimeMillis() - connectedSince >= CONNECTED_GRACE_MS && !localDataPlaneAlive(state.isProvider)) {
                    if (stoppingSession != sessionId) {
                        stoppingSession = sessionId
                        LinkoEngineBridge.reportConnectionDiagnostic(
                            ConnectionStage.CONNECTED,
                            "LOCAL_DATA_PLANE_MISSING",
                            "Shared session is connected but the local data-plane service is no longer running",
                            ConnectionSeverity.ERROR,
                        )
                        runCatching { LinkoDeviceControlApi(context).transition(sessionId, "disconnected") }
                        stopLocalDataPlane(context, "local_data_plane_missing")
                    }
                }
            }
        }
    }

    private fun localDataPlaneAlive(isProvider: Boolean): Boolean =
        if (isProvider) LinkoProviderService.isRunning else LinkShareVpnService.isRunning()

    private fun stopLocalDataPlane(context: Context, reason: String) {
        runCatching { context.stopService(Intent(context, LinkShareVpnService::class.java)) }
        runCatching { context.stopService(Intent(context, LinkoProviderService::class.java)) }
        LinkoEngineBridge.reportTunnelState("stopped", "Connection ended: $reason")
    }
}
