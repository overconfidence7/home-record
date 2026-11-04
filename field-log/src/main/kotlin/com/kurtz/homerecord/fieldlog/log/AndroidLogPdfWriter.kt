package com.kurtz.homerecord.fieldlog.log

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.kurtz.homerecord.core.log.LogPdfContent
import com.kurtz.homerecord.core.log.LogPdfLayout
import com.kurtz.homerecord.core.log.LogPdfMeasure
import com.kurtz.homerecord.core.log.LogPdfOp
import com.kurtz.homerecord.core.log.LogPdfPage
import com.kurtz.homerecord.fieldlog.photos.BitmapAnnotationRenderer
import java.io.File
import java.io.OutputStream

/**
 * Draws a daily log PDF (AC-F33) with the platform PdfDocument: [LogPdfLayout] places everything, this draws it.
 * Photos are decoded at about twice the size they print, cropped to fill their cell; the logo is fitted inside its
 * square. Photos print upright whatever their EXIF orientation. A photo that cannot be read leaves a light box rather
 * than failing the whole PDF.
 */
class AndroidLogPdfWriter(private val pageFooter: (page: Int, pages: Int) -> String, private val spec: LogPdfPage = LogPdfPage()) {
    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE; strokeWidth = 0.75f }
    private val missing = Paint().apply { color = RULE; style = Paint.Style.FILL }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val measure = LogPdfMeasure { t, size, isBold ->
        style(size, isBold, false)
        text.measureText(t)
    }

    fun pages(content: LogPdfContent): List<List<LogPdfOp>> = LogPdfLayout(measure, pageFooter, spec).layout(content)

    /** Measures text with the paint it draws with; other PDF layouts (the change order, AC-F39) use it too. */
    val measurer: LogPdfMeasure get() = measure

    fun write(content: LogPdfContent, out: OutputStream) = write(pages(content), out, content.logoPath)

    /** Draws pages laid out by any layout that speaks [LogPdfOp]. */
    fun write(pages: List<List<LogPdfOp>>, out: OutputStream, logoPath: String? = null) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, ops ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(spec.widthPt.toInt(), spec.heightPt.toInt(), index + 1).create())
                draw(page.canvas, ops, logoPath)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    internal fun draw(canvas: Canvas, ops: List<LogPdfOp>, logoPath: String?) {
        ops.forEach { op ->
            when (op) {
                is LogPdfOp.Text -> {
                    style(op.size, op.bold, op.muted)
                    canvas.drawText(op.text, op.x, op.y, text)
                }
                is LogPdfOp.Rule -> canvas.drawLine(op.x1, op.y, op.x2, op.y, rule)
                is LogPdfOp.Image -> {
                    val box = RectF(op.left, op.top, op.right, op.bottom)
                    val fit = op.fit || op.path == logoPath
                    val bmp = decode(op.path, box)
                    if (bmp == null) {
                        if (!fit) canvas.drawRect(box, missing)
                    } else {
                        canvas.drawBitmap(bmp, if (fit) null else cropTo(bmp, box), if (fit) fitIn(bmp, box) else box, bitmapPaint)
                        bmp.recycle()
                    }
                }
            }
        }
    }

    private fun style(size: Float, isBold: Boolean, muted: Boolean) {
        text.textSize = size
        text.typeface = if (isBold) bold else regular
        text.color = if (muted) MUTED else INK
    }

    /** Decodes [path] no larger than about twice the box (PDF points print at 72 dpi), turned upright by its EXIF tag. */
    private fun decode(path: String, box: RectF): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val want = maxOf(box.width(), box.height()) * 2f
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= want) sample *= 2
        val stored = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // a portrait shot is stored sideways with an EXIF turn; print it the way the camera saw it
        BitmapAnnotationRenderer.turnUpright(stored, BitmapAnnotationRenderer.orientationOf(path))
    }.getOrNull()

    /** The centre part of [bmp] with [box]'s aspect ratio, so the photo fills its cell without stretching. */
    private fun cropTo(bmp: Bitmap, box: RectF): Rect {
        val target = box.width() / box.height()
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        return if (w / h > target) {
            val cw = h * target
            Rect(((w - cw) / 2).toInt(), 0, ((w + cw) / 2).toInt(), h.toInt())
        } else {
            val ch = w / target
            Rect(0, ((h - ch) / 2).toInt(), w.toInt(), ((h + ch) / 2).toInt())
        }
    }

    /** [box] shrunk to [bmp]'s aspect ratio, top left aligned, for the logo. */
    private fun fitIn(bmp: Bitmap, box: RectF): RectF {
        val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
        return RectF(box.left, box.top, box.left + bmp.width * scale, box.top + bmp.height * scale)
    }

    private companion object {
        val INK = Color.rgb(0x1B, 0x1B, 0x1F)
        val MUTED = Color.rgb(0x5E, 0x5E, 0x66)
        val RULE = Color.rgb(0xD0, 0xD0, 0xD6)
    }
}
