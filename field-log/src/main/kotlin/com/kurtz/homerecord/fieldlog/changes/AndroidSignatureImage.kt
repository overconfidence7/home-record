package com.kurtz.homerecord.fieldlog.changes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.kurtz.homerecord.core.model.SignatureStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Draws a signature into a PNG in [mediaDir] (AC-F38): black ink on a transparent background, so it prints on the
 * PDF (T057b) and sits on any page. Returns the file's path, or null when it could not be written.
 */
class AndroidSignatureImage(private val mediaDir: () -> File, private val width: Int = 1200, private val height: Int = 480) {
    suspend fun write(strokes: List<SignatureStroke>): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = width / 150f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val canvas = Canvas(bitmap)
            strokes.filter { it.pointCount > 0 }.forEach { s ->
                val path = Path()
                path.moveTo(s.x(0) * width, s.y(0) * height)
                for (i in 1 until s.pointCount) path.lineTo(s.x(i) * width, s.y(i) * height)
                if (s.pointCount == 1) path.lineTo(s.x(0) * width + 1f, s.y(0) * height)
                canvas.drawPath(path, paint)
            }
            val dir = mediaDir().also { it.mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }
}
