package com.kurtz.homerecord.fieldlog.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.DailyReport
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.ReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val loaded: Boolean = false,
    val jobName: String = "",
    /** ISO yyyy-MM-dd. */
    val day: String = "",
    val weather: String = "",
    val crew: String = "",
    val hours: String = "",
    val workCompleted: String = "",
    val delayReason: DelayReason = DelayReason.NONE,
    val delays: String = "",
    val materials: String = "",
    val nextSteps: String = "",
    val photoCount: Int = 0,
    val saved: Boolean = false,
    val makingPdf: Boolean = false,
    /** Cache-relative path of a freshly made PDF waiting to be shared; cleared by [ReportEvent.PdfShared]. */
    val pdfReady: String? = null,
    val pdfFailed: Boolean = false,
)

sealed interface ReportEvent {
    data class DayChanged(val day: String) : ReportEvent
    data class WeatherChanged(val value: String) : ReportEvent
    data class CrewChanged(val value: String) : ReportEvent
    data class HoursChanged(val value: String) : ReportEvent
    data class WorkChanged(val value: String) : ReportEvent
    data class DelayReasonChanged(val value: DelayReason) : ReportEvent
    data class DelaysChanged(val value: String) : ReportEvent
    data class MaterialsChanged(val value: String) : ReportEvent
    data class NextStepsChanged(val value: String) : ReportEvent
    data object Save : ReportEvent
    data object MakePdf : ReportEvent
    data object PdfShared : ReportEvent
}

/** Renders the saved draft for a day to a PDF and returns its cache-relative path, or null on failure. */
fun interface ReportPdfExporter {
    suspend fun export(jobId: String, day: String): String?
}

/** FL-Report: an editable draft per job per day, prefilled from entries (AC-67) and autosaved. */
class ReportViewModel(
    private val jobId: String,
    today: String,
    private val jobs: JobRepository,
    private val reports: ReportRepository,
    private val exporter: ReportPdfExporter,
    private val debounceMs: Long = 400,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState(day = today))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private var report: DailyReport? = null
    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val name = jobs.getJob(jobId)?.name.orEmpty()
            _state.update { it.copy(jobName = name) }
            load(_state.value.day)
        }
    }

    private suspend fun load(day: String) {
        val draft = reports.draftFor(jobId, day)
        report = draft
        dirty = false
        val photos = reports.photosFor(jobId, day).size
        _state.update {
            ReportUiState(
                loaded = true, jobName = it.jobName, day = day, weather = draft.weather.orEmpty(),
                crew = draft.crewCount?.toString().orEmpty(), hours = draft.hours?.let(::formatHours).orEmpty(),
                workCompleted = draft.workCompleted, delayReason = draft.delayReason ?: DelayReason.NONE,
                delays = draft.delays.orEmpty(), materials = draft.materials.orEmpty(),
                nextSteps = draft.nextSteps.orEmpty(), photoCount = photos,
            )
        }
    }

    fun onEvent(event: ReportEvent) {
        when (event) {
            is ReportEvent.DayChanged -> changeDay(event.day)
            is ReportEvent.WeatherChanged -> edit { it.copy(weather = event.value) }
            is ReportEvent.CrewChanged -> edit { it.copy(crew = event.value.filter(Char::isDigit).take(3)) }
            is ReportEvent.HoursChanged -> edit { it.copy(hours = event.value.filter { c -> c.isDigit() || c == '.' }.take(5)) }
            is ReportEvent.WorkChanged -> edit { it.copy(workCompleted = event.value) }
            is ReportEvent.DelayReasonChanged -> edit { it.copy(delayReason = event.value) }
            is ReportEvent.DelaysChanged -> edit { it.copy(delays = event.value) }
            is ReportEvent.MaterialsChanged -> edit { it.copy(materials = event.value) }
            is ReportEvent.NextStepsChanged -> edit { it.copy(nextSteps = event.value) }
            ReportEvent.Save -> { dirty = true; flush() }
            ReportEvent.MakePdf -> makePdf()
            ReportEvent.PdfShared -> _state.update { it.copy(pdfReady = null, pdfFailed = false) }
        }
    }

    /** Writes pending edits now (called on Save and when the screen stops). */
    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private fun makePdf() {
        if (_state.value.makingPdf) return
        _state.update { it.copy(makingPdf = true, pdfFailed = false) }
        saveJob?.cancel()
        viewModelScope.launch {
            dirty = true
            persist()
            val path = try {
                exporter.export(jobId, _state.value.day)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(makingPdf = false, pdfReady = path, pdfFailed = path == null) }
        }
    }

    private fun changeDay(day: String) {
        if (day == _state.value.day) return
        saveJob?.cancel()
        viewModelScope.launch {
            persist()
            load(day)
        }
    }

    private fun edit(transform: (ReportUiState) -> ReportUiState) {
        _state.update { transform(it).copy(saved = false) }
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(debounceMs)
            persist()
        }
    }

    private suspend fun persist() {
        val base = report ?: return
        if (!dirty) return
        val s = _state.value
        val updated = base.copy(
            weather = s.weather.clean(), crewCount = s.crew.toIntOrNull(), hours = s.hours.toDoubleOrNull(),
            workCompleted = s.workCompleted, delayReason = s.delayReason, delays = s.delays.clean(),
            materials = s.materials.clean(), nextSteps = s.nextSteps.clean(),
        )
        report = reports.save(updated)
        dirty = false
        _state.update { it.copy(saved = true) }
    }

    private fun String.clean(): String? = trim().ifEmpty { null }

    private fun formatHours(h: Double): String = if (h % 1.0 == 0.0) h.toLong().toString() else h.toString()
}
