package com.linkshare.app.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight in-process telemetry used only by the Diagnostic Center. */
data class DiagnosticTelemetrySnapshot(
    val enginePhase: String = "Idle",
    val engineDetail: String = "Ready",
    val engineError: String? = null,
    val engineTrace: List<String> = emptyList(),
    val realtimeConnected: Boolean = false,
    val realtimeError: String? = null,
    val realtimeChannels: List<String> = emptyList(),
    val vpnRunning: Boolean = false,
    val vpnTxPackets: Long = 0L,
    val vpnRxPackets: Long = 0L,
    val vpnTxBytes: Long = 0L,
    val vpnRxBytes: Long = 0L,
    val vpnError: String? = null,
)

object LinkoDiagnosticTelemetry {
    private val _snapshot = MutableStateFlow(DiagnosticTelemetrySnapshot())
    val snapshot: StateFlow<DiagnosticTelemetrySnapshot> = _snapshot.asStateFlow()

    fun recordEngine(phase: String, detail: String, error: String?) {
        _snapshot.value = _snapshot.value.let { current ->
            val entry = "$phase — $detail"
            val trace = (current.engineTrace + entry).takeLast(32)
            current.copy(enginePhase = phase, engineDetail = detail, engineError = error, engineTrace = trace)
        }
    }

    fun recordRealtime(connected: Boolean, error: String?, channels: List<String>) {
        _snapshot.value = _snapshot.value.copy(
            realtimeConnected = connected,
            realtimeError = error,
            realtimeChannels = channels,
        )
    }

    fun recordVpn(running: Boolean, txPackets: Long, rxPackets: Long, txBytes: Long, rxBytes: Long, error: String?) {
        _snapshot.value = _snapshot.value.copy(
            vpnRunning = running,
            vpnTxPackets = txPackets,
            vpnRxPackets = rxPackets,
            vpnTxBytes = txBytes,
            vpnRxBytes = rxBytes,
            vpnError = error,
        )
    }
}
