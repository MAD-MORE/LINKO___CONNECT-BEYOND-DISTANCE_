package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import com.linkshare.app.diagnostics.LinkoDiagnosticTelemetry
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
 * Keeps the local data plane synchronized with the shared session state and protects
 * live sessions from transient mobile-network failures.
 *
 * The network health classifier uses hysteresis: one missed control probe never tears
 * down a tunnel. Repeated failures enter DEGRADED/RECOVERING, with automatic retry, and
 * only sustained failure is promoted to LOST and allowed to terminate the data plane.
 */
object LinkoSessionWatchdog {
    private const val POLL_MS = 1_500L
    private const val AUTO_RETRY_COOLDOWN_MS = 12_000L

    private val started = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var lastObservedSession: String? = null
    private var stoppingSession: String? = null
    private var resilience: LinkoNetworkResilience? = null
    private var lastRetryAt = 0L

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        resilience = LinkoNetworkResilience()
        lastRetryAt = 0L
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
        resilience?.reset()
        resilience = null
        lastRetryAt = 0L
    }

    private suspend fun tick(context: Context) {
        val sessionId = LinkoEngineBridge.connection.value.sessionId?.takeIf { it.isNotBlank() } ?: run {
            lastObservedSession = null
            resilience?.reset()
            return
        }

        if (sessionId != lastObservedSession) {
            lastObservedSession = sessionId
            stoppingSession = null
            resilience?.reset()
            lastRetryAt = 0L
        }

        val startedAt = System.nanoTime()
        val sessionResult = runCatching { LinkoDeviceControlApi(context).session(sessionId) }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L

        val session = sessionResult.getOrNull()
        val sessionState = session?.state?.trim()?.lowercase()
        val decision = resilience?.observe(
            LinkoNetworkResilience.Sample(
                probeSucceeded = sessionResult.isSuccess,
                roundTripMs = elapsedMs,
                // LINKO does not treat an idle tunnel as packet loss. Packet loss is
                // therefore left at zero until a real transport probe supplies it.
                packetLossPercent = 0,
                realtimeConnected = LinkoDiagnosticTelemetry.snapshot.value.realtimeConnected,
            ),
        ) ?: return

        publishHealthDiagnostic(decision, elapsedMs)

        if (sessionState !in TERMINAL_STATES && decision.shouldRetry && !decision.shouldTerminate) {
            maybeRecover(context, sessionId, decision)
        }

        if (session == null) return
        if (sessionState in TERMINAL_STATES) {
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

    private fun publishHealthDiagnostic(
        decision: LinkoNetworkResilience.Decision,
        roundTripMs: Long,
    ) {
        val severity = when (decision.state) {
            LinkoNetworkResilience.State.HEALTHY -> ConnectionSeverity.SUCCESS
            LinkoNetworkResilience.State.DEGRADED, LinkoNetworkResilience.State.RECOVERING -> ConnectionSeverity.WARNING
            LinkoNetworkResilience.State.LOST -> ConnectionSeverity.ERROR
        }
        LinkoEngineBridge.reportConnectionDiagnostic(
            ConnectionStage.CONNECTED,
            "NETWORK_HEALTH_${decision.state.name}",
            "Network ${decision.state.name.lowercase()}: score=${decision.score}, rtt=${roundTripMs}ms, failures=${decision.consecutiveFailures}",
            severity,
            metadata = mapOf(
                "networkHealth" to decision.state.name.lowercase(),
                "score" to decision.score.toString(),
                "roundTripMs" to roundTripMs.toString(),
                "consecutiveFailures" to decision.consecutiveFailures.toString(),
                "realtimeConnected" to LinkoDiagnosticTelemetry.snapshot.value.realtimeConnected.toString(),
            ),
        )
    }

    private fun maybeRecover(
        context: Context,
        sessionId: String,
        decision: LinkoNetworkResilience.Decision,
    ) {
        val now = System.currentTimeMillis()
        if (decision.state != LinkoNetworkResilience.State.RECOVERING) return
        if (now - lastRetryAt < AUTO_RETRY_COOLDOWN_MS) return

        lastRetryAt = now
        LinkoEngineBridge.reportTunnelState(
            "reconnecting",
            "Network is unstable. LINKO is automatically recovering the connection…",
        )
        LinkoEngineBridge.reportConnectionDiagnostic(
            ConnectionStage.CONNECTED,
            "NETWORK_AUTO_RECOVERY",
            "Starting automatic connection recovery after ${decision.consecutiveFailures} consecutive failed probes",
            ConnectionSeverity.WARNING,
        )

        // Reconnect runs through the existing authenticated Provider/Receiver session
        // workflow. It preserves LINKO's data-role invariant: the Provider remains the
        // Internet source and the Receiver remains the consumer.
        runCatching {
            LinkoEngineBridge.reconnect()
        }.onFailure { error ->
            LinkoEngineBridge.reportConnectionDiagnostic(
                ConnectionStage.CONNECTED,
                "NETWORK_AUTO_RECOVERY_FAILED",
                error.message ?: "network_auto_recovery_failed",
                ConnectionSeverity.WARNING,
                metadata = mapOf("sessionId" to sessionId, "context" to context.packageName),
            )
        }
    }

    private fun stopLocalDataPlane(context: Context, reason: String) {
        runCatching { context.stopService(Intent(context, LinkShareVpnService::class.java)) }
        runCatching { context.stopService(Intent(context, LinkoProviderService::class.java)) }
        LinkoEngineBridge.reportTunnelState("stopped", "Connection ended: $reason")
    }

    private val TERMINAL_STATES = setOf("failed", "denied", "expired", "revoked", "disconnected")
}
