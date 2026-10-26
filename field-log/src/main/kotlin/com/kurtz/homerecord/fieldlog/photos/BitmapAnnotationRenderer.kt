package com.kurtz.homerecord.fieldlog.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotatedImageRenderer
import com.kurtz.homerecord.core.photo.NormPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Burns marks into a copy of the photo as a JPEG (AC-F26). Mark colour is a fixed high-visibility red, not a theme colour. */
object BitmapAnnotationRenderer : AnnotatedImageRenderer {
    private const val MARK_COLOR = 0xFFE53935.toInt()

    override suspend fun render(sourcePath: String, marks: List<Annotation>, destPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = BitmapFactory.decodeFile(sourcePath) ?: return@runCatching false
            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            source.recycle()
            draw(Canvas(out), out.width.toFloat(), out.height.toFloat(), marks)
            File(destPath).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }.also { out.recycle() }
        }.getOrDefault(false)
    }

    /** Draws onto [canvas] sized [w] by [h]; the same marks as the editor shows, scaled to the photo. */
    fun draw(canvas: Canvas, w: Float, h: Float, marks: List<Annotation>) {
        val stroke = (minOf(w, h) * 0.008f).coerceAtLeast(3f)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MARK_COLOR; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MARK_COLOR; style = Paint.Style.FILL; textSize = minOf(w, h) * 0.05f; setShadowLayer(stroke, 0f, 0f, 0xFF000000.toInt()) }
        fun x(p: NormPoint) = p.x * w
        fun y(p: NormPoint) = p.y * h
        marks.forEach { m ->
            when (m) {
                is Annotation.Arrow -> arrow(canvas, x(m.from), y(m.from), x(m.to), y(m.to), line, stroke)
                is Annotation.Circle -> canvas.drawCircle(x(m.center), y(m.center), m.radius * minOf(w, h), line)
                is Annotation.Rectangle -> canvas.drawRect(x(m.topLeft), y(m.topLeft), x(m.bottomRight), y(m.bottomRight), line)
                is Annotation.Freehand -> canvas.drawPath(Path().apply { m.points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p), y(p)) else lineTo(x(p), y(p)) } }, line)
                is Annotation.Text -> canvas.drawText(m.text, x(m.at), y(m.at), fill)
                is Annotation.Measure -> {
                    canvas.drawLine(x(m.from), y(m.from), x(m.to), y(m.to), line)
                    canvas.drawText(m.label, (x(m.from) + x(m.to)) / 2f, (y(m.from) + y(m.to)) / 2f - stroke * 2, fill)
                }
            }
        }
    }

    private fun arrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint, stroke: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint)
        val angle = atan2(y2 - y1, x2 - x1)
        val head = stroke * 5f
        for (side in listOf(-1, 1)) {
            val a = angle + Math.PI.toFloat() + side * 0.5f
            canvas.drawLine(x2, y2, x2 + head * cos(a), y2 + head * sin(a), paint)
        }
    }
}
