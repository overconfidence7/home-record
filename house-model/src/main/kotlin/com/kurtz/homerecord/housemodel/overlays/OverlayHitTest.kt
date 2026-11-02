package com.kurtz.homerecord.housemodel.overlays

import kotlin.math.hypot

/**
 * Which overlay or point a finger lands on in the wall editor (AC-C28). Works in screen pixels: the caller maps each
 * overlay's wall-space points onto the picture first. A run is hit anywhere along its segments, a spot near its point;
 * when two are within reach the nearest wins, and on a tie the one drawn last (on top).
 */
object OverlayHitTest {
    /** The index in [shapes] of the overlay nearest ([x], [y]) within [radius], or null when none is that close. */
    fun overlayAt(shapes: List<List<OverlayPoint>>, x: Float, y: Float, radius: Float): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        shapes.forEachIndexed { index, points ->
            val d = distance(points, x, y)
            if (d <= radius && d <= bestDistance) {
                best = index
                bestDistance = d
            }
        }
        return best
    }

    /** The index of the point in [points] nearest ([x], [y]) within [radius], for dragging it; null when none is. */
    fun pointAt(points: List<OverlayPoint>, x: Float, y: Float, radius: Float): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        points.forEachIndexed { index, p ->
            val d = hypot(p.x - x, p.y - y)
            if (d <= radius && d < bestDistance) {
                best = index
                bestDistance = d
            }
        }
        return best
    }

    /** Distance from ([x], [y]) to the overlay: to its point, or to the nearest of its segments. */
    fun distance(points: List<OverlayPoint>, x: Float, y: Float): Float = when (points.size) {
        0 -> Float.MAX_VALUE
        1 -> hypot(points[0].x - x, points[0].y - y)
        else -> points.zipWithNext { a, b -> segmentDistance(a, b, x, y) }.min()
    }

    private fun segmentDistance(a: OverlayPoint, b: OverlayPoint, x: Float, y: Float): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(a.x - x, a.y - y)
        val t = (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(a.x + t * dx - x, a.y + t * dy - y)
    }
}
