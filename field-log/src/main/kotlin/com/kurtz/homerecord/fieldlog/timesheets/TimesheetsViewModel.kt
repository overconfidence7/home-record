package com.kurtz.homerecord.fieldlog.timesheets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.data.TimesheetApprovalEntity
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.core.model.TimesheetRules
import com.kurtz.homerecord.core.model.effectiveStatus
import com.kurtz.homerecord.core.punch.PunchRepository
import com.kurtz.homerecord.core.punch.TimesheetApprovals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Everything Timesheets needs from outside, so tests can swap each piece. Owner and Office only (AC-F3). */
class TimesheetsDeps(
    val callerId: String,
    val callerName: suspend () -> String,
    val punchRepo: PunchRepository,
    val approvals: TimesheetApprovals,
    val members: Flow<List<MemberEntity>>,
    val punches: Flow<List<PunchEntity>>,
    val jobs: Flow<List<JobEntity>>,
    val approvalRows: Flow<List<TimesheetApprovalEntity>>,
    val settings: Flow<CompanySettings>,
    val zone: ZoneId = ZoneId.systemDefault(),
    val now: () -> Long = System::currentTimeMillis,
)

/** FL-Timesheets: a week per person with flags, punch edits with a reason, Approve person or all (AC-F14). */
class TimesheetsViewModel(private val d: TimesheetsDeps, private val saved: SavedStateHandle) : ViewModel() {
    private val thisWeek = mondayOf(d.now())
    private var weekStart: LocalDate = saved.get<String>(WEEK)?.let(LocalDate::parse) ?: thisWeek
    private val _state = MutableStateFlow(
        TimesheetsUiState(weekStart = weekStart, isCurrentWeek = weekStart == thisWeek, expandedId = saved[OPEN]),
    )
    val state: StateFlow<TimesheetsUiState> = _state.asStateFlow()

    private var settings = CompanySettings()
    private val week = MutableStateFlow(weekStart)
    private var latestPunches: List<PunchEntity> = emptyList()
    private var latestJobs: List<JobChoice> = emptyList()

    init {
        viewModelScope.launch {
            combine(d.members, d.punches, d.jobs, d.approvalRows, combine(week, d.settings, ::Pair)) { members, punches, jobs, approvals, (start, rules) ->
                settings = rules
                latestPunches = punches
                val today = Instant.ofEpochMilli(d.now()).atZone(d.zone).toLocalDate().toString()
                latestJobs = jobs.filter { it.effectiveStatus(today) == JobStatus.ACTIVE }.map { JobChoice(it.id, it.name) }
                val ids = members.map { it.id }.toSet()
                val people = TimesheetsBuilder.build(
                    members, punches, jobs.associate { it.id to it.name },
                    approvals.filter { it.memberId in ids }.map { it.memberId to it.weekStart }.toSet(),
                    start, TimesheetRules.from(settings.overtimeRule), settings.breakRule, d.zone, d.now(),
                )
                start to people
            }.collect { (start, people) ->
                _state.update { it.copy(loading = false, weekStart = start, isCurrentWeek = start == thisWeek, people = people) }
                restoreEdit()
            }
        }
    }

    fun onEvent(event: TimesheetsEvent) {
        when (event) {
            TimesheetsEvent.PreviousWeek -> moveTo(weekStart.minusWeeks(1))
            TimesheetsEvent.NextWeek -> if (weekStart < thisWeek) moveTo(weekStart.plusWeeks(1))
            is TimesheetsEvent.Toggle -> {
                val next = if (_state.value.expandedId == event.memberId) null else event.memberId
                saved[OPEN] = next
                _state.update { it.copy(expandedId = next) }
            }
            is TimesheetsEvent.Approve -> viewModelScope.launch { d.approvals.approve(event.memberId, weekStart.toString(), d.callerId) }
            TimesheetsEvent.AskApproveAll -> _state.update { it.copy(confirmApproveAll = true) }
            TimesheetsEvent.CancelApproveAll -> _state.update { it.copy(confirmApproveAll = false) }
            TimesheetsEvent.ApproveAll -> {
                val ids = _state.value.people.filter { !it.approved && it.punches.isNotEmpty() }.map { it.memberId }
                _state.update { it.copy(confirmApproveAll = false) }
                viewModelScope.launch { d.approvals.approveAll(ids, weekStart.toString(), d.callerId) }
            }
            is TimesheetsEvent.EditPunch -> openEdit(event.punchId)
            is TimesheetsEvent.EditTime -> editSheet { it.copy(time = event.time) }
            is TimesheetsEvent.EditJob -> editSheet { it.copy(jobId = event.jobId) }
            is TimesheetsEvent.EditReason -> editSheet { it.copy(reason = event.text, error = null) }
            TimesheetsEvent.SaveEdit -> save()
            TimesheetsEvent.CancelEdit -> closeEdit()
        }
    }

