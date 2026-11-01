package com.kurtz.homerecord.core.log

import kotlin.math.floor

/**
 * What goes on a daily log PDF (AC-F33), already in words: the app fills it from the saved log and its string
 * resources, so the layout below stays free of wording and of Android.
 */
data class LogPdfContent(
    /** The company name, the PDF's top line beside the logo. */
    val company: String,
    /** Phone and email under the company name, or null when the company has neither. */
    val companyLine: String? = null,
    /** The company logo as a local file, or null to leave the space out. */
    val logoPath: String? = null,
    /** "Daily log, Friday, October 30, 2026". */
    val title: String,
    /** The job name. */
    val job: String,
    /** Client and address under the job name, or null. */
    val jobLine: String? = null,
    val sections: List<LogPdfSection> = emptyList(),
    val photosTitle: String = "",
    /** Local photo files, in the log's order; a photo still on another phone is left out by the caller. */
    val photos: List<String> = emptyList(),
)

/** One section: a title and its lines, each line one paragraph. */
data class LogPdfSection(val title: String, val lines: List<String>)

/** One drawing step on a page, in PDF points from the top left. */
sealed interface LogPdfOp {
    /** [y] is the text baseline. */
    data class Text(val x: Float, val y: Float, val text: String, val size: Float, val bold: Boolean = false, val muted: Boolean = false) : LogPdfOp
    data class Image(val path: String, val left: Float, val top: Float, val right: Float, val bottom: Float) : LogPdfOp
    data class Rule(val x1: Float, val x2: Float, val y: Float) : LogPdfOp
}

/** How wide a run of text draws; the app measures with the same Paint it draws with, tests with a fixed width. */
fun interface LogPdfMeasure {
    fun width(text: String, size: Float, bold: Boolean): Float
}

/** US Letter in points with half-inch-plus margins. */
data class LogPdfPage(val widthPt: Float = 612f, val heightPt: Float = 792f, val marginPt: Float = 48f, val footerPt: Float = 28f) {
    val contentWidth: Float get() = widthPt - 2 * marginPt
    /** Nothing but the footer is drawn below this line. */
    val bottom: Float get() = heightPt - marginPt - footerPt
}

/**
 * Lays a daily log out on pages (AC-F33): the company header with logo on page one, the title and job, then every
 * section with wrapped lines, then the photos in a three-across grid, and "Page n of m" at the foot of each page.
 * A section title never sits alone at the bottom of a page, a word longer than the line is broken, and nothing is
 * drawn below [LogPdfPage.bottom] except the footer.
 */
