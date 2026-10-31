package com.kurtz.homerecord.fieldlog.reports

import com.kurtz.homerecord.core.model.HoursLine
import com.kurtz.homerecord.core.model.JobDelays
import java.time.LocalDate

/** Which whole Monday weeks a report covers (AC-F46). */
enum class ReportRange(val weeks: Int) { ThisWeek(1), LastWeek(1), LastFourWeeks(4) }

enum class ExportNotice { Shared, Failed, Empty }

data class ReportsUiState(
    val loading: Boolean = true,
    val range: ReportRange = ReportRange.ThisWeek,
    val from: LocalDate = LocalDate.of(2026, 1, 5),
    val to: LocalDate = LocalDate.of(2026, 1, 11),
    val byPerson: List<HoursLine> = emptyList(),
    val byJob: List<HoursLine> = emptyList(),
    val totalMillis: Long = 0,
    val regularMillis: Long = 0,
    val overtimeMillis: Long = 0,
    val doubleMillis: Long = 0,
    /** The job timeline of delays in the range (AC-F32), most delayed job first. */
    val delays: List<JobDelays> = emptyList(),
    val exporting: Boolean = false,
    val notice: ExportNotice? = null,
) {
    val isEmpty: Boolean get() = byPerson.isEmpty() && byJob.isEmpty()
}

sealed interface ReportsEvent {
    data class SelectRange(val range: ReportRange) : ReportsEvent
    data object ExportShifts : ReportsEvent
    data object ExportQuickBooks : ReportsEvent
    data object DismissNotice : ReportsEvent
}
