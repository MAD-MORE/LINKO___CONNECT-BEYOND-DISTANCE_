package com.linkshare.app.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Process-local, bounded diagnostic stream for one LINKO connection attempt.
 * It intentionally stores metadata only; tunnel/session keys must never be recorded.
 */
object LinkoConnectionDiagnostics {
    private const val MAX_EVENTS = 80

    private val _snapshot = MutableStateFlow(ConnectionDiagnosticsSnapshot())
    val snapshot: StateFlow<ConnectionDiagnosticsSnapshot> = _snapshot.asStateFlow()

    private val _events = MutableStateFlow<List<ConnectionDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<ConnectionDiagnosticEvent>> = _events.asStateFlow()

    fun begin(sessionId: String? = null, peerName: String? = null) {
        _events.value = emptyList()
        _snapshot.value = ConnectionDiagnosticsSnapshot(
            sessionId = sessionId,
            peerName = peerName,
            stage = ConnectionStage.REQUESTING,
            progress = ConnectionStage.REQUESTING.progress,
            headline = "Starting connection",
        )
    }

    fun record(
        stage: ConnectionStage,
        event: String,
        message: String,
        severity: ConnectionSeverity = ConnectionSeverity.INFO,
        sessionId: String? = _snapshot.value.sessionId,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val safeMetadata = metadata.filterKeys { key ->
            val normalized = key.lowercase()
            !normalized.contains("key") &&
                !normalized.contains("secret") &&
                !normalized.contains("token") &&
                !normalized.contains("password")
        }
        val item = ConnectionDiagnosticEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            stage = stage,
            event = event,
            message = message,
            severity = severity,
            metadata = safeMetadata,
        )
        _events.value = (listOf(item) + _events.value).take(MAX_EVENTS)

        val previous = _snapshot.value
        val localCandidates = safeMetadata["localCandidates"]?.toIntOrNull() ?: previous.localCandidates
        val remoteCandidates = safeMetadata["remoteCandidates"]?.toIntOrNull() ?: previous.remoteCandidates
        val checks = safeMetadata["checks"]?.toIntOrNull() ?: previous.checks
        val successfulChecks = safeMetadata["successfulChecks"]?.toIntOrNull() ?: previous.successfulChecks
        val failure = if (severity == ConnectionSeverity.ERROR || stage == ConnectionStage.FAILED) message else previous.failureReason
        _snapshot.value = previous.copy(
            sessionId = sessionId ?: previous.sessionId,
            stage = stage,
            progress = if (stage == ConnectionStage.FAILED) previous.progress else stage.progress.coerceIn(0f, 1f),
            headline = message,
            failureReason = failure,
            localCandidates = localCandidates,
            remoteCandidates = remoteCandidates,
            checks = checks,
            successfulChecks = successfulChecks,
            recentEvents = _events.value.take(8),
        )
    }

    fun fail(
        reason: String,
        sessionId: String? = _snapshot.value.sessionId,
        metadata: Map<String, String> = emptyMap(),
    ) {
        record(
            stage = ConnectionStage.FAILED,
            event = "CONNECTION_FAILED",
            message = reason,
            severity = ConnectionSeverity.ERROR,
            sessionId = sessionId,
            metadata = metadata,
        )
    }

    fun reset() {
        _events.value = emptyList()
        _snapshot.value = ConnectionDiagnosticsSnapshot()
    }

    fun stageForState(state: String): ConnectionStage = when (state.lowercase()) {
        "connecting", "reconnecting", "requesting", "waiting_for_provider", "provider_ready" -> ConnectionStage.REQUESTING
        "approved", "approving" -> ConnectionStage.APPROVING
        "authenticating", "resolving_provider", "signaling", "signaling_retry" -> ConnectionStage.SIGNALING
        "offer", "answer", "sdp", "sdp_offer", "sdp_answer" -> ConnectionStage.SDP_NEGOTIATION
        "ice_gathering", "ice_gathered" -> ConnectionStage.ICE_GATHERING
        "direct_connecting", "ice_checking", "connectivity_check" -> ConnectionStage.ICE_CHECKING
        "nomination", "nominating", "nomination_ack" -> ConnectionStage.NOMINATING
        "securing", "handshake", "final_ready", "final_handshake" -> ConnectionStage.HANDSHAKE
        "establishing", "tunnel_starting" -> ConnectionStage.TUNNEL_STARTING
        "routing", "packet_flow" -> ConnectionStage.PACKET_FLOW
        "direct_established", "direct_verified", "connected" -> ConnectionStage.CONNECTED
        "failed", "provider_connection_failed", "connection_request_denied", "connection_request_expired" -> ConnectionStage.FAILED
        else -> _snapshot.value.stage
    }
}

enum class ConnectionStage(val progress: Float) {
    REQUESTING(0.05f),
    APPROVING(0.12f),
    SIGNALING(0.20f),
    SDP_NEGOTIATION(0.28f),
    ICE_GATHERING(0.40f),
    ICE_CHECKING(0.55f),
    NOMINATING(0.68f),
    HANDSHAKE(0.78f),
    TUNNEL_STARTING(0.88f),
    PACKET_FLOW(0.95f),
    CONNECTED(1.0f),
    FAILED(0f),
}

enum class ConnectionSeverity { INFO, SUCCESS, WARNING, ERROR }

data class ConnectionDiagnosticEvent(
    val id: String,
    val timestamp: Long,
    val sessionId: String?,
    val stage: ConnectionStage,
    val event: String,
    val message: String,
    val severity: ConnectionSeverity,
    val metadata: Map<String, String> = emptyMap(),
)

data class ConnectionDiagnosticsSnapshot(
    val sessionId: String? = null,
    val peerName: String? = null,
    val stage: ConnectionStage = ConnectionStage.REQUESTING,
    val progress: Float = 0f,
    val headline: String = "Ready",
    val failureReason: String? = null,
    val localCandidates: Int = 0,
    val remoteCandidates: Int = 0,
    val checks: Int = 0,
    val successfulChecks: Int = 0,
    val recentEvents: List<ConnectionDiagnosticEvent> = emptyList(),
)
