package com.kurtz.homerecord.fieldlog.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.core.model.ScheduleEntry
import com.kurtz.homerecord.core.model.ScheduleWeek
import com.kurtz.homerecord.core.model.effectiveStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything FL-Schedule reads and writes, so tests feed rows directly (AC-F41). */
class ScheduleDeps(
    val memberId: String,
    val members: Flow<List<MemberEntity>>,
    val jobs: Flow<List<JobEntity>>,
    val entries: Flow<List<ScheduleEntry>>,
    val gates: Flow<RoleGates>,
    val assign: suspend (jobId: String, memberId: String, date: LocalDate) -> String?,
    val move: suspend (id: String, date: LocalDate) -> Boolean,
    val remove: suspend (id: String) -> Boolean,
    /** A day a tapped schedule push asks to show (AC-F42); [consumeOpenDay] clears it once shown. */
    val openDay: Flow<LocalDate?> = kotlinx.coroutines.flow.flowOf(null),
    val consumeOpenDay: () -> Unit = {},
    val zone: ZoneId = ZoneId.systemDefault(),
    val now: () -> Long = System::currentTimeMillis,
)

/**
 * FL-Schedule (AC-F41, AC-F43): the week with a row per person. Owner, Office and a Lead pick a day and put people on
 * jobs; a scheduled day can move to another day or come off. Crew and Sub see their own week. Week and day survive
 * process death.
 */
class ScheduleViewModel(private val d: ScheduleDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val today: LocalDate = Instant.ofEpochMilli(d.now()).atZone(d.zone).toLocalDate()
    private val _state = MutableStateFlow(
        ScheduleUiState(
            today = today,
            weekStart = saved.get<String>(KEY_WEEK)?.let(LocalDate::parse) ?: ScheduleWeek.weekStart(today),
            selected = saved.get<String>(KEY_DAY)?.let(LocalDate::parse) ?: today,
        ),
    )
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()
    private val week = MutableStateFlow(_state.value.weekStart)

    init {
        viewModelScope.launch {
            d.openDay.collect { day ->
                if (day != null) {
                    showWeek(ScheduleWeek.weekStart(day), day)
                    d.consumeOpenDay()
                }
            }
        }
        viewModelScope.launch {
            combine(d.members, d.jobs, d.entries, d.gates, week) { members, jobs, entries, gates, weekStart ->
                val day = today.toString()
                val shown = jobs.filter { it.deletedAt == null && it.effectiveStatus(day) != JobStatus.ARCHIVED }
                val names = shown.associate { it.id to it.name }
                val ownOnly = gates.role == CompanyRole.Crew || gates.role == CompanyRole.Sub
                val people = members.filter { it.deletedAt == null && (!ownOnly || it.id == d.memberId) }.map { it.id to it.name }
                val options = if (ownOnly) emptyList() else shown
                    .filter { it.effectiveStatus(day) in setOf(JobStatus.ACTIVE, JobStatus.UPCOMING) && gates.leads(it.id) }
                    .sortedBy { it.name.lowercase() }
                    .map { ScheduleJobOption(it.id, it.name) }
                Triple(ScheduleWeek.build(weekStart, people, entries, names), ownOnly, options)
            }.collect { (rows, ownOnly, options) ->
                _state.update { it.copy(loading = false, rows = rows, ownOnly = ownOnly, jobs = options, sheet = it.sheet?.takeIf { s -> s.stillValid(rows) }) }
            }
        }
    }

    fun onEvent(e: ScheduleEvent) {
        when (e) {
            ScheduleEvent.PreviousWeek -> showWeek(_state.value.weekStart.minusWeeks(1))
            ScheduleEvent.NextWeek -> showWeek(_state.value.weekStart.plusWeeks(1))
            ScheduleEvent.ThisWeek -> showWeek(ScheduleWeek.weekStart(today), today)
            is ScheduleEvent.SelectDay -> select(e.day)
            is ScheduleEvent.Assign -> {
                val s = _state.value
                val row = s.rows.firstOrNull { it.memberId == e.memberId } ?: return
                if (s.canAssign) _state.update { it.copy(sheet = ScheduleSheet.PickJob(row.memberId, row.name, s.selected)) }
            }
            is ScheduleEvent.PickJob -> {
                val sheet = _state.value.sheet as? ScheduleSheet.PickJob ?: return
                if (_state.value.jobs.none { it.id == e.jobId }) return
                _state.update { it.copy(sheet = null) }
                viewModelScope.launch { d.assign(e.jobId, sheet.memberId, sheet.date) }
            }
            is ScheduleEvent.OpenDay -> {
                val s = _state.value
                val row = s.rows.firstOrNull { it.memberId == e.memberId } ?: return
                val cell = row.cells.firstOrNull { c -> c.jobs.any { it.assignmentId == e.assignmentId } } ?: return
                val job = cell.jobs.first { it.assignmentId == e.assignmentId }
                if (s.canChange(job.jobId)) _state.update { it.copy(sheet = ScheduleSheet.Day(row.memberId, row.name, cell.date, job)) }
            }
            is ScheduleEvent.MoveTo -> {
                val sheet = _state.value.sheet as? ScheduleSheet.Day ?: return
                _state.update { it.copy(sheet = null) }
                viewModelScope.launch { d.move(sheet.job.assignmentId, e.day) }
            }
            ScheduleEvent.Remove -> {
                val sheet = _state.value.sheet as? ScheduleSheet.Day ?: return
                _state.update { it.copy(sheet = null) }
                viewModelScope.launch { d.remove(sheet.job.assignmentId) }
            }
            ScheduleEvent.Dismiss -> _state.update { it.copy(sheet = null) }
        }
    }

    private fun showWeek(weekStart: LocalDate, day: LocalDate = weekStart.plusDays((_state.value.selected.dayOfWeek.value - 1).toLong())) {
        saved[KEY_WEEK] = weekStart.toString()
        saved[KEY_DAY] = day.toString()
        _state.update { it.copy(weekStart = weekStart, selected = day, sheet = null) }
        week.value = weekStart
    }

    private fun select(day: LocalDate) {
        if (day !in _state.value.days) return
        saved[KEY_DAY] = day.toString()
        _state.update { it.copy(selected = day) }
    }

    private fun ScheduleSheet.stillValid(rows: List<com.kurtz.homerecord.core.model.ScheduleRow>): Boolean = when (this) {
        is ScheduleSheet.PickJob -> rows.any { it.memberId == memberId }
        is ScheduleSheet.Day -> rows.any { r -> r.memberId == memberId && r.cells.any { c -> c.jobs.any { it.assignmentId == job.assignmentId } } }
    }

    private companion object {
        const val KEY_WEEK = "schedule_week"
        const val KEY_DAY = "schedule_day"
    }
}
