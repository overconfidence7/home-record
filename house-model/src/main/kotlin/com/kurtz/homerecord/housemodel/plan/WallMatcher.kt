package com.kurtz.homerecord.housemodel.plan

import kotlin.math.abs

/** A saved wall: its id and the edge it sat on in the room's previous outline. */
data class SavedWall(val id: String, val edgeIndex: Int)

/** Which saved wall each edge of the new outline keeps, and the saved walls whose edge is gone. */
data class WallMatch(val wallOfEdge: Map<Int, String>, val removed: List<String>)

/**
 * Keeps a wall's id (and so its photos and overlays, AC-C17, AC-C27) when its room is reshaped, by matching edges of
 * the old and new outline by their endpoints rather than by position in the corner list (cr:wall-identity).
 *
 * An edge whose corners did not move keeps its wall. Otherwise a wall goes to the new edge running the same way along
 * an overlapping stretch whose corners moved least: dragging a side keeps that side's wall and the two beside it, and
 * adding a corner keeps the wall on the part of the split edge nearest its old corners. A room moved across the plan
 * is compared as if it had not moved. Walls left without an edge (a corner was removed) are returned in
 * [WallMatch.removed]; edges left without a wall get a new one.
 */
class WallMatcher {
    fun match(oldOutline: List<Pt>, saved: List<SavedWall>, newPoints: List<Pt>): WallMatch {
        val oldPoints = aligned(oldOutline, newPoints)
        val oldEdges = saved.filter { it.edgeIndex in oldPoints.indices }.distinctBy { it.edgeIndex }
            .map { Edge(it.id, oldPoints, it.edgeIndex) }
        val unusable = saved.map { it.id } - oldEdges.map { it.id }.toSet()
        if (newPoints.size < 2) return WallMatch(emptyMap(), saved.map { it.id })
        // A room whose corners now run the other way keeps its walls: compare the old edges reversed.
        val flipped = oldPoints.size >= 3 && newPoints.size >= 3 && signedArea2(oldPoints) * signedArea2(newPoints) < 0
        val newEdges = newPoints.indices.map { Edge("", newPoints, it) }

        val candidates = mutableListOf<Triple<Long, Int, String>>()
        oldEdges.forEach { old ->
            val (oa, ob) = if (flipped) old.b to old.a else old.a to old.b
            newEdges.forEachIndexed { i, new ->
                if (!sameDirection(oa, ob, new.a, new.b) || !overlaps(oa, ob, new.a, new.b)) return@forEachIndexed
                candidates += Triple(dist(oa, new.a) + dist(ob, new.b), i, old.id)
            }
        }
        val wallOfEdge = mutableMapOf<Int, String>()
        val used = mutableSetOf<String>()
        candidates.sortedWith(compareBy({ it.first }, { it.second })).forEach { (_, edge, id) ->
            if (edge in wallOfEdge || id in used) return@forEach
            wallOfEdge[edge] = id
            used += id
        }
        return WallMatch(wallOfEdge, unusable + oldEdges.map { it.id }.filter { it !in used })
    }

    /** The old outline shifted by the room's move, when lining up the corner nearest the origin explains more edges. */
    private fun aligned(old: List<Pt>, new: List<Pt>): List<Pt> {
        if (old.isEmpty() || new.isEmpty()) return old
        val dx = new.minOf { it.x } - old.minOf { it.x }
        val dy = new.minOf { it.y } - old.minOf { it.y }
        if (dx == 0 && dy == 0) return old
        val moved = old.map { Pt(it.x + dx, it.y + dy) }
        return if (unmovedEdges(moved, new) > unmovedEdges(old, new)) moved else old
    }

    private fun unmovedEdges(old: List<Pt>, new: List<Pt>): Int {
        val edges = new.indices.map { setOf(new[it], new[(it + 1) % new.size]) }.toSet()
        return old.indices.count { setOf(old[it], old[(it + 1) % old.size]) in edges }
    }

    private class Edge(val id: String, points: List<Pt>, index: Int) {
        val a = points[index]
        val b = points[(index + 1) % points.size]
    }

    private fun dist(p: Pt, q: Pt): Long = abs(p.x - q.x).toLong() + abs(p.y - q.y)

    /** Parallel and pointing the same way (cross product zero, dot product positive). */
    private fun sameDirection(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
        val ux = (b.x - a.x).toLong()
        val uy = (b.y - a.y).toLong()
        val vx = (d.x - c.x).toLong()
        val vy = (d.y - c.y).toLong()
        return ux * vy - uy * vx == 0L && ux * vx + uy * vy > 0
    }

    /** The two edges cover some common stretch when projected on the old edge's direction. */
    private fun overlaps(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
        val ux = (b.x - a.x).toLong()
        val uy = (b.y - a.y).toLong()
        fun along(p: Pt) = (p.x - a.x) * ux + (p.y - a.y) * uy
        val lo = maxOf(0L, minOf(along(c), along(d)))
        val hi = minOf(ux * ux + uy * uy, maxOf(along(c), along(d)))
        return hi > lo
    }

    private fun signedArea2(points: List<Pt>): Long = points.indices.sumOf { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        a.x.toLong() * b.y - b.x.toLong() * a.y
    }
}
