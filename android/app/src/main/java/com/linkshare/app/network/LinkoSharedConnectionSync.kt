package com.linkshare.app.network

import android.content.Context
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps both connected LINKO UIs on one canonical session snapshot.
 * Only counters/metadata are synchronized; packet payloads never leave the data plane.
 */
object LinkoSharedConnectionSync {
    private const val TAG = "LINKO_UI_SYNC"
    private const val ACTIVE_POLL_MS = 1_500L
    private const val IDLE_POLL_MS = 600L

    private var job: Job? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        appContext = context.applicationContext
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    val state = LinkoEngineBridge.connection.value
                    val sessionId = state.sessionId?.takeIf { it.isNotBlank() }
                    val app = appContext

                    if (app != null && sessionId != null && state.phase == LinkoConnectionPhase.Connected) {
                        val api = LinkoDeviceControlApi(app)
                        val deviceId = LinkoAuth(app).currentDeviceId()?.takeIf { it.isNotBlank() }
                        if (deviceId != null) {
                            runCatching {
                                api.publishSessionUiState(
                                    sessionId = sessionId,
                                    deviceId = deviceId,
                                    role = if (state.isProvider) "provider" else "receiver",
                                    txBytes = state.bytesOut,
                                    rxBytes = state.bytesIn,
                                    latencyMs = state.latencyMs,
                                )
                            }.onFailure {
                                Log.w(TAG, "UI state publish deferred: ${it.message}")
                            }

                            runCatching { api.getSessionUiState(sessionId) }
                                .onSuccess { snapshot ->
                                    val canonicalTx = if (state.isProvider) snapshot.providerTxBytes else snapshot.receiverTxBytes
                                    val canonicalRx = if (state.isProvider) snapshot.providerRxBytes else snapshot.receiverRxBytes
                                    LinkoEngineBridge.updateTrafficStats(
                                        bytesIn = canonicalRx,
                                        bytesOut = canonicalTx,
                                        latencyMs = snapshot.sharedLatencyMs,
                                    )

                                    val peerName = if (state.isProvider) snapshot.receiverName else snapshot.providerName
                                    val peerId = if (state.isProvider) snapshot.receiverLinkoId else snapshot.providerLinkoId
                                    LinkoEngineBridge.setPeerInfo(peerName, peerId, state.isProvider)
                                }
                                .onFailure {
                                    Log.w(TAG, "UI state read deferred: ${it.message}")
                                }
                        }
                        delay(ACTIVE_POLL_MS)
                    } else {
                        delay(IDLE_POLL_MS)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Log.w(TAG, "Shared UI sync cycle failed: ${error.message}")
                    delay(ACTIVE_POLL_MS)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        appContext = null
    }
}
