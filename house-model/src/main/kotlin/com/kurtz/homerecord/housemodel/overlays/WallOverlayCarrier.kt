package com.kurtz.homerecord.housemodel.overlays

import com.kurtz.homerecord.housemodel.plan.Pt
import kotlin.math.roundToInt

/** Where a wall overlay goes when its room is reshaped (R10). */
sealed interface CarriedOverlay {
    /** Stays on its wall with its points as they are. */
    data object Unchanged : CarriedOverlay

    /** Goes onto the wall now on new edge [edgeIndex], with [points] in that wall's space. */
    data class OnWall(val edgeIndex: Int, val points: List<OverlayPoint>) : CarriedOverlay

    /** Its wall is gone and no new wall runs where it was: it goes onto the floor plan at [points] (plan inches). */
    data class OnPlan(val points: List<OverlayPoint>) : CarriedOverlay
}

/**
 * Carries a wall overlay (points in wall space, AC-C27) through a reshape of its room in the plan editor.
 *
 * - A wall whose corners moved (a side dragged, a length typed in) keeps the overlay's wall-space points: they stay
 *   at the same place between the wall's corners, the same way the wall photo (outlined corner to corner) stretches.
 * - A wall split by a new corner, or merged with its neighbour when the corner between them is removed, hands each
 *   overlay to the new wall running along the stretch where the overlay sat, its points rescaled to that wall; a run
 *   that crossed the new corner goes to the wall holding most of its points and stops at the corner.
 * - An overlay whose wall is gone with no wall left along its line (a notch removed) goes onto the floor plan at its
 *   place along the old wall, so the record of the pipe or wire is never lost.
 *
 * [oldOutline] is the room's previous outline lined up with [newPoints] (a moved room compared as if unmoved);
 * [matchedEdge] is the new edge the overlay's wall kept (WallMatcher), or null when the wall is gone.
 */
object WallOverlayCarrier {
    fun carry(oldOutline: List<Pt>, oldEdge: Int, matchedEdge: Int?, newPoints: List<Pt>, points: List<OverlayPoint>): CarriedOverlay {
        if (oldEdge !in oldOutline.indices || oldOutline.size < 2) return if (matchedEdge != null) CarriedOverlay.Unchanged else CarriedOverlay.OnPlan(emptyList())
        val a = oldOutline[oldEdge]
        val b = oldOutline[(oldEdge + 1) % oldOutline.size]
        val pieces = if (newPoints.size < 2) emptyList() else newPoints.indices.filter { i ->
            val c = newPoints[i]
            val d = newPoints[(i + 1) % newPoints.size]
            onLine(a, b, c) && onLine(a, b, d) && sameDirection(a, b, c, d) && overlaps(a, b, c, d)
        }
        // A wall whose corners moved, one wall to one wall along its line: the overlay moves with its corners. When
        // another old wall on the same line now shares the new wall (a merge), positions are kept along the line instead.
        if (matchedEdge != null && pieces.all { it == matchedEdge }) {
            val m = newPoints[matchedEdge]
            val n = newPoints[(matchedEdge + 1) % newPoints.size]
            val merged = oldOutline.indices.any { j ->
                if (j == oldEdge) return@any false
                val c = oldOutline[j]
                val d = oldOutline[(j + 1) % oldOutline.size]
                onLine(m, n, c) && onLine(m, n, d) && sameDirection(m, n, c, d) && overlaps(m, n, c, d)
            }
            if (!merged) return CarriedOverlay.Unchanged
        }

        val plan = points.map { p -> (a.x + p.x * (b.x - a.x)) to (a.y + p.x * (b.y - a.y)) }
        fun along(edge: Int, x: Float, y: Float): Float {
            val c = newPoints[edge]
            val d = newPoints[(edge + 1) % newPoints.size]
            val ux = (d.x - c.x).toFloat()
            val uy = (d.y - c.y).toFloat()
            val len2 = ux * ux + uy * uy
            return if (len2 == 0f) 0f else ((x - c.x) * ux + (y - c.y) * uy) / len2
        }
        fun covers(edge: Int, x: Float, y: Float) = along(edge, x, y) in -EPS..1f + EPS

        val target = pieces.maxWithOrNull(
            compareBy<Int> { edge -> plan.count { (x, y) -> covers(edge, x, y) } }
                .thenBy { edge -> if (plan.firstOrNull()?.let { (x, y) -> covers(edge, x, y) } == true) 1 else 0 }
                .thenBy { edge -> if (edge == matchedEdge) 1 else 0 },
        )?.takeIf { edge -> plan.isEmpty() || plan.any { (x, y) -> covers(edge, x, y) } }
            ?: matchedEdge
            ?: return CarriedOverlay.OnPlan(plan.map { (x, y) -> OverlayPoint(x.roundToInt().toFloat(), y.roundToInt().toFloat()) })

        return CarriedOverlay.OnWall(target, plan.zip(points) { (x, y), p -> OverlayPoint(along(target, x, y).coerceIn(0f, 1f), p.y) })
    }

    private fun onLine(a: Pt, b: Pt, p: Pt): Boolean =
        (b.x - a.x).toLong() * (p.y - a.y) - (b.y - a.y).toLong() * (p.x - a.x) == 0L

    private fun sameDirection(a: Pt, b: Pt, c: Pt, d: Pt): Boolean =
        (b.x - a.x).toLong() * (d.x - c.x) + (b.y - a.y).toLong() * (d.y - c.y) > 0

    /** The new edge covers some of the old edge's stretch. */
    private fun overlaps(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
        val ux = (b.x - a.x).toLong()
        val uy = (b.y - a.y).toLong()
        fun along(p: Pt) = (p.x - a.x) * ux + (p.y - a.y) * uy
        val lo = maxOf(0L, minOf(along(c), along(d)))
        val hi = minOf(ux * ux + uy * uy, maxOf(along(c), along(d)))
        return hi > lo
    }

    private const val EPS = 1e-4f
}
