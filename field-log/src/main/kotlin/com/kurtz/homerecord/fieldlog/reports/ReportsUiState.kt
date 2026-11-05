package com.kurtz.homerecord.fieldlog.reports

import com.kurtz.homerecord.core.model.HoursLine
import com.kurtz.homerecord.core.model.JobDelays
import java.time.LocalDate

/** Which whole Monday weeks a report covers (AC-F46). */
enum class ReportRange(val weeks: Int) { ThisWeek(1), LastWeek(1), LastFourWeeks(4) }

/** The three report types, one tab each, so each is one tap from Reports (rule 12). */
enum class ReportTab { Hours, Photos, Summaries }

enum class ExportNotice { Shared, Failed, Empty }

data class ReportsUiState(
    val loading: Boolean = true,
    val range: ReportRange = ReportRange.ThisWeek,
    val tab: ReportTab = ReportTab.Hours,
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
    /** Every job, Active first, each offering its summary PDF (AC-F47). */
    val summaries: List<SummaryJob> = emptyList(),
    /** The job whose summary PDF is being made, or null. */
    val summaryBusy: String? = null,
    val summaryFailed: Boolean = false,
    /** Every job with its photos in the range, for the photo report PDF (AC-F28). */
    val photoJobs: List<PhotoReportJob> = emptyList(),
    /** The job whose photo report is being made, or null. */
    val photoBusy: String? = null,
    val photoNotice: PhotoReportOutcome? = null,
) {
    val isEmpty: Boolean get() = byPerson.isEmpty() && byJob.isEmpty()
}

/** A job row under Job summaries; [line] is its address, or null. */
data class SummaryJob(val id: String, val name: String, val line: String?)

/** A job row under Photo reports; [photos] is how many it has in the range (0 leaves the row untappable). */
data class PhotoReportJob(val id: String, val name: String, val photos: Int)

sealed interface ReportsEvent {
    data class SharePhotoReport(val jobId: String) : ReportsEvent
    data class ShareJobSummary(val jobId: String) : ReportsEvent
    data class SelectTab(val tab: ReportTab) : ReportsEvent
    data class SelectRange(val range: ReportRange) : ReportsEvent
    data object ExportShifts : ReportsEvent
    data object ExportQuickBooks : ReportsEvent
    data object DismissNotice : ReportsEvent
}
