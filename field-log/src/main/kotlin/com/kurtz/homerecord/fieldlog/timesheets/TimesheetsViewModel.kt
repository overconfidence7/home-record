package com.kurtz.homerecord.fieldlog.timesheets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.data.TimesheetApprovalEntity
import com.kurtz.homerecord.core.model.TimesheetRules
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Everything Timesheets needs from outside, so tests can swap each piece. Owner and Office only (AC-F3). */
class TimesheetsDeps(
    val callerId: String,
    val callerName: String,
    val punchRepo: PunchRepository,
    val approvals: TimesheetApprovals,
    val members: Flow<List<MemberEntity>>,
    val punches: Flow<List<PunchEntity>>,
    val jobs: Flow<List<JobEntity>>,
    val approvalRows: Flow<List<TimesheetApprovalEntity>>,
    val settings: suspend () -> CompanySettings,
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
        viewModelScope.launch { settings = d.settings() }
        viewModelScope.launch {
            combine(d.members, d.punches, d.jobs, d.approvalRows, week) { members, punches, jobs, approvals, start ->
                latestPunches = punches
                latestJobs = jobs.filter { it.status == "ACTIVE" }.map { JobChoice(it.id, it.name) }
                val ids = members.map { it.id }.toSet()
                val people = TimesheetsBuilder.build(
                    members, punches, jobs.associate { it.id to it.name },
                    approvals.filter { it.memberId in ids }.map { it.memberId to it.weekStart }.toSet(),
                    start, TimesheetRules.from(settings.overtimeRule), settings.breakRule, d.zone, d.now(),
                )
                start to people
            }.collect { (start, people) ->
                _state.update { it.copy(loading = false, weekStart = start, isCurrentWeek = start == thisWeek, people = people) }
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
            is TimesheetsEvent.EditTime -> editSheet { it.copy(timeText = event.text, error = null) }
            is TimesheetsEvent.EditJob -> editSheet { it.copy(jobId = event.jobId) }
            is TimesheetsEvent.EditReason -> editSheet { it.copy(reason = event.text, error = null) }
            TimesheetsEvent.SaveEdit -> save()
            TimesheetsEvent.CancelEdit -> _state.update { it.copy(edit = null) }
        }
    }

    private fun moveTo(start: LocalDate) {
        weekStart = start
        saved[WEEK] = start.toString()
        saved[OPEN] = null
        week.value = start
        _state.update { it.copy(expandedId = null, edit = null) }
    }

    private fun editSheet(block: (PunchEditSheet) -> PunchEditSheet) = _state.update { s -> s.edit?.let { s.copy(edit = block(it)) } ?: s }

    private fun openEdit(punchId: String) {
        val person = _state.value.people.firstOrNull { p -> p.punches.any { it.id == punchId } } ?: return
        val punch = person.punches.first { it.id == punchId }
        if (person.approved) return // an approved week is locked (AC-F14)
        val local = Instant.ofEpochMilli(punch.at).atZone(d.zone)
        _state.update {
            it.copy(
                edit = PunchEditSheet(
                    punchId, person.name, punch.kind, local.format(DateTimeFormatter.ofPattern("EEE MMM d")),
                    local.format(DateTimeFormatter.ofPattern("h:mm a")), punch.jobId, latestJobs, audit = punch.edits,
                ),
            )
        }
    }

    private fun save() {
        val sheet = _state.value.edit ?: return
        if (sheet.reason.isBlank()) { editSheet { it.copy(error = EditError.ReasonRequired) }; return }
        val time = TimeText.parse(sheet.timeText) ?: run { editSheet { it.copy(error = EditError.BadTime) }; return }
        val old = latestPunches.firstOrNull { it.id == sheet.punchId } ?: return
        val newAt = Instant.ofEpochMilli(old.at).atZone(d.zone).toLocalDate().atTime(time).atZone(d.zone).toInstant().toEpochMilli()
        viewModelScope.launch {
            val ok = d.punchRepo.edit(sheet.punchId, d.callerId, d.callerName, newAt, sheet.jobId, sheet.reason)
            if (ok) _state.update { it.copy(edit = null) } else editSheet { it.copy(error = EditError.Locked) }
        }
    }

    private fun mondayOf(at: Long): LocalDate =
        Instant.ofEpochMilli(at).atZone(d.zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private companion object {
        const val WEEK = "week"
        const val OPEN = "open"
    }
}
