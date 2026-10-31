package com.kurtz.homerecord.fieldlog.log

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.model.LogWeather
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class LogWeatherDeps(
    val jobId: String,
    val day: LocalDate,
    val logs: DailyLogRepository,
    val gates: Flow<RoleGates>,
)

/**
 * The weather form for one job and day (SPEC FL-Log, rule 15). Save stores the typed weather and marks it edited, so
 * the lookup never replaces it (AC-F30); "Look it up again" clears that mark and rebuilds the day, which fetches the
 * weather from the backend proxy when online (AC-F31). The typing survives process death.
 */
class LogWeatherViewModel(private val d: LogWeatherDeps, private val saved: SavedStateHandle) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(LogWeatherUiState(day = d.day))
    val state: StateFlow<LogWeatherUiState> = _state.asStateFlow()
    private var logId: String? = null
    private var previous: LogWeather? = null

    init {
        load()
    }

    private fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val log = d.logs.observe(d.jobId, d.day).first()
            val canEdit = d.gates.first().leads(d.jobId)
            if (log == null || !canEdit) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            logId = log.id
            val weather = log.sections.weather
            previous = weather.value
            val initial = LogWeatherForm.draftOf(weather.value)
            val draft = saved.get<String>(DRAFT)?.let { runCatching { json.decodeFromString(WeatherDraft.serializer(), it) }.getOrNull() } ?: initial
            _state.update {
                it.copy(
                    loading = false, draft = draft, initial = initial, handEntered = weather.edited,
                    showErrors = saved.get<Boolean>(SHOW_ERRORS) == true,
                )
            }
        }
    }

    fun onEvent(event: LogWeatherEvent) {
        when (event) {
            is LogWeatherEvent.Conditions -> edit { it.copy(conditions = event.key) }
            is LogWeatherEvent.High -> edit { it.copy(high = event.text) }
            is LogWeatherEvent.Low -> edit { it.copy(low = event.text) }
            is LogWeatherEvent.Precip -> edit { it.copy(precip = event.text) }
            LogWeatherEvent.Save -> save()
            LogWeatherEvent.LookUpAgain -> lookUpAgain()
            LogWeatherEvent.Retry -> load()
            LogWeatherEvent.Close -> Unit
        }
    }

    private fun edit(change: (WeatherDraft) -> WeatherDraft) {
        val st = _state.value
        if (st.loading || st.failed || st.working) return
        val draft = change(st.draft)
        saved[DRAFT] = json.encodeToString(WeatherDraft.serializer(), draft)
        _state.update { it.copy(draft = draft) }
    }

    private fun save() {
        val st = _state.value
        val id = logId ?: return
        if (st.working) return
        val weather = LogWeatherForm.check(st.draft, previous).weather
        if (weather == null) {
            saved[SHOW_ERRORS] = true
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            d.logs.update(id) { LogEdits.weather(it, weather) }
            finish()
        }
    }

    private fun lookUpAgain() {
        val id = logId ?: return
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            d.logs.update(id) { LogEdits.lookUpWeather(it) }
            d.logs.assemble(d.jobId, d.day)
            finish()
        }
    }

    private fun finish() {
        saved.remove<String>(DRAFT)
        saved.remove<Boolean>(SHOW_ERRORS)
        _state.update { it.copy(working = false, done = true) }
    }

    private companion object {
        const val DRAFT = "weatherDraft"
        const val SHOW_ERRORS = "weatherShowErrors"
    }
}
