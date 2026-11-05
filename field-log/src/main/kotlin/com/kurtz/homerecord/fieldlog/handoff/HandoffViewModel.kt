package com.kurtz.homerecord.fieldlog.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.bundle.ExportedHome
import com.kurtz.homerecord.core.bundle.HandoffSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Counts the job's house for the handoff summary and writes the `.homerecord` on Export (AC-F51). The home is read
 * again on Export so a photo taken while the screen was open goes in the file.
 */
class HandoffViewModel(
    jobName: String,
    private val load: suspend () -> ExportedHome?,
    private val write: suspend (ExportedHome) -> File?,
) : ViewModel() {
    private val _state = MutableStateFlow(HandoffUiState(jobName = jobName))
    val state: StateFlow<HandoffUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val home = runCatching { load() }.getOrNull()
            _state.update {
                it.copy(loading = false, summary = home?.let { h -> HandoffSummary.of(h.bundle) }, missing = home == null)
            }
        }
    }

    fun onEvent(event: HandoffEvent) {
        when (event) {
            HandoffEvent.Export -> export()
            HandoffEvent.ShareHandled -> _state.update { it.copy(sharePath = null) }
            HandoffEvent.Back -> Unit
        }
    }

    private fun export() {
        val s = _state.value
        if (s.exporting || s.loading || s.missing || s.summary?.isEmpty != false) return
        _state.update { it.copy(exporting = true, failed = false) }
        viewModelScope.launch {
            val home = runCatching { load() }.getOrNull()
            val file = home?.let { runCatching { write(it) }.getOrNull() }
            _state.update {
                it.copy(
                    exporting = false,
                    summary = home?.let { h -> HandoffSummary.of(h.bundle) } ?: it.summary,
                    sharePath = file?.path,
                    failed = file == null,
                )
            }
        }
    }
}
