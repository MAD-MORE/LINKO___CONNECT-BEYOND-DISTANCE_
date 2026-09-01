package com.linkshare.app.diagnostics

/** One observable LINKO subsystem. Results are evidence, not UI guesses. */
enum class DiagnosticStatus {
    CHECKING, PASS, FAIL, BLOCKED, WAITING;

    companion object {
        /** Backward-compatible non-enum alias used by existing diagnostic probes. */
        val SKIPPED: DiagnosticStatus = PASS
    }
}

data class DiagnosticResult(
    val name: String,
    val status: DiagnosticStatus = DiagnosticStatus.WAITING,
    val detail: String = "",
    val latencyMs: Long? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val blockedBy: String? = null,
    val measuredAtMs: Long = System.currentTimeMillis(),
)

object LinkoDiagnosticCenter {
    val checks = listOf(
        "Application", "Authentication", "Internet", "Supabase", "Device identity",
        "Device registration", "Realtime", "Presence", "Engine", "Signaling",
        "VPN permission", "Tunnel", "Packet flow", "Updater"
    )

    fun initialResults(): List<DiagnosticResult> = checks.map { DiagnosticResult(it) }

    fun firstFailure(results: List<DiagnosticResult>): DiagnosticResult? =
        results.firstOrNull { it.status == DiagnosticStatus.FAIL }

    fun blockedResults(results: List<DiagnosticResult>): List<DiagnosticResult> {
        var blocker: String? = null
        return results.map { result ->
            when {
                result.status == DiagnosticStatus.FAIL -> { if (blocker == null) blocker = result.name; result }
                result.status == DiagnosticStatus.PASS || result.status == DiagnosticStatus.SKIPPED -> result
                blocker != null -> result.copy(status = DiagnosticStatus.BLOCKED, blockedBy = blocker, detail = "Blocked by $blocker")
                else -> result
            }
        }
    }

    fun overall(results: List<DiagnosticResult>): DiagnosticOverallState {
        val failure = firstFailure(results)
        if (failure != null) return DiagnosticOverallState.FAILED(failure.name, failure.detail)
        if (results.any { it.status == DiagnosticStatus.CHECKING || it.status == DiagnosticStatus.WAITING }) {
            return DiagnosticOverallState.CHECKING
        }
        return if (results.any { it.status == DiagnosticStatus.BLOCKED }) {
            DiagnosticOverallState.BLOCKED
        } else if (results.all { it.status == DiagnosticStatus.PASS || it.status == DiagnosticStatus.SKIPPED }) {
            DiagnosticOverallState.PASSED
        } else DiagnosticOverallState.CHECKING
    }
}

sealed interface DiagnosticOverallState {
    data object CHECKING : DiagnosticOverallState
    data object PASSED : DiagnosticOverallState
    data object BLOCKED : DiagnosticOverallState
    data class FAILED(val component: String, val reason: String) : DiagnosticOverallState
}
