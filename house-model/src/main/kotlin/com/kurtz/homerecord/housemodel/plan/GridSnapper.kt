package com.kurtz.homerecord.housemodel.plan

import kotlin.math.abs
import kotlin.math.roundToInt

/** Grid and neighbor-edge snapping. [gridIn] is 6 in (imperial) or about 10 cm (metric, 4 in). */
class GridSnapper(val gridIn: Int = 6, val neighborThresholdIn: Int = 6) {
    fun snap(value: Int): Int = (value.toDouble() / gridIn).roundToInt() * gridIn

    fun snap(p: Pt) = Pt(snap(p.x), snap(p.y))

    /**
     * Snaps a proposed move of [room] by ([dx], [dy]): first to the grid, then pulls each side onto a
     * neighboring room's parallel side when within [neighborThresholdIn]. Returns the final (dx, dy).
     */
    fun snapMove(room: PlanRoom, dx: Int, dy: Int, others: List<PlanRoom>): Pair<Int, Int> {
        var sx = snap(room.minX + dx) - room.minX
        var sy = snap(room.minY + dy) - room.minY
        val xs = others.flatMap { o -> o.points.map { it.x } }.distinct()
        val ys = others.flatMap { o -> o.points.map { it.y } }.distinct()
        pull(listOf(room.minX + dx, room.maxX + dx), xs)?.let { sx = dx + it }
        pull(listOf(room.minY + dy, room.maxY + dy), ys)?.let { sy = dy + it }
        return sx to sy
    }

    /** Snaps one coordinate being dragged (an edge handle or corner) to neighbors, else to the grid. */
    fun snapCoordinate(value: Int, neighbors: List<Int>): Int {
        val near = neighbors.minByOrNull { abs(it - value) }
        return if (near != null && abs(near - value) <= neighborThresholdIn) near else snap(value)
    }

    private fun pull(sides: List<Int>, targets: List<Int>): Int? {
        var best: Int? = null
        var bestDist = Int.MAX_VALUE
        for (s in sides) for (t in targets) {
            val d = abs(t - s)
            if (d <= neighborThresholdIn && d < bestDist) {
                bestDist = d
                best = t - s
            }
        }
        return best
    }
}
