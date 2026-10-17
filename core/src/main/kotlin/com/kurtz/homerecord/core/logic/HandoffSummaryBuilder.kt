package com.kurtz.homerecord.core.logic

/** Labels for the handoff PDF, supplied by the caller from strings.xml. */
data class HandoffLabels(
    val rooms: String,
    val walls: String,
    val behindWallPhotos: String,
    val equipment: String,
    val finishes: String,
    val shutoffs: String,
    val panels: String,
)

/** Pure rows of the handoff summary PDF; rendered through the same writer as the daily report. */
object HandoffSummaryBuilder {
    fun sections(counts: HandoffCounts, labels: HandoffLabels): List<ReportSection> = listOf(
        ReportSection(labels.rooms, counts.rooms.toString()),
        ReportSection(labels.walls, counts.walls.toString()),
        ReportSection(labels.behindWallPhotos, counts.behindWallPhotos.toString()),
        ReportSection(labels.equipment, counts.equipment.toString()),
        ReportSection(labels.finishes, counts.finishes.toString()),
        ReportSection(labels.shutoffs, counts.shutoffs.toString()),
        ReportSection(labels.panels, counts.panels.toString()),
    )

    fun pdfFileName(jobId: String): String = "handoff-${safe(jobId)}.pdf"

    fun bundleFileName(jobId: String): String = "handoff-${safe(jobId)}.homerecord"

    private fun safe(s: String) = s.filter { it.isLetterOrDigit() || it == '-' }
}
