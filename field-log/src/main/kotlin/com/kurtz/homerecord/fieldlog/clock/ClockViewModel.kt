package com.kurtz.homerecord.fieldlog.clock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.model.TimesheetRules
import com.kurtz.homerecord.core.punch.ClockContext
import com.kurtz.homerecord.core.punch.ClockController
import com.kurtz.homerecord.core.punch.ClockJob
import com.kurtz.homerecord.core.punch.ClockResult
import com.kurtz.homerecord.core.punch.ClockStatus
import com.kurtz.homerecord.core.punch.PunchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Everything the clock screen needs from the outside, so tests can swap each piece. */
class ClockDeps(
    val companyId: String,
    val memberId: String,
    val controller: ClockController,
    val punches: PunchRepository,
    val jobs: JobDao,
    val location: LocationProvider,
    val settings: suspend () -> CompanySettings,
    val permissionAsked: () -> Boolean,
    val markPermissionAsked: () -> Unit,
    val zone: ZoneId = ZoneId.systemDefault(),
    val now: () -> Long = System::currentTimeMillis,
    val ticks: Flow<Unit> = flow { while (true) { emit(Unit); kotlinx.coroutines.delay(1_000) } },
)

/** FL-Clock: clock in with job and GPS, break, switch, clock out, and this week's timesheet (AC-F8..F10, AC-F18). */
class ClockViewModel(private val d: ClockDeps, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ClockUiState(selectedJobId = saved[JOB], note = saved[NOTE] ?: "", now = d.now()))
    val state: StateFlow<ClockUiState> = _state.asStateFlow()

    private var settings = CompanySettings()
    private var pending: String? = saved[PENDING] // "in" or "switch" while the reason sheet is open

    init {
        viewModelScope.launch { settings = d.settings() }
        viewModelScope.launch {
            combine(d.jobs.observeFor(d.companyId), d.punches.observeShifts(d.companyId, d.memberId)) { jobs, shifts -> jobs to shifts }
                .collect { (jobs, shifts) ->
                    val options = jobs.filter { it.status == "ACTIVE" }
                        .map { ClockJobOption(it.id, it.name, it.address, it.lat, it.lng) }
                    val status = d.controller.status(d.companyId, d.memberId)
                    val now = d.now()
                    val rules = TimesheetRules.from(settings.overtimeRule)
                    val sheet = WeekSheetBuilder.build(shifts, rules, settings.breakRule, d.zone, now)
                    // The last job worked is preselected, so the widget's Clock in lands on it (AC-F17).
                    val lastJob = shifts.lastOrNull()?.segments?.lastOrNull()?.jobId?.takeIf { id -> options.any { it.id == id } }
                    _state.update { s ->
                        val selected = s.selectedJobId?.takeIf { id -> options.any { it.id == id } } ?: lastJob ?: options.singleOrNull()?.id
                        s.copy(loading = false, jobs = options, status = status, selectedJobId = selected, sheet = sheet, now = now)
                    }
                }
        }
        viewModelScope.launch { d.ticks.collect { _state.update { it.copy(now = d.now()) } } }
    }

    private fun ctx() = ClockContext(d.companyId, d.memberId, settings)
    private fun jobFor(id: String?): ClockJob? = _state.value.jobs.firstOrNull { it.id == id }?.let { ClockJob(it.id, it.lat, it.lng) }

    fun onEvent(event: ClockEvent) {
        when (event) {
            is ClockEvent.SelectJob -> { saved[JOB] = event.id; _state.update { it.copy(selectedJobId = event.id) } }
            is ClockEvent.Note -> { saved[NOTE] = event.text; _state.update { it.copy(note = event.text) } }
            ClockEvent.ClockIn -> {
                if (!d.location.hasPermission() && !d.permissionAsked()) _state.update { it.copy(explainLocation = true) }
                else run("in")
            }
            ClockEvent.SwitchJob -> run("switch")
            ClockEvent.StartBreak -> act { d.controller.startBreak(ctx()) }
            ClockEvent.EndBreak -> act { d.controller.endBreak(ctx()) }
            ClockEvent.ClockOut -> act { d.controller.clockOut(ctx(), note()).also { clearNote() } }
            ClockEvent.ExplainContinue -> {
                d.markPermissionAsked()
                _state.update { it.copy(explainLocation = false, requestPermission = true) }
            }
            ClockEvent.ExplainSkip -> {
                d.markPermissionAsked()
                _state.update { it.copy(explainLocation = false) }
                run("in")
            }
            is ClockEvent.PermissionResult -> {
                _state.update { it.copy(requestPermission = false) }
                run("in")
            }
            is ClockEvent.PickReason -> _state.update { it.copy(farReason = it.farReason?.copy(choice = event.choice)) }
            is ClockEvent.OtherReason -> _state.update { it.copy(farReason = it.farReason?.copy(other = event.text)) }
            ClockEvent.SaveReason -> {
                val reason = _state.value.farReason?.reason ?: return
                val kind = pending ?: return
                _state.update { it.copy(farReason = null) }
                run(kind, reason)
            }
            ClockEvent.CancelReason -> { setPending(null); _state.update { it.copy(farReason = null) } }
            ClockEvent.DismissGps -> _state.update { it.copy(gpsRequired = null) }
            ClockEvent.OpenSettings -> Unit // handled by ClockRoute
            ClockEvent.DismissNotice -> _state.update { it.copy(savedWithoutLocation = false) }
        }
    }

    private fun note() = _state.value.note.takeIf { it.isNotBlank() }
    private fun clearNote() { saved[NOTE] = ""; _state.update { it.copy(note = "") } }
    private fun setPending(kind: String?) { pending = kind; saved[PENDING] = kind }

    private fun run(kind: String, reason: String? = null) {
        val s = _state.value
        val job = jobFor(s.selectedJobId)
        if (kind == "in" && s.selectedJobId == null && s.jobs.isNotEmpty()) return // a job is required when the company has any
        act {
            when (kind) {
                "switch" -> job?.let { d.controller.switchJob(ctx(), it, reason, note()) } ?: ClockResult.NotAllowed
                else -> d.controller.clockIn(ctx(), job, reason, note())
            }.also { r -> if (r is ClockResult.NeedsReason) setPending(kind) else setPending(null) }
        }
    }

    private fun act(block: suspend () -> ClockResult) {
        viewModelScope.launch {
            val locating = _state.value.status !is ClockStatus.OnBreak
            _state.update { it.copy(gettingLocation = locating) }
            val result = block()
            val status = d.controller.status(d.companyId, d.memberId)
            _state.update { s ->
                val withStatus = s.copy(gettingLocation = false, status = status, now = d.now())
                when (result) {
                    is ClockResult.Done -> {
                        if (result.noLocation) withStatus.copy(savedWithoutLocation = true, note = "").also { saved[NOTE] = "" }
                        else withStatus.copy(note = "").also { saved[NOTE] = "" }
                    }
                    is ClockResult.NeedsReason -> withStatus.copy(farReason = FarReasonSheet(result.distanceFt))
                    is ClockResult.GpsRequired -> withStatus.copy(gpsRequired = result.permissionMissing)
                    ClockResult.NotAllowed -> withStatus
                }
            }
        }
    }

    private companion object {
        const val JOB = "job"
        const val NOTE = "note"
        const val PENDING = "pending"
    }
}
