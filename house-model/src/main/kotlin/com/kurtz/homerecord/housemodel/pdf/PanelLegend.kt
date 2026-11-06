package com.kurtz.homerecord.housemodel.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.SlotOccupancy
import com.kurtz.homerecord.housemodel.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/** One row of the legend: a slot and what fills it ([text] blank for an open slot). */
data class LegendRow(val slot: Int, val text: String, val open: Boolean)

/** The words the legend uses, from string resources. */
data class LegendWords(
    val open: String,
    val amps: (Int) -> String,
    val withSlot: (Int) -> String,
    val feeds: (String) -> String,
    val service: (Int) -> String,
    val slots: (Int) -> String,
)

/**
 * The panel legend (AC-C43): one row per slot in slot order. A breaker's row says its label, amps and what it feeds; a
 * two-pole breaker's second slot points back at its first ("with 1"); a tandem slot, or a slot two breakers
 * clash over, lists both.
 */
object PanelLegend {
    fun rows(panel: Panel, breakers: List<Breaker>, feeds: Map<String, List<String>>, words: LegendWords): List<LegendRow> {
        // Every breaker prints, even one whose slots clash with another (two phones edited the panel apart): the
        // legend on the door must not hide a breaker the screen still shows under "Not placed".
        return (1..panel.slotCount).map { slot ->
            val here = breakers.filter { slot in it.slots }.sortedBy { it.createdAt }
            when {
                here.isEmpty() -> LegendRow(slot, words.open, open = true)
                here.size == 1 && here[0].number != slot -> LegendRow(slot, "${describe(here[0], feeds, words)} (${words.withSlot(here[0].number)})", open = false)
                else -> LegendRow(slot, here.joinToString(" / ") { describe(it, feeds, words) }, open = false)
            }
        }
    }

    private fun describe(b: Breaker, feeds: Map<String, List<String>>, words: LegendWords): String = buildString {
        append(b.label.ifBlank { "—" })
        b.amps?.let { append(" · ").append(words.amps(it)) }
        feeds[b.id]?.takeIf { it.isNotEmpty() }?.let { append(" · ").append(words.feeds(it.joinToString(", "))) }
    }

    /** Columns on the page: a two-column panel prints odd slots left and even right, as on the door. */
    fun columns(layout: PanelLayout): Int = SlotOccupancy.columns(layout)
}

/** Draws the legend page: panel name, service size, brand and slot count, then the slot rows (AC-C43). */
class PanelLegendPdfWriter(private val words: LegendWords, private val spec: PdfPageSpec = PdfPageSpec()) {
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = BLACK; isFakeBoldText = true }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = GRAY }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = GRAY }

    fun write(name: String, panel: Panel, rows: List<LegendRow>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(spec.widthPt, spec.heightPt, 1).create())
            drawPage(page.canvas, name, panel, rows)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** The header line under the name: "200 A service · Square D QO · 40 slots". */
    fun headerLine(panel: Panel): String =
        listOfNotNull(panel.mainAmps?.let(words.service), panel.brand?.ifBlank { null }, words.slots(panel.slotCount)).joinToString(" · ")

    internal fun drawPage(c: Canvas, name: String, panel: Panel, rows: List<LegendRow>) =
        drawPage(c, name, headerLine(panel), PanelLegend.columns(panel.layout), rows)

    /** Draws one legend page from its parts; the home report reuses it for each panel (AC-H13). */
    fun drawPage(c: Canvas, name: String, header: String, columns: Int, rows: List<LegendRow>) {
        val m = spec.marginPt
        c.drawText(name, m, m + 20f, title)
        c.drawText(header, m, m + 40f, sub)
        val perColumn = (rows.size + columns - 1) / columns
        val top = m + spec.headerPt
        val rowH = ((spec.heightPt - top - m) / perColumn.coerceAtLeast(1)).coerceAtMost(18f)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = (rowH * 0.6f).coerceIn(5f, 10f); color = BLACK }
        val colW = (spec.widthPt - 2 * m) / columns
        rows.forEachIndexed { i, row ->
            val col = if (columns == 2) (row.slot + 1) % 2 else 0
            val r = if (columns == 2) (row.slot - 1) / 2 else i
            val x = m + col * colW
            val y = top + r * rowH
            c.drawRect(x, y, x + colW - 4f, y + rowH, line)
            text.color = if (row.open) GRAY else BLACK
            val number = row.slot.toString()
            c.drawText(number, x + 4f, y + rowH * 0.7f, text)
            val avail = colW - 36f
            var label = row.text
            while (label.length > 1 && text.measureText(label) > avail) label = label.dropLast(2) + "…"
            c.drawText(label, x + 28f, y + rowH * 0.7f, text)
        }
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val GRAY = 0xFF555555.toInt()
    }
}

/** Makes the legend PDF and returns a shareable URI, or null when it could not be written (AC-C43). */
fun interface LegendExporter {
    suspend fun export(name: String, panel: Panel, breakers: List<Breaker>, feeds: Map<String, List<String>>): String?
}

/** Writes the legend into cache/exports and hands back a FileProvider URI for the share sheet (AC-C43). */
class PanelLegendExporter(private val context: Context) : LegendExporter {
    val words = LegendWords(
        open = context.getString(R.string.legend_open),
        amps = { context.getString(R.string.legend_amps, it) },
        withSlot = { context.getString(R.string.legend_with_slot, it) },
        feeds = { context.getString(R.string.legend_feeds, it) },
        service = { context.getString(R.string.legend_service, it) },
        slots = { context.getString(R.string.legend_slots, it) },
    )

    override suspend fun export(name: String, panel: Panel, breakers: List<Breaker>, feeds: Map<String, List<String>>): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safe = name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "panel" }
            val file = File(dir, "$safe-legend.pdf")
            val rows = PanelLegend.rows(panel, breakers, feeds, words)
            file.outputStream().use { PanelLegendPdfWriter(words).write(name, panel, rows, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
        } catch (e: java.io.IOException) {
            null
        }
    }
}
