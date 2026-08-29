package com.linkshare.app.diagnostics

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder only. Existing LINKO networking is not replaced or duplicated.
 * Adapters can update individual observations as the real services report them.
 */
class DiagnosticCenterViewModel : ViewModel() {
    private val _results = MutableStateFlow(LinkoDiagnosticCenter.initialResults())
    val results: StateFlow<List<DiagnosticResult>> = _results.asStateFlow()

    fun record(result: DiagnosticResult) {
        _results.value = _results.value.map {
            if (it.name == result.name) result else it
        }
    }

    fun reset() {
        _results.value = LinkoDiagnosticCenter.initialResults()
    }
}
