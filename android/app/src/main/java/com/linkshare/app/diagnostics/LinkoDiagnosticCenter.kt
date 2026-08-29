package com.linkshare.app.diagnostics

/**
 * Observable result for one LINKO subsystem.
 *
 * This model is intentionally independent of the existing networking/UI code.
 * The Diagnostic Center can therefore observe production components without
 * changing how those components establish connections.
 */
enum class DiagnosticStatus {
    CHECKING,
    PASS,
    FAIL,
    WAITING,
    SKIPPED
}

data class DiagnosticResult(
    val name: String,
    val status: DiagnosticStatus = DiagnosticStatus.WAITING,
    val detail: String = "",
    val latencyMs: Long? = null,
    val measuredAtMs: Long = System.currentTimeMillis()
)

/**
 * Deterministic state reducer for the Diagnostic Center.
 *
 * CONNECTED is deliberately derived from evidence, never from a UI event.
 * A missing prerequisite is reported as NOT_READY and later checks may remain
 * WAITING instead of pretending that a downstream subsystem was tested.
 */
object LinkoDiagnosticCenter {
    private val required = listOf(
        "Authentication",
        "Supabase",
        "Realtime",
        "Relay",
        "VPN",
        "Encryption",
        "Tunnel",
        "Packet flow",
        "Internet"
    )

    fun initialResults(): List<DiagnosticResult> = required.map { DiagnosticResult(it) }

    fun reduce(results: List<DiagnosticResult>): DiagnosticOverallState {
        val byName = results.associateBy { it.name }
        val failed = required.firstOrNull { byName[it]?.status == DiagnosticStatus.FAIL }
        if (failed != null) {
            return DiagnosticOverallState.NOT_READY(failed, byName[failed]?.detail.orEmpty())
        }

        val incomplete = required.any {
            byName[it]?.status != DiagnosticStatus.PASS
        }
        return if (incomplete) DiagnosticOverallState.CHECKING
        else DiagnosticOverallState.CONNECTED
    }

    fun blockedResults(results: List<DiagnosticResult>): List<DiagnosticResult> {
        val byName = results.associateBy { it.name }
        var blocked = false
        return results.map { result ->
            if (result.status == DiagnosticStatus.PASS || result.status == DiagnosticStatus.FAIL) {
                result
            } else if (blocked) {
                result.copy(status = DiagnosticStatus.WAITING, detail = "Waiting for previous check")
            } else {
                val failedBefore = required.takeWhile { it != result.name }
                    .firstOrNull { byName[it]?.status == DiagnosticStatus.FAIL }
                if (failedBefore != null) {
                    blocked = true
                    result.copy(status = DiagnosticStatus.WAITING, detail = "Blocked by $failedBefore")
                } else result
            }
        }
    }
}

sealed interface DiagnosticOverallState {
    data object CHECKING : DiagnosticOverallState
    data object CONNECTED : DiagnosticOverallState
    data class NOT_READY(val failedComponent: String, val reason: String) : DiagnosticOverallState
}