class LogPdfLayout(
    private val measure: LogPdfMeasure,
    private val pageFooter: (page: Int, pages: Int) -> String,
    private val page: LogPdfPage = LogPdfPage(),
) {
    private val left get() = page.marginPt
    private val right get() = page.widthPt - page.marginPt

    fun layout(content: LogPdfContent): List<List<LogPdfOp>> {
        val pages = mutableListOf<MutableList<LogPdfOp>>(mutableListOf())
        var y = page.marginPt
        fun newPage() {
            pages += mutableListOf<LogPdfOp>()
            y = page.marginPt
        }
        fun ops() = pages.last()
        fun room(h: Float) = y + h <= page.bottom

        // header: logo square on the left, company name and its line beside it
        val logo = content.logoPath
        val textLeft = if (logo != null) left + LOGO + 12f else left
        if (logo != null) ops() += LogPdfOp.Image(logo, left, y, left + LOGO, y + LOGO)
        var base = y + COMPANY
        wrap(content.company, COMPANY, true, right - textLeft).forEachIndexed { i, line ->
            if (i > 0) base += COMPANY * LEADING
            ops() += LogPdfOp.Text(textLeft, base, line, COMPANY, bold = true)
        }
        content.companyLine?.takeIf { it.isNotBlank() }?.let { line ->
            wrap(line, SMALL, false, right - textLeft).forEach {
                base += SMALL * LEADING + 2f
                ops() += LogPdfOp.Text(textLeft, base, it, SMALL, muted = true)
            }
        }
        // the descent of the last text line sits about a third of a size below its baseline
        y = maxOf(if (logo != null) y + LOGO else y, base + SMALL / 2) + 12f
        ops() += LogPdfOp.Rule(left, right, y)
        y += 24f
        wrap(content.title, TITLE, true, page.contentWidth).forEach {
            ops() += LogPdfOp.Text(left, y, it, TITLE, bold = true)
            y += TITLE * LEADING
        }
        wrap(content.job, BODY, true, page.contentWidth).forEach {
            ops() += LogPdfOp.Text(left, y, it, BODY, bold = true)
            y += BODY * LEADING
        }
        content.jobLine?.takeIf { it.isNotBlank() }?.let { line ->
            wrap(line, BODY, false, page.contentWidth).forEach {
                ops() += LogPdfOp.Text(left, y, it, BODY, muted = true)
                y += BODY * LEADING
            }
        }

        for (section in content.sections) {
            val lines = section.lines.map { wrap(it, BODY, false, page.contentWidth - BULLET_INDENT) }
            val head = SECTION_GAP + HEADING * LEADING + 6f
            val firstLine = lines.firstOrNull()?.firstOrNull()?.let { BODY * LEADING } ?: 0f
            // keep the title with its first line
            if (!room(head + firstLine)) newPage() else y += SECTION_GAP
            y += HEADING
            ops() += LogPdfOp.Text(left, y, section.title, HEADING, bold = true)
            y += HEADING * (LEADING - 1) + 6f
            for (paragraph in lines) {
                paragraph.forEachIndexed { i, line ->
                    if (!room(BODY * LEADING)) newPage()
                    y += BODY
                    if (i == 0) ops() += LogPdfOp.Text(left, y, BULLET, BODY)
                    ops() += LogPdfOp.Text(left + BULLET_INDENT, y, line, BODY)
                    y += BODY * (LEADING - 1)
                }
                y += 3f
            }
        }

        if (content.photos.isNotEmpty()) {
            val cell = (page.contentWidth - GRID_GAP * (COLUMNS - 1)) / COLUMNS
            val cellH = floor(cell * 3f / 4f)
            if (!room(SECTION_GAP + HEADING * LEADING + 6f + cellH)) newPage() else y += SECTION_GAP
            y += HEADING
            ops() += LogPdfOp.Text(left, y, content.photosTitle, HEADING, bold = true)
            y += HEADING * (LEADING - 1) + 6f
            content.photos.chunked(COLUMNS).forEach { row ->
                if (!room(cellH)) newPage()
                row.forEachIndexed { i, path ->
                    val x = left + i * (cell + GRID_GAP)
                    ops() += LogPdfOp.Image(path, x, y, x + cell, y + cellH)
                }
                y += cellH + GRID_GAP
            }
        }

        val total = pages.size
        pages.forEachIndexed { i, ops ->
            ops += LogPdfOp.Text(left, page.heightPt - page.marginPt, pageFooter(i + 1, total), SMALL, muted = true)
        }
        return pages
    }

    /** Breaks [text] into lines no wider than [width]; explicit line breaks are kept and a too-long word is split. */
    internal fun wrap(text: String, size: Float, bold: Boolean, width: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            var line = ""
            for (word in para.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure.width(candidate, size, bold) <= width) {
                    line = candidate
                    continue
                }
                if (line.isNotEmpty()) out += line
                line = ""
                var rest = word
                while (measure.width(rest, size, bold) > width) {
                    var cut = rest.length - 1
                    while (cut > 1 && measure.width(rest.substring(0, cut), size, bold) > width) cut--
                    out += rest.substring(0, cut)
                    rest = rest.substring(cut)
                }
                line = rest
            }
            if (line.isNotEmpty() || para.isBlank()) out += line
        }
        return out.ifEmpty { listOf("") }
    }

    companion object {
        const val LOGO = 56f
        const val COMPANY = 18f
        const val TITLE = 16f
        const val HEADING = 12.5f
        const val BODY = 10.5f
        const val SMALL = 9f
        const val LEADING = 1.4f
        const val SECTION_GAP = 14f
        const val BULLET_INDENT = 12f
        const val BULLET = "•"
        const val COLUMNS = 3
        const val GRID_GAP = 8f
    }
}

/**
 * Who a daily log goes to by email (AC-F33): the client's address first, then the office addresses on the job; blanks,
 * malformed addresses and repeats (any case) are left out.
 */
object LogRecipients {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

    fun of(clientEmail: String?, officeEmails: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return (listOfNotNull(clientEmail) + officeEmails).map { it.trim() }
            .filter { it.length <= 254 && EMAIL.matches(it) && seen.add(it.lowercase()) }
    }
}
