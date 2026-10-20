package com.kurtz.homerecord.housemodel.plan

enum class PlanIssueKind { OVERLAP, TOO_SMALL }

data class PlanIssue(val kind: PlanIssueKind, val roomIds: List<String>)

/** Flags overlapping rooms and rooms under 2 x 2 ft. Warnings only; never blocks saving. */
class PlanValidator(private val minSideIn: Int = 24) {
    fun validate(rooms: List<PlanRoom>): List<PlanIssue> {
        val issues = mutableListOf<PlanIssue>()
        rooms.filter { it.widthIn < minSideIn || it.lengthIn < minSideIn }
            .forEach { issues += PlanIssue(PlanIssueKind.TOO_SMALL, listOf(it.id)) }
        for (i in rooms.indices) for (j in i + 1 until rooms.size) {
            if (overlaps(rooms[i], rooms[j])) issues += PlanIssue(PlanIssueKind.OVERLAP, listOf(rooms[i].id, rooms[j].id))
        }
        return issues
    }

    fun flaggedRoomIds(rooms: List<PlanRoom>): Set<String> = validate(rooms).flatMap { it.roomIds }.toSet()

    /** Interiors intersect. Rooms that only touch along edges or corners do not overlap. */
    fun overlaps(a: PlanRoom, b: PlanRoom): Boolean {
        if (a.maxX <= b.minX || b.maxX <= a.minX || a.maxY <= b.minY || b.maxY <= a.minY) return false
        if (a.points.toSet() == b.points.toSet()) return true
        if (crossing(a, b)) return true
        // Containment and edge-overlap cases: probe interior points of each polygon in the other.
        return interiorProbes(a).any { inside(it, b) } || interiorProbes(b).any { inside(it, a) }
    }

    private fun crossing(a: PlanRoom, b: PlanRoom): Boolean {
        for (i in a.points.indices) for (j in b.points.indices) {
            if (properCross(a.points[i], a.points[(i + 1) % a.points.size], b.points[j], b.points[(j + 1) % b.points.size])) return true
        }
        return false
    }

    private fun cross(o: Pt, a: Pt, b: Pt): Long =
        (a.x - o.x).toLong() * (b.y - o.y) - (a.y - o.y).toLong() * (b.x - o.x)

    private fun properCross(p1: Pt, p2: Pt, p3: Pt, p4: Pt): Boolean {
        val d1 = cross(p3, p4, p1)
        val d2 = cross(p3, p4, p2)
        val d3 = cross(p1, p2, p3)
        val d4 = cross(p1, p2, p4)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /** Points just inside each edge midpoint and vertex, nudged toward the polygon interior (half-inch scale). */
    private fun interiorProbes(r: PlanRoom): List<Pair<Double, Double>> {
        val sign = if (r.signedArea2() >= 0) 1.0 else -1.0
        val n = r.points.size
        val probes = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until n) {
            val a = r.points[i]
            val b = r.points[(i + 1) % n]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val len = kotlin.math.hypot(dx, dy)
            if (len == 0.0) continue
            // Interior lies to the right of travel for positive (screen) area.
            val nx = -dy / len * sign
            val ny = dx / len * sign
            for (t in doubleArrayOf(0.25, 0.5, 0.75)) {
                probes += (a.x + dx * t + nx * 0.5) to (a.y + dy * t + ny * 0.5)
            }
        }
        return probes
    }

    private fun inside(p: Pair<Double, Double>, r: PlanRoom): Boolean {
        var result = false
        val n = r.points.size
        var j = n - 1
        for (i in 0 until n) {
            val pi = r.points[i]
            val pj = r.points[j]
            if ((pi.y > p.second) != (pj.y > p.second) &&
                p.first < (pj.x - pi.x) * (p.second - pi.y) / (pj.y - pi.y).toDouble() + pi.x
            ) result = !result
            j = i
        }
        return result
    }
}
