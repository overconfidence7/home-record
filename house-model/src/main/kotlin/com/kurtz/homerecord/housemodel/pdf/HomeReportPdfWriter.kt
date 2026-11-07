package com.kurtz.homerecord.housemodel.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.kurtz.homerecord.housemodel.R
import java.io.OutputStream

/** Where the report's pages go: a PDF on the phone, a bitmap canvas in JVM tests (PdfDocument has no JVM backing). */
interface ReportPages {
    fun start(): Canvas
    fun finish()
}

/**
 * Writes the home report (AC-H13): a cover with the name, address, house type, date and counts; one blueprint page per
 * floor; every room with its items (make, model, serial, in-service date, warranty status), flowing over as many pages
 * as it needs; the shutoffs with instructions and photo; and a legend page per breaker panel. Every page carries its
 * number at the bottom.
 */
class HomeReportPdfWriter(
    private val words: ReportWords,
    private val legendWords: LegendWords,
    private val spec: PdfPageSpec = PdfPageSpec(),
    private val loadPhoto: (path: String, maxWidthPx: Int) -> Bitmap? = ::decodeSampled,
) {
    private val title = text(26f, BLACK, bold = true)
    private val heading = text(18f, BLACK, bold = true)
    private val roomName = text(13f, BLACK, bold = true)
    private val body = text(11f, BLACK)
    private val small = text(9f, GRAY)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = GRAY }

    fun write(data: HomeReportData, madeOn: String, out: OutputStream) {
        val doc = PdfDocument()
        try {
            var number = 0
            var current: PdfDocument.Page? = null
            draw(data, madeOn, object : ReportPages {
                override fun start(): Canvas {
                    number++
                    return doc.startPage(PdfDocument.PageInfo.Builder(spec.widthPt, spec.heightPt, number).create()).also { current = it }.canvas
                }
                override fun finish() {
                    current?.let(doc::finishPage)
                    current = null
                }
            })
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Draws every page into [pages] and returns how many there were. */
    fun draw(data: HomeReportData, madeOn: String, pages: ReportPages): Int {
        val flow = Flow(pages)
        cover(flow.newPage(), data, madeOn)
        val blueprint = BlueprintPdfWriter(words.scaleFeet, spec)
        for (floor in data.floors) blueprint.drawPage(flow.newPage(), data.homeName, floor)
        rooms(flow, data.rooms)
        shutoffs(flow, data.shutoffs)
        val legend = PanelLegendPdfWriter(legendWords, spec)
        for (p in data.panels) legend.drawPage(flow.newPage(), p.name, p.header, p.columns, p.rows)
        flow.close()
        return flow.count
    }

    private fun cover(c: Canvas, data: HomeReportData, madeOn: String) {
        val m = spec.marginPt
        var y = m + 120f
        c.drawText(words.title, m, y, small.withSize(14f)); y += 40f
        for (line in wrap(data.homeName, title, width())) { c.drawText(line, m, y, title); y += 32f }
        data.address?.ifBlank { null }?.let { for (line in wrap(it, body.withSize(14f), width())) { c.drawText(line, m, y, body.withSize(14f)); y += 20f } }
        if (data.houseType.isNotBlank()) { c.drawText(data.houseType, m, y, body.withSize(14f)); y += 20f }
        y += 24f
        c.drawText(words.madeOn(madeOn), m, y, small.withSize(11f)); y += 16f
        c.drawText(words.counts(data.rooms.count { it.floorName.isNotEmpty() }, data.rooms.sumOf { it.items.size }), m, y, small.withSize(11f))
    }

    private fun rooms(flow: Flow, rooms: List<ReportRoom>) {
        flow.heading(words.roomsAndItems)
        for (room in rooms) {
            val roomLines = 1 + if (room.items.isEmpty()) 1 else 0
            flow.need(roomLines * 18f + (room.items.firstOrNull()?.let { itemHeight(it) } ?: 0f))
            flow.line(listOfNotNull(room.name, room.floorName.ifBlank { null }).joinToString(words.separator), roomName, 20f)
            if (room.items.isEmpty()) flow.line(words.noItems, small, 16f)
            for (item in room.items) {
                flow.need(itemHeight(item))
                flow.line(item.name, body, 14f, indent = 12f)
                HomeReport.detailLine(item, words).takeIf { it.isNotEmpty() }?.let { d -> wrap(d, small, width() - 12f).forEach { flow.line(it, small, 12f, indent = 12f) } }
                flow.line(HomeReport.warrantyText(item.warranty, words), small, 16f, indent = 12f)
            }
            flow.gap(6f)
        }
    }

    private fun itemHeight(item: ReportItem) = 14f + 12f * wrap(HomeReport.detailLine(item, words), small, width() - 12f).size + 16f

    private fun shutoffs(flow: Flow, shutoffs: List<ReportShutoff>) {
        flow.heading(words.shutoffs)
        if (shutoffs.isEmpty()) flow.line(words.noShutoffs, small, 16f)
        for (s in shutoffs) {
            val lines = wrap(s.instructions, body, width())
            val photo = s.photoPath?.let { loadPhoto(it, PHOTO_MAX_PX) }
            val photoH = photo?.let { minOf(PHOTO_MAX_PT, width() * it.height / it.width.coerceAtLeast(1)) } ?: 0f
            flow.need(20f + lines.size * 14f + photoH + 12f)
            flow.line(listOfNotNull(s.name, s.roomName.ifBlank { null }).joinToString(words.separator), roomName, 20f)
            lines.forEach { flow.line(it, body, 14f) }
            if (photo != null) {
                val w = photoH * photo.width / photo.height.coerceAtLeast(1)
                flow.canvas.drawBitmap(photo, null, RectF(spec.marginPt, flow.y + 4f, spec.marginPt + w, flow.y + 4f + photoH), null)
                flow.y += photoH + 8f
                photo.recycle()
            }
            flow.gap(10f)
        }
    }

    private fun width() = spec.widthPt - 2 * spec.marginPt

    /** A run of pages with a cursor: [need] starts a new page when the next block would cross the footer. */
    private inner class Flow(private val pages: ReportPages) {
        var count = 0
        private var open: Canvas? = null
        var y = 0f
        val canvas: Canvas get() = open ?: newPage()
        private val bottom get() = spec.heightPt - spec.marginPt - FOOTER_PT

        fun newPage(): Canvas {
            close()
            count++
            val c = pages.start()
            open = c
            y = spec.marginPt
            return c
        }

        fun close() {
            val c = open ?: return
            c.drawText(words.page(count), spec.widthPt - spec.marginPt - small.measureText(words.page(count)), spec.heightPt - spec.marginPt, small)
            pages.finish()
            open = null
        }

        fun heading(text: String) {
            newPage()
            y += 22f
            canvas.drawText(text, spec.marginPt, y, heading)
            y += 8f
            canvas.drawLine(spec.marginPt, y, spec.widthPt - spec.marginPt, y, rule)
            y += 12f
        }

        fun need(height: Float) {
            if (open == null || y + height > bottom) newPage()
        }

        fun line(text: String, paint: Paint, height: Float, indent: Float = 0f) {
            need(height)
            y += height
            canvas.drawText(ellipsize(text, paint, width() - indent), spec.marginPt + indent, y - height * 0.25f, paint)
        }

        fun gap(h: Float) { y += h }
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val GRAY = 0xFF555555.toInt()
        const val FOOTER_PT = 20f
        const val PHOTO_MAX_PT = 220f
        const val PHOTO_MAX_PX = 1200

        fun text(size: Float, color: Int, bold: Boolean = false) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; isFakeBoldText = bold }

        fun Paint.withSize(size: Float) = Paint(this).apply { textSize = size }
    }
}

