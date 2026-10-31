package com.kurtz.homerecord.fieldlog.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.DelayDay
import com.kurtz.homerecord.core.model.DelayTimeline
import com.kurtz.homerecord.core.model.HoursReport
import com.kurtz.homerecord.core.model.ShiftBuilder
import com.kurtz.homerecord.core.model.ShiftPunch
import com.kurtz.homerecord.core.model.TimesheetRules
import com.kurtz.homerecord.core.punch.TimeExportBuilder
import com.kurtz.homerecord.core.punch.TimeExportFile
import com.kurtz.homerecord.core.punch.TimeExportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Everything Reports needs from outside; [share] writes the file and opens the share sheet. Owner and Office only (AC-F3). */
class ReportsDeps(
    val members: Flow<List<MemberEntity>>,
    val punches: Flow<List<PunchEntity>>,
    val jobs: Flow<List<JobEntity>>,
    val clients: Flow<List<ClientEntity>>,
    val settings: Flow<CompanySettings>,
    val share: suspend (TimeExportFile) -> Unit,
    /** The daily logs' delays from one day to another, inclusive (AC-F32). */
    val delays: (from: LocalDate, to: LocalDate) -> Flow<List<DelayDay>> = { _, _ -> flowOf(emptyList()) },
    val zone: ZoneId = ZoneId.systemDefault(),
    val now: () -> Long = System::currentTimeMillis,
)

/** FL-Reports: hours by person and by job over a week range, the two CSV exports (AC-F15, AC-F46), and the job timeline of delays (AC-F32). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val d: ReportsDeps, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ReportsUiState(range = rangeOf(saved[RANGE])))
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    private var settings = CompanySettings()
    private val range = MutableStateFlow(_state.value.range)
    private var latest: Snapshot? = null

    private class Snapshot(
        val members: List<MemberEntity>, val punches: List<PunchEntity>, val jobs: List<JobEntity>, val clients: List<ClientEntity>,
    )

    init {
        viewModelScope.launch {
            combine(d.members, d.punches, d.jobs, d.clients, combine(range, d.settings, ::Pair)) { members, punches, jobs, clients, (r, rules) ->
                settings = rules
                latest = Snapshot(members, punches, jobs, clients)
                val weeks = weeksOf(r)
                val now = d.now()
                val live = punches.filter { it.deletedAt == null }
                val shifts = ShiftBuilder.build(live.map { ShiftPunch(it.memberId, it.jobId, it.kind, it.at, it.farFromJob, it.editsJson != "[]") }, now)
                val result = HoursReport.build(
                    shifts, weeks, members.associate { it.id to it.name }, jobs.associate { it.id to it.name },
                    TimesheetRules.from(settings.overtimeRule), d.zone, now,
                )
                Triple(r, weeks, result)
            }.collect { (r, weeks, result) ->
                _state.update {
                    it.copy(
                        loading = false, range = r, from = weeks.first(), to = weeks.last().plusDays(6),
                        byPerson = result.byPerson, byJob = result.byJob, totalMillis = result.totalMillis,
                        regularMillis = result.regularMillis, overtimeMillis = result.overtimeMillis, doubleMillis = result.doubleMillis,
                    )
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            range.flatMapLatest { r ->
                val weeks = weeksOf(r)
                val from = weeks.first()
                val to = weeks.last().plusDays(6)
                combine(d.delays(from, to), d.jobs) { days, jobs -> DelayTimeline.build(days, jobs.associate { it.id to it.name }, from, to) }
            }.collect { timeline -> _state.update { it.copy(delays = timeline) } }
        }
    }

    fun onEvent(event: ReportsEvent) {
        when (event) {
            is ReportsEvent.SelectRange -> {
                saved[RANGE] = event.range.name
                range.value = event.range
            }
            ReportsEvent.ExportShifts -> export(TimeExportKind.ShiftCsv)
            ReportsEvent.ExportQuickBooks -> export(TimeExportKind.QuickBooksCsv)
            ReportsEvent.DismissNotice -> _state.update { it.copy(notice = null) }
        }
    }

    private fun export(kind: TimeExportKind) {
        val data = latest ?: return
        if (_state.value.exporting) return
        if (_state.value.isEmpty) { _state.update { it.copy(notice = ExportNotice.Empty) }; return }
        _state.update { it.copy(exporting = true, notice = null) }
        viewModelScope.launch {
            val notice = try {
                val file = TimeExportBuilder.build(
                    kind, data.members, data.punches, data.jobs, data.clients, weeksOf(range.value),
                    TimesheetRules.from(settings.overtimeRule), settings.breakRule, d.zone, d.now(),
                )
                d.share(file)
                ExportNotice.Shared
            } catch (e: java.io.IOException) {
                ExportNotice.Failed
            } catch (e: android.content.ActivityNotFoundException) {
                ExportNotice.Failed
            }
            _state.update { it.copy(exporting = false, notice = notice) }
        }
    }

    private fun weeksOf(r: ReportRange): List<LocalDate> {
        val thisWeek = Instant.ofEpochMilli(d.now()).atZone(d.zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = if (r == ReportRange.ThisWeek) thisWeek else thisWeek.minusWeeks(1)
        return (r.weeks - 1 downTo 0).map { last.minusWeeks(it.toLong()) }
    }

    private fun rangeOf(name: String?) = ReportRange.entries.firstOrNull { it.name == name } ?: ReportRange.ThisWeek

    private companion object {
        const val RANGE = "range"
    }
}
