package com.kurtz.homerecord.core.log

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/** A job photo as the photo report sees it (AC-F28): [path] is the local file, blank when it is still on another phone. */
data class ReportPhoto(val id: String, val jobId: String, val takenAt: Long, val path: String)

/** Which of a job's photos go in its photo report (AC-F28). */
object PhotoReportPicker {
    /** The job's photos taken from [from] to [to] (both whole days, in [zone]), oldest first; ties keep their id order. */
    fun inRange(photos: List<ReportPhoto>, jobId: String, from: LocalDate, to: LocalDate, zone: ZoneId): List<ReportPhoto> =
        photos.filter { it.jobId == jobId && Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() in from..to }
            .sortedWith(compareBy<ReportPhoto>({ it.takenAt }, { it.id }))

    /** How many photos each job has from [from] to [to], for the Reports list; jobs with none are left out. */
    fun countsByJob(photos: List<ReportPhoto>, from: LocalDate, to: LocalDate, zone: ZoneId): Map<String, Int> =
        photos.filter { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() in from..to }
            .groupingBy { it.jobId }.eachCount()
}

/** What goes on a photo report PDF, already in words; [note] is a line under the job (photos left out), or null. */
data class PhotoReportContent(
    val company: String,
    val companyLine: String? = null,
    val logoPath: String? = null,
    /** "Photo report, Oct 19 – Nov 1, 2026". */
    val title: String,
    val job: String,
    val jobLine: String? = null,
    val note: String? = null,
    val photos: List<PhotoReportEntry> = emptyList(),
)

/** One photo and its caption lines (date and time, room · phase, the note), each line one paragraph. */
data class PhotoReportEntry(val path: String, val caption: List<String>)

/**
 * Lays a photo report out (AC-F28): the company header, title and job on page one, then the photos two across and two
 * down, so never more than four to a page, each with its caption under it (at most [CAPTION_LINES] lines, the last
 * ending in "…" when cut). Pages after the first carry the job name at the top; every page has "Page n of m". The
 * photo cells shrink on page one if the header is tall, so four still fit above the footer.
 */
