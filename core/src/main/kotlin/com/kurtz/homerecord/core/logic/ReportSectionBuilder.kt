package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.DailyReport

/** Header lines of the PDF; blank values are dropped. */
data class ReportHeader(val company: String, val job: String, val address: String, val date: String)

/** One labelled text block of the PDF body. */
data class ReportSection(val label: String, val text: String)

/** Labels supplied by the caller from strings.xml. */
data class ReportLabels(
    val weather: String,
    val crew: String,
    val hours: String,
    val work: String,
    val delays: String,
    val materials: String,
    val nextSteps: String,
)

/** Pure layout rules for the daily report PDF (AC-69, AC-70). */
object ReportSectionBuilder {
    const val MAX_PHOTOS = 6
    const val PHOTOS_PER_ROW = 2

    /** Sections with content, in report order. [delayReasonLabel] is null when no reason (or "none") is set. */
    fun sections(report: DailyReport, labels: ReportLabels, delayReasonLabel: String?): List<ReportSection> {
        val delays = listOfNotNull(delayReasonLabel?.trim()?.ifEmpty { null }, report.delays?.trim()?.ifEmpty { null })
            .joinToString(": ")
        return listOf(
            ReportSection(labels.weather, report.weather.orEmpty()),
            ReportSection(labels.crew, report.crewCount?.toString().orEmpty()),
            ReportSection(labels.hours, report.hours?.let(::formatHours).orEmpty()),
            ReportSection(labels.work, report.workCompleted),
            ReportSection(labels.delays, delays),
            ReportSection(labels.materials, report.materials.orEmpty()),
            ReportSection(labels.nextSteps, report.nextSteps.orEmpty()),
        ).map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    }

    /** Up to [MAX_PHOTOS] photos, two per row. */
    fun photoRows(photos: List<String>): List<List<String>> = photos.take(MAX_PHOTOS).chunked(PHOTOS_PER_ROW)

    /** Same job + day always maps to the same file name, so regenerating overwrites (AC-70). */
    fun fileName(jobId: String, day: String): String = "report-${safe(jobId)}-${safe(day)}.pdf"

    private fun safe(s: String) = s.filter { it.isLetterOrDigit() || it == '-' }

    private fun formatHours(h: Double): String = if (h % 1.0 == 0.0) h.toLong().toString() else h.toString()
}