/** Splits [text] into lines no wider than [width] at word breaks; a word wider than a line is cut by [ellipsize]. */
internal fun wrap(text: String, paint: Paint, width: Float): List<String> {
    if (text.isBlank()) return emptyList()
    val out = mutableListOf<String>()
    for (para in text.trim().split('\n')) {
        var line = ""
        for (word in para.split(' ').filter { it.isNotEmpty() }) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(next) > width) { out += line; line = word } else line = next
        }
        if (line.isNotEmpty()) out += line
    }
    return out
}

internal fun ellipsize(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var t = text
    while (t.length > 1 && paint.measureText("$t…") > width) t = t.dropLast(1)
    return "$t…"
}

/** Decodes a photo no wider than about [maxWidthPx], or null when it cannot be read. */
internal fun decodeSampled(path: String, maxWidthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidthPx) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** "May 3, 2021" for an ISO date; anything unreadable prints as stored. */
internal fun longDate(iso: String): String = try {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
} catch (_: java.time.format.DateTimeParseException) {
    iso
}

/** The report's words from string resources (AC-H13); dates print long ("May 3, 2021"). */
fun reportWords(context: android.content.Context): ReportWords {
    val r = context.resources
    return ReportWords(
        title = r.getString(R.string.report_title),
        madeOn = { r.getString(R.string.report_made_on, longDate(it)) },
        counts = { rooms, items -> r.getString(R.string.report_counts, r.getQuantityString(R.plurals.report_count_rooms, rooms, rooms), r.getQuantityString(R.plurals.report_count_items, items, items)) },
        roomsAndItems = r.getString(R.string.report_rooms),
        noItems = r.getString(R.string.report_no_items),
        shutoffs = r.getString(R.string.report_shutoffs),
        noShutoffs = r.getString(R.string.report_no_shutoffs),
        separator = r.getString(R.string.report_separator),
        serial = { r.getString(R.string.report_serial, it) },
        inService = { r.getString(R.string.report_in_service, longDate(it)) },
        warrantyUnknown = r.getString(R.string.report_warranty_unknown),
        warrantyActive = { r.getString(R.string.report_warranty_active, longDate(it)) },
        warrantyExpiring = { r.getString(R.string.report_warranty_expiring, longDate(it)) },
        warrantyExpired = { r.getString(R.string.report_warranty_expired, longDate(it)) },
        scaleFeet = { r.getString(R.string.blueprint_pdf_scale_feet, it) },
        page = { r.getString(R.string.report_page, it) },
    )
}
