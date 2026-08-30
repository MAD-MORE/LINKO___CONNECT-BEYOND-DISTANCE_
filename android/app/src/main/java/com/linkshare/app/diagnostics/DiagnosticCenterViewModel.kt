package com.linkshare.app.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs the diagnostic probes and exposes explicit progress state to the UI.
 */
class DiagnosticCenterViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TOTAL_CHECKS = 9
    }

    private val _results = MutableStateFlow(LinkoDiagnosticCenter.initialResults())
    val results: StateFlow<List<DiagnosticResult>> = _results.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _completedChecks = MutableStateFlow(0)
    val completedChecks: StateFlow<Int> = _completedChecks.asStateFlow()

    private val _currentCheck = MutableStateFlow("")
    val currentCheck: StateFlow<String> = _currentCheck.asStateFlow()

    private val _complete = MutableStateFlow(false)
    val complete: StateFlow<Boolean> = _complete.asStateFlow()

    fun runDiagnostics() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _complete.value = false
            _completedChecks.value = 0
            _currentCheck.value = "Preparing system checks…"
            _results.value = LinkoDiagnosticCenter.initialResults()

            try {
                _results.value = DiagnosticProbe.run(getApplication()) { completed, updated ->
                    _completedChecks.value = completed
                    _results.value = updated
                    _currentCheck.value = if (completed < TOTAL_CHECKS) {
                        "Checking ${updated.getOrNull(completed)?.name ?: "LINKO subsystem"}…"
                    } else {
                        "Finalizing diagnostic report…"
                    }
                }
                _completedChecks.value = TOTAL_CHECKS
                _currentCheck.value = "Diagnostic scan complete"
                _complete.value = true
            } finally {
                _running.value = false
            }
        }
    }

    fun record(result: DiagnosticResult) {
        _results.value = _results.value.map { if (it.name == result.name) result else it }
    }

    fun reset() {
        _results.value = LinkoDiagnosticCenter.initialResults()
        _completedChecks.value = 0
        _currentCheck.value = "Ready to run system verification"
        _complete.value = false
    }
}
