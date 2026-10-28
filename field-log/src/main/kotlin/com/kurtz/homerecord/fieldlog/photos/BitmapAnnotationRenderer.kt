package com.kurtz.homerecord.fieldlog.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.media.ExifInterface
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotatedImageRenderer
import com.kurtz.homerecord.core.photo.ImageOrientation
import com.kurtz.homerecord.core.photo.NormPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Burns marks into an upright copy of the photo as a JPEG (AC-F26). The stored image is turned by its EXIF orientation
 * first, since the editor shows it upright, and decoded at most [MAX_PIXELS] so a large shot fits in memory. Mark colour
 * is a fixed high-visibility red, not a theme colour.
 */
object BitmapAnnotationRenderer : AnnotatedImageRenderer {
    private const val MARK_COLOR = 0xFFE53935.toInt()
    private const val MAX_PIXELS = 8_000_000L

    override suspend fun render(sourcePath: String, marks: List<Annotation>, destPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val out = decodeUpright(sourcePath) ?: return@runCatching false
            draw(Canvas(out), out.width.toFloat(), out.height.toFloat(), marks)
            File(destPath).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }.also { out.recycle() }
        }.getOrDefault(false)
    }

    /** The EXIF orientation of the image at [path]; upright when it has none or cannot be read. */
    fun orientationOf(path: String): ImageOrientation = runCatching {
        ImageOrientation.fromExif(ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
    }.getOrDefault(ImageOrientation.Upright)

    /** Upright width over height of the image at [path], as the editor and Coil show it, or null when it cannot be read. */
    fun uprightAspect(path: String): Float? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return orientationOf(path).uprightAspect(bounds.outWidth, bounds.outHeight)
    }

    /** Decodes [path] mutable, sampled under the pixel cap and turned upright; only a turned image needs a second bitmap. */
    internal fun decodeUpright(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageOrientation.sampleSize(bounds.outWidth, bounds.outHeight, MAX_PIXELS)
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val stored = BitmapFactory.decodeFile(path, options) ?: return null
        val orientation = orientationOf(path)
        if (orientation.isIdentity) return stored
        // Turn, then mirror: EXIF 5 (transpose) is a quarter turn clockwise followed by a horizontal flip.
        val matrix = Matrix().apply {
            postRotate(orientation.rotationDegrees.toFloat())
            if (orientation.flipHorizontal) postScale(-1f, 1f)
        }
        val upright = Bitmap.createBitmap(stored, 0, 0, stored.width, stored.height, matrix, true)
        if (upright !== stored) stored.recycle()
        return if (upright.isMutable) upright else upright.copy(Bitmap.Config.ARGB_8888, true).also { upright.recycle() }
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
