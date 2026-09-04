package com.linkshare.app.vpn

import com.linkshare.app.network.ConnectionDiagnosticEvent
import com.linkshare.app.network.ConnectionSeverity
import com.linkshare.app.network.ConnectionStage
import com.linkshare.app.network.LinkoConnectionDiagnostics as NetworkLinkoConnectionDiagnostics

/**
 * VPN-package facade for the shared diagnostics collector.
 * Keeps legacy VPN call sites source-compatible without creating a second diagnostic state store.
 */
object LinkoConnectionDiagnostics {
    fun record(
        stage: ConnectionStage,
        event: String,
        message: String,
        severity: ConnectionSeverity = ConnectionSeverity.INFO,
        sessionId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        NetworkLinkoConnectionDiagnostics.record(
            stage = stage,
            event = event,
            message = message,
            severity = severity,
            sessionId = sessionId,
            metadata = metadata,
        )
    }
}
