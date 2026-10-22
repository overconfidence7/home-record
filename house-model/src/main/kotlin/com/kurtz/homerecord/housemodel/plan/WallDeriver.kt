package com.kurtz.homerecord.housemodel.plan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DerivedWall(
    val roomId: String,
    val edgeIndex: Int,
    val lengthIn: Int,
    /** "N", "E", "S", "W" for the side the wall faces, or "D1", "D2"... for diagonal edges. */
    val orientation: String,
    val exterior: Boolean,
)

/** Turns room polygons into one wall per edge. Each room owns its side of a shared edge. */
class WallDeriver(private val sharedToleranceIn: Int = 4) {
    fun derive(rooms: List<PlanRoom>): List<DerivedWall> {
        val out = mutableListOf<DerivedWall>()
        for (room in rooms) {
            val ccwSign = if (room.signedArea2() >= 0) 1 else -1
            var diagonal = 0
            val n = room.points.size
            for (i in 0 until n) {
                val a = room.points[i]
                val b = room.points[(i + 1) % n]
                val length = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).roundToInt()
                val orientation = when {
                    a == b -> "D${++diagonal}"
                    a.y == b.y -> {
                        // Screen coordinates (y down): positive area means the interior is to the right of travel.
                        val dirX = if (b.x > a.x) 1 else -1
                        if (dirX * ccwSign > 0) "N" else "S"
                    }
                    a.x == b.x -> {
                        val dirY = if (b.y > a.y) 1 else -1
                        if (dirY * ccwSign > 0) "E" else "W"
                    }
                    else -> "D${++diagonal}"
                }
                val shared = rooms.any { o -> o.id != room.id && sharesEdge(a, b, o) }
                out += DerivedWall(room.id, i, length, orientation, exterior = !shared)
            }
        }
        return out
    }

    private fun sharesEdge(a: Pt, b: Pt, other: PlanRoom): Boolean {
        val n = other.points.size
        for (j in 0 until n) {
            if (collinearOverlap(a, b, other.points[j], other.points[(j + 1) % n])) return true
        }
        return false
    }

    /** True when segment c-d lies within tolerance of line a-b and overlaps it along its length. */
    private fun collinearOverlap(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
        val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        if (len == 0.0) return false
        val ux = (b.x - a.x) / len
        val uy = (b.y - a.y) / len
        fun perp(p: Pt) = abs((p.x - a.x) * uy - (p.y - a.y) * ux)
        fun along(p: Pt) = (p.x - a.x) * ux + (p.y - a.y) * uy
        if (perp(c) > sharedToleranceIn || perp(d) > sharedToleranceIn) return false
        val lo = max(0.0, min(along(c), along(d)))
        val hi = min(len, max(along(c), along(d)))
        return hi - lo > sharedToleranceIn
    }
}
