package com.kurtz.homerecord.core.pdf

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.kurtz.homerecord.core.logic.LegendPage
import com.kurtz.homerecord.core.logic.LegendRow
import java.io.File

/** Column headings and title, supplied by the caller from strings.xml. */
data class LegendHeadings(
    val title: String,
    val slot: String,
    val label: String,
    val amps: String,
    val poles: String,
    val type: String,
    val rooms: String,
)

/** AC-22 / AC-98: renders one Letter portrait page per panel; row height shrinks so any slot count fits. */
object LegendPdfWriter {
    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 36f
    private const val MAX_ROW_HEIGHT = 18f

    fun write(file: File, headings: LegendHeadings, pages: List<LegendPage>) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, content ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                val title = listOfNotNull(headings.title, content.panelName).joinToString(" — ")
                draw(page.canvas, headings, title, content.rows, content.subtitle)
                doc.finishPage(page)
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun draw(canvas: android.graphics.Canvas, h: LegendHeadings, titleText: String, rows: List<LegendRow>, subtitle: String?) {
        val title = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText(titleText, MARGIN, MARGIN + 14f, title)

        if (!subtitle.isNullOrBlank()) {
            val sub = Paint().apply { textSize = 11f }
            canvas.drawText(clip(subtitle, sub, PAGE_WIDTH - 2 * MARGIN), MARGIN, MARGIN + 30f, sub)
        }
        val top = MARGIN + if (subtitle.isNullOrBlank()) 30f else 40f
        val available = PAGE_HEIGHT - MARGIN - top
        val rowHeight = minOf(MAX_ROW_HEIGHT, available / (rows.size + 1).coerceAtLeast(1))
        val text = Paint().apply { textSize = (rowHeight * 0.62f).coerceAtMost(11f) }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val line = Paint().apply { strokeWidth = 0.5f }

        val width = PAGE_WIDTH - 2 * MARGIN
        val columns = floatArrayOf(0f, 0.12f, 0.42f, 0.50f, 0.58f, 0.70f).map { MARGIN + it * width }
        val widths = floatArrayOf(0.12f, 0.30f, 0.08f, 0.08f, 0.12f, 0.30f).map { it * width - 4f }

        fun drawRow(y: Float, cells: List<String>, paint: Paint) {
            cells.forEachIndexed { i, cell -> canvas.drawText(clip(cell, paint, widths[i]), columns[i], y, paint) }
            canvas.drawLine(MARGIN, y + rowHeight * 0.25f, PAGE_WIDTH - MARGIN, y + rowHeight * 0.25f, line)
        }

        var y = top + rowHeight * 0.75f
        drawRow(y, listOf(h.slot, h.label, h.amps, h.poles, h.type, h.rooms), bold)
        rows.forEach { row ->
            y += rowHeight
            drawRow(y, listOf(row.slot, row.label, row.amps, row.poles, row.type, row.rooms), text)
        }
    }

    private fun clip(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        val fit = paint.breakText(value, true, maxWidth - paint.measureText("…"), null)
        return value.take(fit) + "…"
    }
}
