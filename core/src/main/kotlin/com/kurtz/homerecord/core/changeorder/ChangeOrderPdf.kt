package com.kurtz.homerecord.core.changeorder

import com.kurtz.homerecord.core.log.LogPdfLayout
import com.kurtz.homerecord.core.log.LogPdfMeasure
import com.kurtz.homerecord.core.log.LogPdfOp
import com.kurtz.homerecord.core.log.LogPdfPage
import kotlin.math.floor

/**
 * What goes on a change order PDF (AC-F39), already in words: the app fills it from the saved order and its string
 * resources, so the layout stays free of wording and of Android. Money and quantities arrive formatted.
 */
data class ChangeOrderPdfContent(
    val company: String,
    /** Phone and email under the company name, or null. */
    val companyLine: String? = null,
    /** The company logo as a local file, or null. */
    val logoPath: String? = null,
    /** "Change order: Second outlet on the island". */
    val title: String,
    val job: String,
    /** Client and address under the job name, or null. */
    val jobLine: String? = null,
    /** Short lines under the job: client, date, status. */
    val facts: List<String> = emptyList(),
    val descriptionTitle: String = "",
    /** Null or blank leaves the section out. */
    val description: String? = null,
    val linesTitle: String = "",
    /** Column heads: item, qty, price each, amount. */
    val heads: ChangeOrderPdfRow = ChangeOrderPdfRow("", "", "", ""),
    val lines: List<ChangeOrderPdfRow> = emptyList(),
    val totalLabel: String = "",
    val total: String = "",
    val photosTitle: String = "",
    /** Local photo files in the order's order; a photo still on another phone is left out by the caller. */
    val photos: List<String> = emptyList(),
    val signatureTitle: String = "",
    /** The signature PNG, or null on an unsigned order (a blank line to sign on is drawn instead). */
    val signaturePath: String? = null,
    /** "Signed by Dana Hale, Nov 3, 2026, 2:15 PM" or "Not signed yet". */
    val signedLine: String = "",
)

/** One line item as printed. */
data class ChangeOrderPdfRow(val item: String, val qty: String, val price: String, val amount: String)

/**
 * Lays a change order out on pages (AC-F39): company header with logo, title, job and facts, the description, the line
 * items as a table (item wraps, numbers right aligned) with the total under it, photos three across, and the signature
 * with who signed and when. The signature block is kept whole; a table row never splits across pages; nothing is drawn
 * below [LogPdfPage.bottom] except the "Page n of m" footer.
 */
