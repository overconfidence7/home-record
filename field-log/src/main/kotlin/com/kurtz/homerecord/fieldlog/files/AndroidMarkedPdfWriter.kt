package com.kurtz.homerecord.fieldlog.files

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.kurtz.homerecord.core.photo.MarkedPdfWriter
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.core.photo.PdfMark
import com.kurtz.homerecord.core.photo.PdfMarkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Writes a marked-up copy of a PDF (AC-F21): each page of the source is rendered with PdfRenderer, drawn into a new
 * PdfDocument page of the same size in points, and the page's marks are drawn over it as vectors. The page itself
 * becomes an image (the platform has no way to copy PDF content), rendered at up to [RENDER_SCALE]× and at most
 * [MAX_PAGE_PIXELS] so a 24×36 plan sheet fits in memory. The source file is only read.
 */
object AndroidMarkedPdfWriter : MarkedPdfWriter {
    private const val RENDER_SCALE = 2f
    private const val MAX_PAGE_PIXELS = 12_000_000f

    override suspend fun write(sourcePath: String, marks: List<PdfMark>, destPath: String): Boolean = withContext(Dispatchers.IO) {
        val dest = File(destPath)
        val ok = runCatching {
            ParcelFileDescriptor.open(File(sourcePath), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val doc = PdfDocument()
                    try {
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { source ->
                                val w = source.width
                                val h = source.height
                                val scale = minOf(RENDER_SCALE, sqrt(MAX_PAGE_PIXELS / (w.toFloat() * h)))
                                val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                source.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, index + 1).create())
                                page.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                                bmp.recycle()
                                draw(page.canvas, w.toFloat(), h.toFloat(), marks.filter { it.page == index })
                                doc.finishPage(page)
                            }
                        }
                        dest.outputStream().use(doc::writeTo)
                    } finally {
                        doc.close()
                    }
                }
            }
            true
        }.getOrDefault(false)
        if (!ok) dest.delete()
        ok
    }

    /** Draws [marks] onto [canvas] sized [w] by [h]; the same shapes and colours as the editor shows. */
    fun draw(canvas: Canvas, w: Float, h: Float, marks: List<PdfMark>) {
        val stroke = (minOf(w, h) * 0.006f).coerceAtLeast(1.5f)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfMarkColors.INK; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfMarkColors.INK; style = Paint.Style.FILL; textSize = minOf(w, h) * 0.035f }
        val highlight = Paint().apply { color = PdfMarkColors.HIGHLIGHT; style = Paint.Style.FILL }
        fun x(p: NormPoint) = p.x * w
        fun y(p: NormPoint) = p.y * h
        marks.forEach { m ->
            when (m) {
                is PdfMark.Pen -> canvas.drawPath(Path().apply { m.points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p), y(p)) else lineTo(x(p), y(p)) } }, line)
                is PdfMark.Arrow -> {
                    canvas.drawLine(x(m.from), y(m.from), x(m.to), y(m.to), line)
                    val angle = atan2(y(m.to) - y(m.from), x(m.to) - x(m.from))
                    val head = stroke * 5f
                    for (side in listOf(-1, 1)) {
                        val a = angle + Math.PI.toFloat() + side * 0.5f
                        canvas.drawLine(x(m.to), y(m.to), x(m.to) + head * cos(a), y(m.to) + head * sin(a), line)
                    }
                }
                is PdfMark.Rect -> canvas.drawRect(x(m.topLeft), y(m.topLeft), x(m.bottomRight), y(m.bottomRight), line)
                is PdfMark.Highlight -> canvas.drawRect(x(m.topLeft), y(m.topLeft), x(m.bottomRight), y(m.bottomRight), highlight)
                is PdfMark.Text -> canvas.drawText(m.text, x(m.at), y(m.at), text)
            }
        }
    }
}
