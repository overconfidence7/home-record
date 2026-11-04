package com.kurtz.homerecord.fieldlog.schedule

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.model.ScheduleRow
import com.kurtz.homerecord.core.model.ScheduleWeek
import com.kurtz.homerecord.core.model.ScheduledJob
import java.time.LocalDate

/** A job that can be put on the schedule from this phone. */
@Immutable
data class ScheduleJobOption(val id: String, val name: String)

/** What sheet is open on FL-Schedule. */
sealed interface ScheduleSheet {
    /** "Which job?" for [name] on [date]. */
    data class PickJob(val memberId: String, val name: String, val date: LocalDate) : ScheduleSheet

    /** Move or take off one scheduled day. */
    data class Day(val memberId: String, val name: String, val date: LocalDate, val job: ScheduledJob) : ScheduleSheet
}

@Immutable
data class ScheduleUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    val weekStart: LocalDate = ScheduleWeek.weekStart(today),
    /** The day the crew list shows (Owner, Office and Lead). */
    val selected: LocalDate = today,
    val rows: List<ScheduleRow> = emptyList(),
    /** Crew and Sub see their own week only, read only (AC-F41). */
    val ownOnly: Boolean = false,
    /** Jobs this person may schedule: every Active or Upcoming job for Owner and Office, the jobs they run for a Lead. */
    val jobs: List<ScheduleJobOption> = emptyList(),
    val sheet: ScheduleSheet? = null,
    /**
     * Jobs on the week this person may move or take off besides [jobs]: a Done job someone is still scheduled on can come
     * off the schedule even though it is no longer offered for new days (R11).
     */
    val changeable: Set<String> = emptySet(),
) {
    val days: List<LocalDate> get() = ScheduleWeek.days(weekStart)
    val isThisWeek: Boolean get() = weekStart == ScheduleWeek.weekStart(today)
    val canAssign: Boolean get() = !ownOnly && jobs.isNotEmpty()

    fun canChange(jobId: String): Boolean = !ownOnly && (jobId in changeable || jobs.any { it.id == jobId })

    /** Someone is on a job that day. */
    fun busy(day: LocalDate): Boolean = rows.any { row -> row.cells.any { it.date == day && it.jobs.isNotEmpty() } }

    /** Someone has two jobs that day (AC-F43). */
    fun conflict(day: LocalDate): Boolean = rows.any { row -> row.cells.any { it.date == day && it.conflict } }
}

sealed interface ScheduleEvent {
    data object PreviousWeek : ScheduleEvent
    data object NextWeek : ScheduleEvent
    data object ThisWeek : ScheduleEvent
    data class SelectDay(val day: LocalDate) : ScheduleEvent
    data class Assign(val memberId: String) : ScheduleEvent
    data class PickJob(val jobId: String) : ScheduleEvent
    data class OpenDay(val memberId: String, val assignmentId: String) : ScheduleEvent
    data class MoveTo(val day: LocalDate) : ScheduleEvent
    data object Remove : ScheduleEvent
    data object Dismiss : ScheduleEvent
}