class ChangeOrderPdfLayout(
    private val measure: LogPdfMeasure,
    private val pageFooter: (page: Int, pages: Int) -> String,
    private val page: LogPdfPage = LogPdfPage(),
) {
    private val wrapper = LogPdfLayout(measure, pageFooter, page)
    private val left get() = page.marginPt
    private val right get() = page.widthPt - page.marginPt

    fun layout(content: ChangeOrderPdfContent): List<List<LogPdfOp>> {
        val pages = mutableListOf<MutableList<LogPdfOp>>(mutableListOf())
        var y = page.marginPt
        fun newPage() {
            pages += mutableListOf<LogPdfOp>()
            y = page.marginPt
        }
        fun ops() = pages.last()
        fun room(h: Float) = y + h <= page.bottom
        fun wrap(text: String, size: Float, bold: Boolean, width: Float) = wrapper.wrap(text, size, bold, width)
        fun rightText(text: String, colRight: Float, size: Float, bold: Boolean = false) =
            LogPdfOp.Text(colRight - measure.width(text, size, bold), y, text, size, bold)
        fun heading(text: String, after: Float) {
            if (!room(SECTION_GAP + HEADING * LEADING + 6f + after)) newPage() else y += SECTION_GAP
            y += HEADING
            ops() += LogPdfOp.Text(left, y, text, HEADING, bold = true)
            y += HEADING * (LEADING - 1) + 6f
        }

        // header: logo square on the left, company name and its line beside it
        val logo = content.logoPath
        val textLeft = if (logo != null) left + LOGO + 12f else left
        if (logo != null) ops() += LogPdfOp.Image(logo, left, y, left + LOGO, y + LOGO, fit = true)
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
        (listOfNotNull(content.jobLine) + content.facts).filter { it.isNotBlank() }.forEach { fact ->
            wrap(fact, BODY, false, page.contentWidth).forEach {
                ops() += LogPdfOp.Text(left, y, it, BODY, muted = true)
                y += BODY * LEADING
            }
        }

        content.description?.takeIf { it.isNotBlank() }?.let { text ->
            val lines = wrap(text, BODY, false, page.contentWidth)
            heading(content.descriptionTitle, BODY * LEADING)
            lines.forEach {
                if (!room(BODY * LEADING)) newPage()
                y += BODY
                ops() += LogPdfOp.Text(left, y, it, BODY)
                y += BODY * (LEADING - 1)
            }
        }

        // line items: item wraps in the wide column, the numbers sit right aligned on its first line
        val amountRight = right
        val priceRight = amountRight - AMOUNT_W - COL_GAP
        val qtyRight = priceRight - PRICE_W - COL_GAP
        val itemWidth = qtyRight - QTY_W - COL_GAP - left
        fun row(r: ChangeOrderPdfRow, bold: Boolean, muted: Boolean) {
            val itemLines = wrap(r.item, BODY, bold, itemWidth)
            if (!room(itemLines.size * BODY * LEADING)) newPage()
            itemLines.forEachIndexed { i, line ->
                y += BODY
                ops() += LogPdfOp.Text(left, y, line, BODY, bold, muted)
                if (i == 0) {
                    ops() += rightText(r.qty, qtyRight, BODY, bold).copy(muted = muted)
                    ops() += rightText(r.price, priceRight, BODY, bold).copy(muted = muted)
                    ops() += rightText(r.amount, amountRight, BODY, bold).copy(muted = muted)
                }
                y += BODY * (LEADING - 1)
            }
        }
        heading(content.linesTitle, BODY * LEADING * 2 + 4f)
        row(content.heads, bold = true, muted = true)
        y += 2f
        ops() += LogPdfOp.Rule(left, right, y)
        y += 4f
        content.lines.forEach { row(it, bold = false, muted = false) }
        if (!room(4f + TOTAL * LEADING)) newPage()
        y += 2f
        ops() += LogPdfOp.Rule(left, right, y)
        y += 4f + TOTAL
        ops() += LogPdfOp.Text(left, y, content.totalLabel, TOTAL, bold = true)
        ops() += rightText(content.total, amountRight, TOTAL, bold = true)
        y += TOTAL * (LEADING - 1)

        if (content.photos.isNotEmpty()) {
            val cell = (page.contentWidth - GRID_GAP * (COLUMNS - 1)) / COLUMNS
            val cellH = floor(cell * 3f / 4f)
            heading(content.photosTitle, cellH)
            content.photos.chunked(COLUMNS).forEach { r ->
                if (!room(cellH)) newPage()
                r.forEachIndexed { i, path ->
                    val x = left + i * (cell + GRID_GAP)
                    ops() += LogPdfOp.Image(path, x, y, x + cell, y + cellH)
                }
                y += cellH + GRID_GAP
            }
        }

        // signature: image (or a blank line to sign on), then who and when; kept on one page
        val signedLines = wrap(content.signedLine, BODY, false, page.contentWidth)
        heading(content.signatureTitle, SIGNATURE_H + 6f + signedLines.size * BODY * LEADING)
        val sig = content.signaturePath
        if (sig != null) {
            ops() += LogPdfOp.Image(sig, left, y, left + SIGNATURE_W, y + SIGNATURE_H, fit = true)
            y += SIGNATURE_H
            ops() += LogPdfOp.Rule(left, left + SIGNATURE_W, y)
        } else {
            y += SIGNATURE_H
            ops() += LogPdfOp.Text(left, y - 4f, SIGN_MARK, HEADING, bold = true)
            ops() += LogPdfOp.Rule(left + 14f, left + SIGNATURE_W, y)
        }
        y += 6f
        signedLines.forEach {
            y += BODY
            ops() += LogPdfOp.Text(left, y, it, BODY, muted = sig == null)
            y += BODY * (LEADING - 1)
        }

        val total = pages.size
        pages.forEachIndexed { i, ops ->
            ops += LogPdfOp.Text(left, page.heightPt - page.marginPt, pageFooter(i + 1, total), SMALL, muted = true)
        }
        return pages
    }

    companion object {
        const val LOGO = 56f
        const val COMPANY = 18f
        const val TITLE = 16f
        const val HEADING = 12.5f
        const val BODY = 10.5f
        const val TOTAL = 12.5f
        const val SMALL = 9f
        const val LEADING = 1.4f
        const val SECTION_GAP = 14f
        const val COLUMNS = 3
        const val GRID_GAP = 8f
        const val QTY_W = 48f
        const val PRICE_W = 84f
        const val AMOUNT_W = 84f
        const val COL_GAP = 12f
        const val SIGNATURE_W = 240f
        const val SIGNATURE_H = 96f
        const val SIGN_MARK = "X"
    }
}
