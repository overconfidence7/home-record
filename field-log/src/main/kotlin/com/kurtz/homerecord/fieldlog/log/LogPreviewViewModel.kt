package com.kurtz.homerecord.fieldlog.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.coreui.pages.PageSource
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LogPreviewDeps(
    val jobId: String,
    val day: LocalDate,
    val logs: DailyLogRepository,
    val sender: LogSender,
    /** Opens the written PDF as pages; null when it cannot be read. */
    val open: (File) -> PageSource?,
)

/** Builds the PDF for the saved log of [LogPreviewDeps.day] and shows its pages; Retry builds it again. */
class LogPreviewViewModel(private val d: LogPreviewDeps) : ViewModel() {
    private val _state = MutableStateFlow(LogPreviewUiState(d.day))
    val state: StateFlow<LogPreviewUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: LogPreviewEvent) {
        if (event == LogPreviewEvent.Retry && !_state.value.loading) load()
    }

    private fun load() {
        _state.value.pages?.close()
        _state.value = LogPreviewUiState(d.day)
        viewModelScope.launch {
            val log = d.logs.observe(d.jobId, d.day).first()
            val pages = log?.let { d.sender.make(it) }?.let { d.open(it.file) }
            _state.value = LogPreviewUiState(d.day, loading = false, failed = pages == null, pages = pages)
        }
    }

    override fun onCleared() {
        _state.value.pages?.close()
    }
}
