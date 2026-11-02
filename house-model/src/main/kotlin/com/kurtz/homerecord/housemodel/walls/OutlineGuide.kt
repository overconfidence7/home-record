package com.kurtz.homerecord.housemodel.walls

import kotlin.math.hypot
import kotlin.math.min

/** A rectangle in the upright photo, 0..1 across and 0..1 down. */
data class GuideRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The geometry behind the outline screen (AC-C18): the dashed guide that shows the wall's shape, which handle a touch picks
 * up, and which side of the photo the loupe sits on so the finger never covers it.
 */
object OutlineGuide {
    /** How much of the photo the guide fills along its tighter side. */
    const val GUIDE_FILL = 0.8f

    /**
     * The wall's length × height as a rectangle centered in a [photoWidth] × [photoHeight] photo, as large as fits in
     * [GUIDE_FILL] of it, in 0..1 photo coordinates. Null when the wall's size or the photo's is unknown.
     */
    fun guide(photoWidth: Int, photoHeight: Int, lengthIn: Int, heightIn: Int): GuideRect? {
        if (photoWidth <= 0 || photoHeight <= 0 || lengthIn <= 0 || heightIn <= 0) return null
        val wallAspect = lengthIn.toFloat() / heightIn
        val maxW = photoWidth * GUIDE_FILL
        val maxH = photoHeight * GUIDE_FILL
        val w = min(maxW, maxH * wallAspect)
        val h = w / wallAspect
        val left = (photoWidth - w) / 2f / photoWidth
        val top = (photoHeight - h) / 2f / photoHeight
        return GuideRect(left, top, 1f - left, 1f - top)
    }

    /**
     * The corner (0..3, in [WallOutline.corners] order) nearest to a touch at [x], [y] on a photo drawn [width] × [height]
     * pixels, when it is within [radius] pixels; null for a touch away from every handle.
     */
    fun cornerAt(outline: WallOutline, x: Float, y: Float, width: Float, height: Float, radius: Float): Int? =
        outline.corners
            .mapIndexed { i, c -> i to hypot(c.x * width - x, c.y * height - y) }
            .filter { it.second <= radius }
            .minByOrNull { it.second }
            ?.first

    /** True when the loupe goes at the top right: a handle in the photo's left half would sit under a top-left loupe. */
    fun loupeOnRight(point: PhotoPoint): Boolean = point.x < 0.5f
}