    private fun moveTo(start: LocalDate) {
        weekStart = start
        saved[WEEK] = start.toString()
        saved[OPEN] = null
        week.value = start
        _state.update { it.copy(expandedId = null) }
        closeEdit()
    }

    /** Changes the open form and keeps it in [saved] so it survives process death (CLAUDE.md forms rule). */
    private fun editSheet(block: (PunchEditSheet) -> PunchEditSheet) {
        _state.update { s -> s.edit?.let { s.copy(edit = block(it)) } ?: s }
        persistEdit()
    }

    private fun persistEdit() {
        val e = _state.value.edit ?: return
        saved[EDIT] = e.punchId
        saved[EDIT_TIME] = e.time.toSecondOfDay()
        saved[EDIT_JOB] = e.jobId
        saved[EDIT_REASON] = e.reason
    }

    private fun closeEdit() {
        saved[EDIT] = null
        _state.update { it.copy(edit = null) }
    }

    /** Reopens the form the system killed, with what was typed, once its punch has loaded. */
    private fun restoreEdit() {
        val id = saved.get<String>(EDIT) ?: return
        if (_state.value.edit != null) return
        if (_state.value.people.none { p -> p.punches.any { it.id == id } }) return
        val time = saved.get<Int>(EDIT_TIME)?.let { LocalTime.ofSecondOfDay(it.toLong()) }
        val hasJob = saved.contains(EDIT_JOB)
        val jobId = saved.get<String>(EDIT_JOB)
        val reason = saved.get<String>(EDIT_REASON).orEmpty()
        val sheet = sheetFor(id) ?: run { saved[EDIT] = null; return }
        _state.update { it.copy(edit = sheet.copy(time = time ?: sheet.time, jobId = if (hasJob) jobId else sheet.jobId, reason = reason)) }
        persistEdit()
    }

    private fun openEdit(punchId: String) {
        val sheet = sheetFor(punchId) ?: return
        _state.update { it.copy(edit = sheet) }
        persistEdit()
    }

    private fun sheetFor(punchId: String): PunchEditSheet? {
        val person = _state.value.people.firstOrNull { p -> p.punches.any { it.id == punchId } } ?: return null
        val punch = person.punches.first { it.id == punchId }
        if (person.approved) return null // an approved week is locked (AC-F14)
        val local = Instant.ofEpochMilli(punch.at).atZone(d.zone)
        return PunchEditSheet(
            punchId, person.name, punch.kind, local.format(DateTimeFormatter.ofPattern("EEE MMM d")),
            local.toLocalTime().withSecond(0).withNano(0), punch.jobId, latestJobs, audit = punch.edits,
        )
    }

    private fun save() {
        val sheet = _state.value.edit ?: return
        if (sheet.reason.isBlank()) { editSheet { it.copy(error = EditError.ReasonRequired) }; return }
        val old = latestPunches.firstOrNull { it.id == sheet.punchId } ?: return
        val newAt = Instant.ofEpochMilli(old.at).atZone(d.zone).toLocalDate().atTime(sheet.time).atZone(d.zone).toInstant().toEpochMilli()
        viewModelScope.launch {
            val ok = d.punchRepo.edit(sheet.punchId, d.callerId, d.callerName(), newAt, sheet.jobId, sheet.reason)
            if (ok) closeEdit() else editSheet { it.copy(error = EditError.Locked) }
        }
    }

    private fun mondayOf(at: Long): LocalDate =
        Instant.ofEpochMilli(at).atZone(d.zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private companion object {
        const val WEEK = "week"
        const val OPEN = "open"
        const val EDIT = "edit"
        const val EDIT_TIME = "edit_time"
        const val EDIT_JOB = "edit_job"
        const val EDIT_REASON = "edit_reason"
    }
}
