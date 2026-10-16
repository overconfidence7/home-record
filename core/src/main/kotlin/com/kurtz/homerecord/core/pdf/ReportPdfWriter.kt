package com.kurtz.homerecord.core.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.kurtz.homerecord.core.logic.ReportHeader
import com.kurtz.homerecord.core.logic.ReportSection
import com.kurtz.homerecord.core.logic.ReportSectionBuilder
import java.io.File

/** AC-69: Letter portrait report: header, sections, then photos two per row. Overwrites [file]. */
object ReportPdfWriter {
    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 36f
    private const val GAP = 12f
    private const val PHOTO_HEIGHT = 170f

    fun write(file: File, header: ReportHeader, sections: List<ReportSection>, photoFiles: List<File>) {
        val doc = PdfDocument()
        try {
            val pager = Pager(doc)
            drawHeader(pager, header)
            sections.forEach { drawSection(pager, it) }
            drawPhotos(pager, photoFiles)
            pager.finish()
            file.parentFile?.mkdirs()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private class Pager(private val doc: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        var y = MARGIN
        val canvas: Canvas get() = (page ?: startPage()).canvas

        private fun startPage(): PdfDocument.Page {
            number++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
            page = p
            y = MARGIN
            return p
        }

        fun ensure(height: Float) {
            if (page == null || y + height > PAGE_HEIGHT - MARGIN) {
                finish()
                startPage()
            }
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }
    }

    private fun drawHeader(pager: Pager, h: ReportHeader) {
        val title = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
        val sub = Paint().apply { textSize = 12f }
        pager.ensure(90f)
        if (h.company.isNotBlank()) {
            pager.canvas.drawText(h.company, MARGIN, pager.y + 20f, title)
            pager.y += 26f
        }
        listOf(h.job, h.address, h.date).filter { it.isNotBlank() }.forEach {
            pager.canvas.drawText(it, MARGIN, pager.y + 14f, sub)
            pager.y += 16f
        }
        pager.canvas.drawLine(MARGIN, pager.y + 6f, PAGE_WIDTH - MARGIN, pager.y + 6f, Paint().apply { strokeWidth = 1f })
        pager.y += 20f
    }

    private fun drawSection(pager: Pager, s: ReportSection) {
        val labelPaint = TextPaint().apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        val bodyPaint = TextPaint().apply { textSize = 12f }
        val width = (PAGE_WIDTH - 2 * MARGIN).toInt()
        val layout = StaticLayout.Builder.obtain(s.text, 0, s.text.length, bodyPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        pager.ensure(18f + minOf(layout.height.toFloat(), 60f))
        pager.canvas.drawText(s.label, MARGIN, pager.y + 12f, labelPaint)
        pager.y += 16f
        // Long text continues on the next page, line by line.
        var line = 0
        while (line < layout.lineCount) {
            val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
            pager.ensure(lineHeight.toFloat())
            val c = pager.canvas
            c.save()
            c.translate(MARGIN, pager.y - layout.getLineTop(line))
            c.clipRect(0f, layout.getLineTop(line).toFloat(), width.toFloat(), layout.getLineBottom(line).toFloat())
            layout.draw(c)
            c.restore()
            pager.y += lineHeight
            line++
        }
        pager.y += 10f
    }

    private fun drawPhotos(pager: Pager, files: List<File>) {
        val cellWidth = (PAGE_WIDTH - 2 * MARGIN - GAP) / ReportSectionBuilder.PHOTOS_PER_ROW
        files.take(ReportSectionBuilder.MAX_PHOTOS).chunked(ReportSectionBuilder.PHOTOS_PER_ROW).forEach { row ->
            pager.ensure(PHOTO_HEIGHT + GAP)
            row.forEachIndexed { i, f ->
                val x = MARGIN + i * (cellWidth + GAP)
                decode(f, cellWidth.toInt() * 2, PHOTO_HEIGHT.toInt() * 2)?.let { bmp ->
                    val scale = minOf(cellWidth / bmp.width, PHOTO_HEIGHT / bmp.height)
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    val dst = Rect(x.toInt(), pager.y.toInt(), (x + w).toInt(), (pager.y + h).toInt())
                    pager.canvas.drawBitmap(bmp, null, dst, null)
                    bmp.recycle()
                }
            }
            pager.y += PHOTO_HEIGHT + GAP
        }
    }

    /** Down-sampled decode; null for a missing or unreadable file. */
    private fun decode(file: File, maxW: Int, maxH: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
