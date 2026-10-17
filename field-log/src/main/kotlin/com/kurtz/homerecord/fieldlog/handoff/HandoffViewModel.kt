package com.kurtz.homerecord.fieldlog.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.HandoffCounts
import com.kurtz.homerecord.core.repo.HandoffRepository
import com.kurtz.homerecord.core.repo.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HandoffFormat(val mime: String) {
    BUNDLE("application/octet-stream"),
    PDF("application/pdf"),
}

/** A freshly written export waiting for the share sheet; [path] is relative to the cache dir. */
data class HandoffShare(val path: String, val format: HandoffFormat)

data class HandoffUiState(
    val loaded: Boolean = false,
    val jobName: String = "",
    val counts: HandoffCounts? = null,
    val exporting: Boolean = false,
    val ready: HandoffShare? = null,
    val failed: Boolean = false,
)

sealed interface HandoffEvent {
    data object ExportBundle : HandoffEvent
    data object ExportPdf : HandoffEvent
    data object Shared : HandoffEvent
}

/** Writes an export for the job's Home and returns its cache-relative path, or null on failure. */
interface HandoffExporter {
    suspend fun exportBundle(jobId: String): String?
    suspend fun exportPdf(jobId: String): String?
}

class HandoffViewModel(
    private val jobId: String,
    private val jobs: JobRepository,
    private val handoff: HandoffRepository,
    private val exporter: HandoffExporter,
) : ViewModel() {
    private val _state = MutableStateFlow(HandoffUiState())
    val state: StateFlow<HandoffUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val job = jobs.getJob(jobId)
            val bundle = job?.let { handoff.bundleFor(it.homeId) }
            _state.update {
                it.copy(loaded = true, jobName = job?.name.orEmpty(), counts = bundle?.let(HandoffCounts::of))
            }
        }
    }

    fun onEvent(event: HandoffEvent) {
        when (event) {
            HandoffEvent.ExportBundle -> export(HandoffFormat.BUNDLE)
            HandoffEvent.ExportPdf -> export(HandoffFormat.PDF)
            HandoffEvent.Shared -> _state.update { it.copy(ready = null) }
        }
    }

    private fun export(format: HandoffFormat) {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true, failed = false) }
        viewModelScope.launch {
            val path = try {
                when (format) {
                    HandoffFormat.BUNDLE -> exporter.exportBundle(jobId)
                    HandoffFormat.PDF -> exporter.exportPdf(jobId)
                }
            } catch (e: java.io.IOException) {
                null
            }
            _state.update {
                it.copy(exporting = false, ready = path?.let { p -> HandoffShare(p, format) }, failed = path == null)
            }
        }
    }
}
