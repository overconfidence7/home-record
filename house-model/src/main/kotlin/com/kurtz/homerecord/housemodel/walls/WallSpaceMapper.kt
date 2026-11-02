package com.kurtz.homerecord.housemodel.walls

import kotlin.math.abs

/**
 * A point on a wall in wall space (AC-C27): 0..1 along the wall from its left end as seen from inside the room, and 0..1
 * down from the ceiling line. The same space as the straightened texture (AC-C19), so a point keeps its place on the wall
 * whichever stage's photo is shown.
 */
data class WallPoint(val x: Float, val y: Float) {
    fun clamped(): WallPoint = WallPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/** A wall-space point in inches from the wall's top-left corner, for labels like "32 in from the left, 18 in down". */
data class WallInches(val fromLeftIn: Int, val fromTopIn: Int)

/**
 * Maps overlay points between a wall photo and wall space (AC-C27). Overlays are stored in wall space, never in photo
 * pixels: a point drawn on the Rough-in photo goes through that photo's outline into wall space, and the Finished photo
 * shows it through its own outline, so a pipe stays at the same place on the wall even when the two photos were taken
 * from different spots.
 *
 * The mapping is the 4-point perspective transform from the unit square onto the outline's corners (the same one the
 * texture warp inverts with `Matrix.setPolyToPoly`), done here in plain Kotlin so it is testable without Android. A photo
 * without an outline, the straightened texture, and the plain wall tile are already in wall space.
 */
object WallSpaceMapper {
    /** Where wall-space [point] sits in a photo outlined by [outline] (null: the picture is the texture or plain wall). */
    fun wallToPhoto(outline: WallOutline?, point: WallPoint): PhotoPoint {
        if (outline == null) return PhotoPoint(point.x, point.y)
        val (x, y) = Homography.squareToQuad(outline).apply(point.x, point.y)
        return PhotoPoint(x, y)
    }

    /**
     * The wall-space point under photo point [point], clamped onto the wall; null when the outline is not usable (crossed
     * or collapsed) so no point can be placed through it.
     */
    fun photoToWall(outline: WallOutline?, point: PhotoPoint): WallPoint? {
        if (outline == null) return WallPoint(point.x, point.y).clamped()
        if (!outline.isUsable()) return null
        val inverse = Homography.squareToQuad(outline).inverse() ?: return null
        val (x, y) = inverse.apply(point.x, point.y)
        if (x.isNaN() || y.isNaN()) return null
        return WallPoint(x, y).clamped()
    }

    /** True when photo point [point] falls on the outlined wall (inside the outline), so a tap there can start a mark. */
    fun isOnWall(outline: WallOutline?, point: PhotoPoint): Boolean {
        if (outline == null) return point.x in 0f..1f && point.y in 0f..1f
        if (!outline.isUsable()) return false
        val inverse = Homography.squareToQuad(outline).inverse() ?: return false
        val (x, y) = inverse.apply(point.x, point.y)
        return x in -EDGE..1f + EDGE && y in -EDGE..1f + EDGE
    }

    /** [points] as drawn on the photo outlined by [from], shown on the photo outlined by [to] (Rough-in to Finished). */
    fun remap(points: List<PhotoPoint>, from: WallOutline?, to: WallOutline?): List<PhotoPoint>? =
        points.map { photoToWall(from, it) ?: return null }.map { wallToPhoto(to, it) }

    /** [point] in whole inches on a wall [lengthIn] long and [heightIn] high; zero sizes give zero. */
    fun toInches(point: WallPoint, lengthIn: Int, heightIn: Int): WallInches {
        val p = point.clamped()
        return WallInches(Math.round(p.x * lengthIn.coerceAtLeast(0)), Math.round(p.y * heightIn.coerceAtLeast(0)))
    }

    // Taps a hair outside the outline (a finger's width on the edge line) still count as on the wall, then clamp.
    private const val EDGE = 0.02f
}

/** A 3x3 projective transform, row major, applied to (x, y, 1). */
internal class Homography(private val m: DoubleArray) {
    fun apply(x: Float, y: Float): Pair<Float, Float> {
        val w = m[6] * x + m[7] * y + m[8]
        if (abs(w) < 1e-12) return Float.NaN to Float.NaN
        return ((m[0] * x + m[1] * y + m[2]) / w).toFloat() to ((m[3] * x + m[4] * y + m[5]) / w).toFloat()
    }

    fun inverse(): Homography? {
        val (a, b, c) = Triple(m[0], m[1], m[2])
        val (d, e, f) = Triple(m[3], m[4], m[5])
        val (g, h, i) = Triple(m[6], m[7], m[8])
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < 1e-12) return null
        return Homography(
            doubleArrayOf(
                (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
                (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
                (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
            ),
        )
    }

    companion object {
        /** The transform taking (0,0), (1,0), (1,1), (0,1) to the outline's top-left, top-right, bottom-right, bottom-left. */
        fun squareToQuad(outline: WallOutline): Homography {
            val x0 = outline.topLeft.x.toDouble(); val y0 = outline.topLeft.y.toDouble()
            val x1 = outline.topRight.x.toDouble(); val y1 = outline.topRight.y.toDouble()
            val x2 = outline.bottomRight.x.toDouble(); val y2 = outline.bottomRight.y.toDouble()
            val x3 = outline.bottomLeft.x.toDouble(); val y3 = outline.bottomLeft.y.toDouble()
            val sx = x0 - x1 + x2 - x3
            val sy = y0 - y1 + y2 - y3
            if (abs(sx) < 1e-12 && abs(sy) < 1e-12) {
                // A parallelogram: affine.
                return Homography(doubleArrayOf(x1 - x0, x3 - x0, x0, y1 - y0, y3 - y0, y0, 0.0, 0.0, 1.0))
            }
            val dx1 = x1 - x2; val dx2 = x3 - x2
            val dy1 = y1 - y2; val dy2 = y3 - y2
            val den = dx1 * dy2 - dx2 * dy1
            val g = if (abs(den) < 1e-12) 0.0 else (sx * dy2 - dx2 * sy) / den
            val h = if (abs(den) < 1e-12) 0.0 else (dx1 * sy - sx * dy1) / den
            return Homography(
                doubleArrayOf(
                    x1 - x0 + g * x1, x3 - x0 + h * x3, x0,
                    y1 - y0 + g * y1, y3 - y0 + h * y3, y0,
                    g, h, 1.0,
                ),
            )
        }
    }
}
