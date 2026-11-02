package com.kurtz.homerecord.housemodel.walls

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import com.kurtz.homerecord.core.photo.ImageOrientation
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Width and height of a wall texture in pixels. */
data class TextureSize(val width: Int, val height: Int)

/** A texture written by [WallTextureBuilder.build]. */
data class WallTexture(val path: String, val width: Int, val height: Int)

/**
 * Straightens the outlined wall in a photo into a flat texture (AC-C19): the four corners are mapped onto a rectangle of the
 * wall's length by height with a 4-point perspective transform (`Matrix.setPolyToPoly`), at most [MAX_LONG_SIDE] pixels on
 * the long side, and written as a JPEG in app-private storage. The 3D view puts the result on the wall's face.
 */
object WallTextureBuilder {
    const val MAX_LONG_SIDE = 2048
    private const val JPEG_QUALITY = 88
    // A 12 MP phone shot decodes as is; a 50 MP one is sampled down before warping so it fits in memory.
    private const val MAX_SOURCE_PIXELS = 16_000_000L

    /**
     * The texture's size: the wall's own aspect ([lengthIn] by [heightIn]) when it is known, else the outline's; the long side
     * as long as the outline's longer edges in the photo (no upscaling) and never over [MAX_LONG_SIDE].
     */
    fun textureSize(lengthIn: Int, heightIn: Int, quadWidthPx: Float, quadHeightPx: Float): TextureSize {
        val aspect = when {
            lengthIn > 0 && heightIn > 0 -> lengthIn.toFloat() / heightIn
            quadWidthPx > 0f && quadHeightPx > 0f -> quadWidthPx / quadHeightPx
            else -> 1f
        }
        val long = max(quadWidthPx, quadHeightPx).roundToInt().coerceIn(1, MAX_LONG_SIDE)
        return if (aspect >= 1f) {
            TextureSize(long, (long / aspect).roundToInt().coerceAtLeast(1))
        } else {
            TextureSize((long * aspect).roundToInt().coerceAtLeast(1), long)
        }
    }

    /** The outline's corners in pixels of a [width] by [height] photo, as `x0,y0,…,x3,y3` in [WallOutline.corners] order. */
    fun cornersPx(outline: WallOutline, width: Int, height: Int): FloatArray =
        outline.corners.flatMap { listOf(it.x * width, it.y * height) }.toFloatArray()

    /** The longer of each pair of opposite edges of the quad [px], as width and height. */
    fun quadExtent(px: FloatArray): Pair<Float, Float> {
        fun d(a: Int, b: Int) = hypot(px[2 * a] - px[2 * b], px[2 * a + 1] - px[2 * b + 1])
        return max(d(0, 1), d(3, 2)) to max(d(0, 3), d(1, 2))
    }

    /**
     * The 4-point perspective transform that sends the quad [srcPx] (top-left, top-right, bottom-right, bottom-left) to the
     * corners of a [size] rectangle; null when the quad is degenerate and no such transform exists.
     */
    fun warpMatrix(srcPx: FloatArray, size: TextureSize): Matrix? {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val matrix = Matrix()
        return if (matrix.setPolyToPoly(srcPx, 0, dst, 0, 4)) matrix else null
    }

    /**
     * Warps the photo at [sourcePath] (shown upright, so EXIF orientation is applied first) inside [outline] into a texture for
     * a wall of [lengthIn] by [heightIn] and writes it to [destPath]. Null when the photo cannot be read, the outline is not
     * usable or the file cannot be written. Blocking: call it off the main thread.
     */
    fun build(sourcePath: String, outline: WallOutline, lengthIn: Int, heightIn: Int, destPath: String): WallTexture? = runCatching {
        if (!outline.isUsable()) return@runCatching null
        val source = decodeUpright(sourcePath) ?: return@runCatching null
        try {
            val src = cornersPx(outline, source.width, source.height)
            val (qw, qh) = quadExtent(src)
            val size = textureSize(lengthIn, heightIn, qw, qh)
            val matrix = warpMatrix(src, size) ?: return@runCatching null
            val out = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                val file = File(destPath)
                file.parentFile?.mkdirs()
                val written = file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (written) WallTexture(file.path, size.width, size.height) else null
            } finally {
                out.recycle()
            }
        } finally {
            source.recycle()
        }
    }.getOrNull()

    /** The photo's size as it stands upright (EXIF applied), read from the header only; null when it is not an image. */
    fun uprightSize(path: String): TextureSize? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val (w, h) = orientationOf(path).uprightSize(bounds.outWidth, bounds.outHeight)
        TextureSize(w, h)
    }.getOrNull()

    private fun orientationOf(path: String): ImageOrientation = runCatching {
        ImageOrientation.fromExif(ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
    }.getOrDefault(ImageOrientation.Upright)

    private fun decodeUpright(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageOrientation.sampleSize(bounds.outWidth, bounds.outHeight, MAX_SOURCE_PIXELS)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val stored = BitmapFactory.decodeFile(path, options) ?: return null
        val orientation = orientationOf(path)
        if (orientation.isIdentity) return stored
        val turn = Matrix().apply {
            postRotate(orientation.rotationDegrees.toFloat())
            if (orientation.flipHorizontal) postScale(-1f, 1f)
        }
        val upright = Bitmap.createBitmap(stored, 0, 0, stored.width, stored.height, turn, true)
        if (upright !== stored) stored.recycle()
        return upright
    }
}
