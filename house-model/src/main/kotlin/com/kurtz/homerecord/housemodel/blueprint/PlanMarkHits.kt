package com.kurtz.homerecord.housemodel.blueprint

import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import kotlin.math.hypot

/**
 * Which blueprint mark a tap lands on (T055a), in canvas pixels. A mark is placed as the canvas draws it: plan inches
 * through [BlueprintView], then [insetPx] along its [PlanMark.inward] for a wall overlay. Items are drawn on top, so an
 * item within [radiusPx] wins over an overlay; among the same kind the nearest wins, and a spot counts by its center, a
 * run by its nearest segment.
 */
object PlanMarkHitTester {
    fun markAt(marks: List<PlanMark>, view: BlueprintView, px: Float, py: Float, insetPx: Float, radiusPx: Float): PlanMark? {
        var best: PlanMark? = null
        var bestRank = Int.MAX_VALUE
        var bestD = Float.MAX_VALUE
        for (m in marks) {
            val pts = placed(m, view, insetPx)
            if (pts.isEmpty()) continue
            val d = distance(pts, px, py)
            if (d > radiusPx) continue
            val rank = if (m.isItem) 0 else 1
            if (rank < bestRank || (rank == bestRank && d <= bestD)) { best = m; bestRank = rank; bestD = d }
        }
        return best
    }

    /** The mark's points on the canvas, set [insetPx] into the room for a wall overlay. */
    fun placed(m: PlanMark, view: BlueprintView, insetPx: Float): List<Pair<Float, Float>> = m.points.map { p ->
        val x = p.x * view.scale + view.originX
        val y = p.y * view.scale + view.originY
        val n: OverlayPoint? = m.inward
        if (n == null) x to y else x + n.x * insetPx to y + n.y * insetPx
    }

    private fun distance(pts: List<Pair<Float, Float>>, px: Float, py: Float): Float {
        if (pts.size == 1) return hypot(pts[0].first - px, pts[0].second - py)
        var best = Float.MAX_VALUE
        for (i in 0 until pts.size - 1) best = minOf(best, segment(pts[i], pts[i + 1], px, py))
        return best
    }

    private fun segment(a: Pair<Float, Float>, b: Pair<Float, Float>, px: Float, py: Float): Float {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - a.first) * dx + (py - a.second) * dy) / len2).coerceIn(0f, 1f)
        return hypot(a.first + dx * t - px, a.second + dy * t - py)
    }
}

/** An axis-aligned box in canvas pixels. */
data class LabelBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(o: LabelBox): Boolean = left < o.right && right > o.left && top < o.bottom && bottom > o.top
    fun inside(o: LabelBox): Boolean = left >= o.left && right <= o.right && top >= o.top && bottom <= o.bottom
}

/**
 * Where a room's name goes so it keeps clear of what is drawn in the room (T055a): stairs, and the wall overlays and
 * items shown on the blueprint. The name tries its anchor first, then steps up and down by its own height, then beside
 * each obstacle, and takes the first spot inside [room] that touches no obstacle. When none is free it keeps clear of the
 * [hard] obstacles alone (stairs, so treads never cross the text), and failing that stays at its anchor if that is clear
 * of them.
 */
object LabelPlacer {
    /** Small boxes along a drawn run (or around a spot when it has one point), so a diagonal run blocks only its own path. */
    fun alongRun(points: List<Pair<Float, Float>>, halfWidth: Float): List<LabelBox> {
        val box = { x: Float, y: Float -> LabelBox(x - halfWidth, y - halfWidth, x + halfWidth, y + halfWidth) }
        if (points.size == 1) return listOf(box(points[0].first, points[0].second))
        val out = ArrayList<LabelBox>()
        for (i in 0 until points.size - 1) {
            val (ax, ay) = points[i]
            val (bx, by) = points[i + 1]
            val steps = (hypot(bx - ax, by - ay) / halfWidth).toInt().coerceAtLeast(1)
            for (k in 0..steps) out += box(ax + (bx - ax) * k / steps, ay + (by - ay) * k / steps)
        }
        return out
    }

    /**
     * The name's center, or null when no spot keeps it off the [hard] obstacles (the name is then left out, as when it
     * does not fit the room).
     */
    fun place(
        anchorX: Float,
        anchorY: Float,
        width: Float,
        height: Float,
        room: LabelBox,
        hard: List<LabelBox>,
        soft: List<LabelBox>,
        gap: Float,
    ): Pair<Float, Float>? {
        val candidates = ArrayList<Pair<Float, Float>>()
        candidates += anchorX to anchorY
        for (k in 1..3) {
            candidates += anchorX to anchorY - k * (height + gap)
            candidates += anchorX to anchorY + k * (height + gap)
        }
        for (o in hard + soft) {
            candidates += o.left - gap - width / 2f to anchorY
            candidates += o.right + gap + width / 2f to anchorY
        }
        val box = { c: Pair<Float, Float> -> LabelBox(c.first - width / 2f, c.second - height / 2f, c.first + width / 2f, c.second + height / 2f) }
        val all = hard + soft
        candidates.firstOrNull { c -> box(c).let { b -> b.inside(room) && all.none(b::overlaps) } }?.let { return it }
        candidates.firstOrNull { c -> box(c).let { b -> b.inside(room) && hard.none(b::overlaps) } }?.let { return it }
        // A name taller or wider than the room fits no candidate; it stays at its anchor unless that crosses stairs.
        return (anchorX to anchorY).takeIf { c -> hard.none(box(c)::overlaps) }
    }
}
