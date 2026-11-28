package com.kurtz.homerecord.housemodel.scene

import kotlin.math.abs
import kotlin.math.max

/**
 * Tells a drawn house from a blank 3D view by its pixels ([judge:qa-3d-drawn-tag-blank]): a frame where nearly every
 * pixel is the background colour (white, black or the app surface) or a faint wash of it has nothing on screen, however
 * many frames Filament reports. Walls, floors and pictures differ from the background by a clear step, so a frame
 * counts as drawn when at least [MIN_SHARE] of its pixels stand more than [MIN_STEP] (in any channel, 0..255) away
 * from the most common colour. Pure, so it is unit tested; [FloorSceneView] feeds it a small PixelCopy of the scene.
 *
 * Given the [surface] the scene clears to, the same share must also stand out from that colour
 * ([judge:hm-3d-room-blank-recurs]): a room view that came out white or the surface colour, with a few lines of text or
 * a lighter wash as its most common colour, is blank even if something stands out from that wash.
 */
object SceneFrameCheck {
    const val MIN_STEP = 24
    const val MIN_SHARE = 0.06f

    data class Result(val drawn: Boolean, val share: Float)

    /** [pixels] are packed ARGB as `Bitmap.getPixels` returns them; alpha is ignored. */
    fun check(pixels: IntArray, surface: Int? = null): Result {
        if (pixels.isEmpty()) return Result(false, 0f)
        val background = dominant(pixels)
        var standOut = 0
        var offSurface = 0
        for (p in pixels) {
            if (distance(p, background) > MIN_STEP) standOut++
            if (surface == null || distance(p, surface) > MIN_STEP) offSurface++
        }
        val share = minOf(standOut, offSurface).toFloat() / pixels.size
        return Result(share >= MIN_SHARE, share)
    }

    /** The most common colour, bucketed to 4 bits a channel so a faint gradient counts as one colour. */
    fun dominant(pixels: IntArray): Int {
        val counts = HashMap<Int, Int>()
        val sample = HashMap<Int, Int>()
        for (p in pixels) {
            val bucket = (p shr 4 and 0x0F0F0F)
            counts[bucket] = (counts[bucket] ?: 0) + 1
            sample.putIfAbsent(bucket, p)
        }
        val top = counts.maxByOrNull { it.value }?.key ?: return pixels[0]
        return sample[top] ?: pixels[0]
    }

    private fun distance(a: Int, b: Int): Int = max(
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
        max(abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), abs((a and 0xFF) - (b and 0xFF))),
    )
}