class PhotoReportLayout(
    private val measure: LogPdfMeasure,
    private val pageFooter: (page: Int, pages: Int) -> String,
    private val page: LogPdfPage = LogPdfPage(),
) {
    private val text = LogPdfLayout(measure, pageFooter, page)
    private val left get() = page.marginPt
    private val right get() = page.widthPt - page.marginPt
    private val cellW get() = (page.contentWidth - GAP_X) / 2
    private val captionH get() = CAPTION_PAD + CAPTION_LINES * CAPTION * LogPdfLayout.LEADING

    fun layout(content: PhotoReportContent): List<List<LogPdfOp>> {
        val pages = mutableListOf<MutableList<LogPdfOp>>()
        val chunks = content.photos.chunked(PER_PAGE).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { index, chunk ->
            val ops = mutableListOf<LogPdfOp>()
            val top = if (index == 0) header(content, ops) else running(content.job, ops)
            val avail = page.bottom - top
            val photoH = minOf(floor(cellW * 3f / 4f), floor((avail - 2 * captionH - GAP_Y) / 2))
            chunk.chunked(2).forEachIndexed { row, pair ->
                val y = top + row * (photoH + captionH + GAP_Y)
                pair.forEachIndexed { col, entry ->
                    val x = left + col * (cellW + GAP_X)
                    ops += LogPdfOp.Image(entry.path, x, y, x + cellW, y + photoH)
                    var base = y + photoH + CAPTION_PAD
                    caption(entry.caption).forEachIndexed { i, line ->
                        base += CAPTION
                        ops += LogPdfOp.Text(x, base, line, CAPTION, bold = i == 0, muted = i > 0)
                        base += CAPTION * (LogPdfLayout.LEADING - 1)
                    }
                }
            }
            pages += ops
        }
        val total = pages.size
        pages.forEachIndexed { i, ops ->
            ops += LogPdfOp.Text(left, page.heightPt - page.marginPt, pageFooter(i + 1, total), LogPdfLayout.SMALL, muted = true)
        }
        return pages
    }

    /** The caption wrapped to the cell, at most [CAPTION_LINES] lines; a cut last line ends in "…". */
    internal fun caption(lines: List<String>): List<String> {
        val wrapped = lines.filter { it.isNotBlank() }.flatMap { text.wrap(it.trim(), CAPTION, false, cellW) }
        if (wrapped.size <= CAPTION_LINES) return wrapped
        val kept = wrapped.take(CAPTION_LINES).toMutableList()
        var last = kept.last()
        while (last.isNotEmpty() && measure.width("$last…", CAPTION, false) > cellW) last = last.dropLast(1)
        kept[kept.lastIndex] = last.trimEnd() + "…"
        return kept
    }

    /** Page one's header; returns where the photos start. */
    private fun header(c: PhotoReportContent, ops: MutableList<LogPdfOp>): Float {
        var y = page.marginPt
        val logo = c.logoPath
        val textLeft = if (logo != null) left + LogPdfLayout.LOGO + 12f else left
        if (logo != null) ops += LogPdfOp.Image(logo, left, y, left + LogPdfLayout.LOGO, y + LogPdfLayout.LOGO, fit = true)
        var base = y + LogPdfLayout.COMPANY
        text.wrap(c.company, LogPdfLayout.COMPANY, true, right - textLeft).forEachIndexed { i, line ->
            if (i > 0) base += LogPdfLayout.COMPANY * LogPdfLayout.LEADING
            ops += LogPdfOp.Text(textLeft, base, line, LogPdfLayout.COMPANY, bold = true)
        }
        c.companyLine?.takeIf { it.isNotBlank() }?.let { line ->
            text.wrap(line, LogPdfLayout.SMALL, false, right - textLeft).forEach {
                base += LogPdfLayout.SMALL * LogPdfLayout.LEADING + 2f
                ops += LogPdfOp.Text(textLeft, base, it, LogPdfLayout.SMALL, muted = true)
            }
        }
        y = maxOf(if (logo != null) y + LogPdfLayout.LOGO else y, base + LogPdfLayout.SMALL / 2) + 12f
        ops += LogPdfOp.Rule(left, right, y)
        y += 24f
        fun lines(s: String, size: Float, bold: Boolean, muted: Boolean) = text.wrap(s, size, bold, page.contentWidth).forEach {
            ops += LogPdfOp.Text(left, y, it, size, bold = bold, muted = muted)
            y += size * LogPdfLayout.LEADING
        }
        lines(c.title, LogPdfLayout.TITLE, bold = true, muted = false)
        lines(c.job, LogPdfLayout.BODY, bold = true, muted = false)
        c.jobLine?.takeIf { it.isNotBlank() }?.let { lines(it, LogPdfLayout.BODY, bold = false, muted = true) }
        c.note?.takeIf { it.isNotBlank() }?.let { lines(it, LogPdfLayout.BODY, bold = false, muted = true) }
        return y + 8f
    }

    /** The job name at the top of a later page; returns where the photos start. */
    private fun running(job: String, ops: MutableList<LogPdfOp>): Float {
        val line = text.wrap(job, LogPdfLayout.SMALL, true, page.contentWidth).first()
        ops += LogPdfOp.Text(left, page.marginPt + LogPdfLayout.SMALL, line, LogPdfLayout.SMALL, bold = true, muted = true)
        val y = page.marginPt + LogPdfLayout.SMALL * LogPdfLayout.LEADING + 6f
        ops += LogPdfOp.Rule(left, right, y)
        return y + 12f
    }

    companion object {
        const val PER_PAGE = 4
        const val CAPTION = 9.5f
        const val CAPTION_LINES = 3
        const val CAPTION_PAD = 6f
        const val GAP_X = 12f
        const val GAP_Y = 16f
    }
}
