package com.kurtz.homerecord.core.log

import kotlin.math.floor

/** One job photo as the photo report sees it (AC-F28); [path] is the file on this phone, or null when it is not here. */
data class ReportPhoto(
    val id: String,
    val jobId: String?,
    val takenAt: Long,
    val path: String?,
    val roomId: String? = null,
    val phase: String? = null,
    val note: String? = null,
    val deleted: Boolean = false,
)

/** A photo picked for the report with its caption parts; [day] is the day it was taken in the report's zone. */
data class ReportPhotoPick(
    val path: String,
    val takenAt: Long,
    val day: java.time.LocalDate,
    val room: String?,
    val phase: String?,
    val note: String?,
)

/**
 * Picks the photos of a photo report (AC-F28): the job's live photos taken from [from] to [to] (whole days, inclusive,
 * in [zone]), oldest first; photos still on another phone (no local file) are left out and counted in [left out].
 * A room that no longer exists drops out of the caption rather than printing an id.
 */
object PhotoReportPicker {
    data class Result(val photos: List<ReportPhotoPick>, val leftOut: Int)

    fun pick(
        photos: List<ReportPhoto>,
        jobId: String,
        from: java.time.LocalDate,
        to: java.time.LocalDate,
        zone: java.time.ZoneId,
        roomNames: Map<String, String>,
        fileExists: (String) -> Boolean,
    ): Result {
        val inRange = photos.filter { !it.deleted && it.jobId == jobId }
            .map { it to java.time.Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
            .filter { (_, day) -> !day.isBefore(from) && !day.isAfter(to) }
            .sortedWith(compareBy({ it.first.takenAt }, { it.first.id }))
        val here = inRange.filter { (p, _) -> p.path?.takeIf { it.isNotBlank() }?.let(fileExists) == true }
        return Result(
            here.map { (p, day) ->
                ReportPhotoPick(
                    path = p.path.orEmpty(), takenAt = p.takenAt, day = day,
                    room = p.roomId?.let(roomNames::get)?.trim()?.takeIf(String::isNotEmpty),
                    phase = p.phase?.trim()?.takeIf(String::isNotEmpty),
                    note = p.note?.trim()?.takeIf(String::isNotEmpty),
                )
            },
            inRange.size - here.size,
        )
    }

    /** How many of each job's live photos were taken in the range, for the Reports rows. */
    fun countByJob(photos: List<ReportPhoto>, from: java.time.LocalDate, to: java.time.LocalDate, zone: java.time.ZoneId): Map<String, Int> =
        photos.asSequence().filter { !it.deleted && it.jobId != null }
            .filter {
                val day = java.time.Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate()
                !day.isBefore(from) && !day.isAfter(to)
            }
            .groupingBy { it.jobId.orEmpty() }.eachCount()
}

/** What goes on a photo report PDF, already in words. */
data class PhotoReportContent(
    val company: String,
    val companyLine: String? = null,
    val logoPath: String? = null,
    /** "Photo report, Oct 26 to Nov 1, 2026". */
    val title: String,
    val job: String,
    val jobLine: String? = null,
    /** Under the title: "12 photos" and, when some are still on another phone, a line saying so. */
    val facts: List<String> = emptyList(),
    val photos: List<PhotoReportCell> = emptyList(),
)

/** One photo with its caption lines (room · phase, date, note), printed under it. */
data class PhotoReportCell(val path: String, val caption: List<String>)

/**
 * Lays a photo report out (AC-F28): the company header, title, job and facts on page one, then the photos two across
 * and two down, at most [PER_PAGE] to a page, each with its caption under it (at most [CAPTION_LINES] lines, the last
 * one ending in "…" when the note is longer). Later pages carry the job and title as a running head. Nothing is drawn
 * below [LogPdfPage.bottom] except the "Page n of m" footer.
 */
class PhotoReportLayout(
    private val measure: LogPdfMeasure,
    private val pageFooter: (page: Int, pages: Int) -> String,
    private val page: LogPdfPage = LogPdfPage(),
) {
    private val wrapper = LogPdfLayout(measure, pageFooter, page)
    private val left get() = page.marginPt
    private val right get() = page.widthPt - page.marginPt

    fun layout(content: PhotoReportContent): List<List<LogPdfOp>> {
        val pages = mutableListOf<MutableList<LogPdfOp>>()
        val cellW = (page.contentWidth - GAP) / 2
        val chunks = content.photos.chunked(PER_PAGE).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { index, chunk ->
            val ops = mutableListOf<LogPdfOp>()
            var y = if (index == 0) header(content, ops) else runningHead(content, ops)
            y += 12f
            // the two rows share what is left of the page; each row is a photo box and its caption
            val captionH = CAPTION_LINES * SMALL * LEADING + 4f
            val rowH = floor((page.bottom - y - GAP) / 2)
            val photoH = minOf(rowH - captionH, floor(cellW * 3f / 4f))
            chunk.chunked(2).forEach { row ->
                row.forEachIndexed { i, cell ->
                    val x = left + i * (cellW + GAP)
                    ops += LogPdfOp.Image(cell.path, x, y, x + cellW, y + photoH)
                    var base = y + photoH + 4f
                    caption(cell.caption, cellW).forEachIndexed { n, line ->
                        base += SMALL * LEADING
                        ops += LogPdfOp.Text(x, base - SMALL * (LEADING - 1), line, SMALL, bold = n == 0, muted = n > 0)
                    }
                }
                y += photoH + captionH + GAP
            }
            pages += ops
        }
        val total = pages.size
        pages.forEachIndexed { i, ops -> ops += LogPdfOp.Text(left, page.heightPt - page.marginPt, pageFooter(i + 1, total), SMALL, muted = true) }
        return pages
    }

    /** Page one: logo and company, a rule, the title, job, job line and facts; returns the y below them. */
    private fun header(content: PhotoReportContent, ops: MutableList<LogPdfOp>): Float {
        var y = page.marginPt
        val logo = content.logoPath
        val textLeft = if (logo != null) left + LOGO + 12f else left
        if (logo != null) ops += LogPdfOp.Image(logo, left, y, left + LOGO, y + LOGO, fit = true)
        var base = y + COMPANY
        wrapper.wrap(content.company, COMPANY, true, right - textLeft).forEachIndexed { i, line ->
            if (i > 0) base += COMPANY * LEADING
            ops += LogPdfOp.Text(textLeft, base, line, COMPANY, bold = true)
        }
        content.companyLine?.takeIf { it.isNotBlank() }?.let { line ->
            wrapper.wrap(line, SMALL, false, right - textLeft).forEach {
                base += SMALL * LEADING + 2f
                ops += LogPdfOp.Text(textLeft, base, it, SMALL, muted = true)
            }
        }
        y = maxOf(if (logo != null) y + LOGO else y, base + SMALL / 2) + 12f
        ops += LogPdfOp.Rule(left, right, y)
        y += 24f
        wrapper.wrap(content.title, TITLE, true, page.contentWidth).forEach {
            ops += LogPdfOp.Text(left, y, it, TITLE, bold = true)
            y += TITLE * LEADING
        }
        wrapper.wrap(content.job, BODY, true, page.contentWidth).forEach {
            ops += LogPdfOp.Text(left, y, it, BODY, bold = true)
            y += BODY * LEADING
        }
        (listOfNotNull(content.jobLine?.takeIf { it.isNotBlank() }) + content.facts).forEach { line ->
            wrapper.wrap(line, BODY, false, page.contentWidth).forEach {
                ops += LogPdfOp.Text(left, y, it, BODY, muted = true)
                y += BODY * LEADING
            }
        }
        return y - BODY
    }

    /** Pages after the first: "Job · title" in small type over a rule. */
    private fun runningHead(content: PhotoReportContent, ops: MutableList<LogPdfOp>): Float {
        val y = page.marginPt + SMALL
        ops += LogPdfOp.Text(left, y, fit("${content.job} · ${content.title}", SMALL, false, page.contentWidth), SMALL, muted = true)
        ops += LogPdfOp.Rule(left, right, y + 6f)
        return y + 6f
    }

    /** The caption wrapped to the cell and cut to [CAPTION_LINES], the last kept line ending in "…" if anything was cut. */
    internal fun caption(lines: List<String>, width: Float): List<String> {
        val wrapped = lines.filter { it.isNotBlank() }.flatMapIndexed { i, line -> wrapper.wrap(line, SMALL, i == 0, width).map { it to (i == 0) } }
        if (wrapped.size <= CAPTION_LINES) return wrapped.map { it.first }
        val kept = wrapped.take(CAPTION_LINES)
        val (last, bold) = kept.last()
        return kept.dropLast(1).map { it.first } + fit("$last$ELLIPSIS", SMALL, bold, width, force = true)
    }

    /** [text] cut from the end until it fits [width], ending in "…" when cut (or always, with [force]). */
    private fun fit(text: String, size: Float, bold: Boolean, width: Float, force: Boolean = false): String {
        if (measure.width(text, size, bold) <= width && (!force || text.endsWith(ELLIPSIS))) return text
        var body = text.removeSuffix(ELLIPSIS)
        while (body.isNotEmpty() && measure.width("$body$ELLIPSIS", size, bold) > width) body = body.dropLast(1)
        return "${body.trimEnd()}$ELLIPSIS"
    }

    companion object {
        const val PER_PAGE = 4
        const val CAPTION_LINES = 3
        const val GAP = 16f
        const val LOGO = LogPdfLayout.LOGO
        const val COMPANY = LogPdfLayout.COMPANY
        const val TITLE = LogPdfLayout.TITLE
        const val BODY = LogPdfLayout.BODY
        const val SMALL = LogPdfLayout.SMALL
        const val LEADING = LogPdfLayout.LEADING
        const val ELLIPSIS = "…"
    }
}
