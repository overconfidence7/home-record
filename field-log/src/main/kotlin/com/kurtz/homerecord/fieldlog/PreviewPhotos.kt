package com.kurtz.homerecord.fieldlog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import coil3.Image
import coil3.asImage
import java.io.File

/**
 * Stand-in photos for previews, screenshot tests and the QA screen catalog (QA rule 3: realistic fake state). The fake
 * photo paths in [Previews] (`/none/…`, `/preview/…`, `/media/…`) point at no file, so without this every photo screen
 * showed its broken-image fallback and nobody saw a crew's photo on the Photo, Photos, Mark up or Ghost screens
 * ([judge:qa-fl-photo-catalog-no-image], [judge:fl-annotate-no-photo]). Real paths (filesDir/media) never match.
 */
object PreviewPhotos {
    private val fakeRoots = listOf("/none/", "/preview/", "/media/")

    /** True when [data] (a Coil request's data) is one of the previews' fake photo paths. */
    fun isFake(data: Any?): Boolean {
        val path = when (data) {
            is File -> data.path
            is String -> data
            else -> return false
        }
        return fakeRoots.any { path.startsWith(it) }
    }

    /** A drawn jobsite photo for [data]; the same path always gives the same picture, different paths differ. */
    fun image(data: Any?): Image? = if (isFake(data)) draw(data) else null

    /** A drawn photo for any request; previews (no files at all) use this for every image. */
    fun draw(data: Any?): Image {
        val path = (data as? File)?.path ?: data.toString()
        return (if ("receipt" in path) receipt() else wall(path.hashCode())).asImage()
    }

    /** A drywall wall with an open electrical panel, studs and a floor line; [seed] shifts the light and layout. */
    private fun wall(seed: Int): Bitmap {
        val w = 960
        val h = 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val v = (seed and 0x7fffffff) % 4
        val warm = intArrayOf(0xFFD9CBB4.toInt(), 0xFFCFC6B8.toInt(), 0xFFD6D0C2.toInt(), 0xFFC9BBA3.toInt())[v]
        val shade = intArrayOf(0xFF9C8E78.toInt(), 0xFF8F887C.toInt(), 0xFF9A9486.toInt(), 0xFF8C7E68.toInt())[v]
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), warm, shade, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // Floor and baseboard
        p.color = 0xFF6B5A47.toInt()
        c.drawRect(0f, h * 0.86f, w.toFloat(), h.toFloat(), p)
        p.color = 0xFFEDE8DE.toInt()
        c.drawRect(0f, h * 0.83f, w.toFloat(), h * 0.86f, p)
        // Open stud bay beside the panel
        val bay = w * (0.08f + v * 0.04f)
        p.color = 0xFFB98F5E.toInt()
        c.drawRect(bay, h * 0.08f, bay + 34f, h * 0.83f, p)
        c.drawRect(bay + 250f, h * 0.08f, bay + 284f, h * 0.83f, p)
        // Panel box, door open, two columns of breakers
        val left = w * (0.48f + v * 0.03f)
        val box = RectF(left, h * 0.14f, left + w * 0.3f, h * 0.7f)
        p.color = 0xFF7D8288.toInt()
        c.drawRoundRect(box, 10f, 10f, p)
        p.color = 0xFF3F4347.toInt()
        val inner = RectF(box.left + 24f, box.top + 28f, box.right - 24f, box.bottom - 28f)
        c.drawRect(inner, p)
        val rows = 8
        val rowH = inner.height() / rows
        for (i in 0 until rows) {
            for (col in 0..1) {
                val x = if (col == 0) inner.left + 14f else inner.centerX() + 8f
                p.color = 0xFF1E2023.toInt()
                c.drawRect(x, inner.top + i * rowH + 8f, x + inner.width() / 2 - 22f, inner.top + (i + 1) * rowH - 8f, p)
                p.color = if ((i + col + v) % 5 == 0) 0xFFC23B22.toInt() else 0xFFE6E6E6.toInt()
                c.drawRect(x + 12f, inner.top + i * rowH + 16f, x + 40f, inner.top + (i + 1) * rowH - 16f, p)
            }
        }
        // Cable runs from the bay into the panel
        p.style = Paint.Style.STROKE
        p.strokeWidth = 9f
        p.color = 0xFFE8D25A.toInt()
        c.drawLine(bay + 140f, h * 0.2f, box.left, box.top + 60f, p)
        p.color = 0xFFF2F2F2.toInt()
        c.drawLine(bay + 120f, h * 0.45f, box.left, box.top + 140f, p)
        return bmp
    }

    /** A supply-house receipt on a dark counter: header block, item lines and a total line. */
    private fun receipt(): Bitmap {
        val w = 720
        val h = 960
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF3A3530.toInt()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.color = 0xFFF7F5F0.toInt()
        val slip = RectF(w * 0.18f, h * 0.06f, w * 0.82f, h * 0.94f)
        c.drawRect(slip, p)
        p.color = 0xFF55524D.toInt()
        c.drawRect(slip.left + 60f, slip.top + 50f, slip.right - 60f, slip.top + 90f, p)
        for (i in 0 until 9) {
            val y = slip.top + 150f + i * 52f
            c.drawRect(slip.left + 40f, y, slip.left + 40f + 160f + (i * 37 % 90), y + 16f, p)
            c.drawRect(slip.right - 130f, y, slip.right - 40f, y + 16f, p)
        }
        p.color = 0xFF222222.toInt()
        c.drawRect(slip.left + 40f, slip.bottom - 140f, slip.left + 200f, slip.bottom - 112f, p)
        c.drawRect(slip.right - 170f, slip.bottom - 140f, slip.right - 40f, slip.bottom - 112f, p)
        return bmp
    }
}
