package com.linkshare.app.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs real read-only probes against the installed app/device.
 * Existing LINKO connection services remain the source of truth for actual
 * connection state; this controller only observes what can be verified.
 */
class DiagnosticCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val _results = MutableStateFlow(LinkoDiagnosticCenter.initialResults())
    val results: StateFlow<List<DiagnosticResult>> = _results.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun runDiagnostics() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _results.value = LinkoDiagnosticCenter.initialResults()
            _results.value = DiagnosticProbe.run(getApplication())
            _running.value = false
        }
    }

    fun record(result: DiagnosticResult) {
        _results.value = _results.value.map { if (it.name == result.name) result else it }
    }

    fun reset() {
        _results.value = LinkoDiagnosticCenter.initialResults()
    }
}
