package com.kurtz.homerecord.housemanual.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.pdf.HomeReportData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Makes the home report PDF and hands back a shareable URI, or null when it could not be written (AC-H13). */
fun interface HomeReportExporter {
    suspend fun export(): String?
}

/**
 * HM-Report (AC-H13): [load] reads the home as the report will print it, for the counts on screen; Make asks [exporter]
 * for a fresh PDF (read again from Room, so it is never older than the screen) and the route opens the share sheet.
 */
class ReportViewModel(private val load: suspend () -> HomeReportData?, private val exporter: HomeReportExporter) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = load()
            _state.update { it.copy(loading = false, homeName = data?.homeName.orEmpty(), contents = data?.let(::contentsOf)) }
        }
    }

    fun onEvent(event: ReportEvent) {
        when (event) {
            ReportEvent.Make -> make()
            ReportEvent.ShareHandled -> _state.update { it.copy(uri = null) }
            ReportEvent.Back -> Unit
        }
    }

    private fun make() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, failed = false, uri = null) }
        viewModelScope.launch {
            val uri = exporter.export()
            _state.update { it.copy(busy = false, failed = uri == null, uri = uri) }
        }
    }

    companion object {
        fun contentsOf(data: HomeReportData) = ReportContents(
            floors = data.floors.size,
            rooms = data.rooms.count { it.floorName.isNotEmpty() },
            items = data.rooms.sumOf { it.items.size },
            shutoffs = data.shutoffs.size,
            panels = data.panels.size,
        )
    }
}
