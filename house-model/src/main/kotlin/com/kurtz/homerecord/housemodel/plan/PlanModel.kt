package com.kurtz.homerecord.housemodel.plan

import kotlin.math.abs

/** A point on a floor plan, in whole inches. */
data class Pt(val x: Int, val y: Int)

data class PlanRoom(
    val id: String,
    val name: String,
    val kind: String = "OTHER",
    val points: List<Pt>,
) {
    val minX get() = points.minOf { it.x }
    val maxX get() = points.maxOf { it.x }
    val minY get() = points.minOf { it.y }
    val maxY get() = points.maxOf { it.y }
    val widthIn get() = maxX - minX
    val lengthIn get() = maxY - minY

    fun moved(dx: Int, dy: Int) = copy(points = points.map { Pt(it.x + dx, it.y + dy) })

    /** Replaces one corner; ignored when [index] is out of range. */
    fun withPoint(index: Int, p: Pt): PlanRoom =
        if (index !in points.indices) this else copy(points = points.toMutableList().also { it[index] = p })

    /** Inserts a corner on edge [edgeIndex] (edge i runs from point i to i+1). */
    fun withCornerOnEdge(edgeIndex: Int, p: Pt): PlanRoom =
        if (edgeIndex !in points.indices) this
        else copy(points = points.toMutableList().also { it.add(edgeIndex + 1, p) })

    /** Deletes a corner; a room keeps at least three. */
    fun withoutCorner(index: Int): PlanRoom =
        if (points.size <= 3 || index !in points.indices) this
        else copy(points = points.filterIndexed { i, _ -> i != index })

    /** Resizes an axis-aligned room by moving one side; other shapes are returned unchanged. */
    fun resizedEdge(edgeIndex: Int, newCoordinate: Int): PlanRoom {
        if (edgeIndex !in points.indices) return this
        val a = points[edgeIndex]
        val b = points[(edgeIndex + 1) % points.size]
        return when {
            a.y == b.y -> copy(points = points.map { if (it.y == a.y) it.copy(y = newCoordinate) else it })
            a.x == b.x -> copy(points = points.map { if (it.x == a.x) it.copy(x = newCoordinate) else it })
            else -> this
        }
    }

    /** True when no two edges that are not neighbors cross or touch (a usable outline). */
    fun isSimple(): Boolean {
        val n = points.size
        if (n < 3) return false
        for (i in 0 until n) for (j in i + 1 until n) {
            val adjacent = j == i + 1 || (i == 0 && j == n - 1)
            if (adjacent) continue
            if (segmentsIntersect(points[i], points[(i + 1) % n], points[j], points[(j + 1) % n])) return false
        }
        return signedArea2() != 0L
    }

    /** Shoelace area in square inches. */
    fun areaSqIn(): Long = abs(signedArea2()) / 2

    internal fun signedArea2(): Long {
        var sum = 0L
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x.toLong() * b.y - b.x.toLong() * a.y
        }
        return sum
    }

    companion object {
        fun rectangle(id: String, name: String, kind: String, left: Int, top: Int, widthIn: Int, lengthIn: Int) =
            PlanRoom(
                id, name, kind,
                listOf(Pt(left, top), Pt(left + widthIn, top), Pt(left + widthIn, top + lengthIn), Pt(left, top + lengthIn)),
            )
    }
}

private fun orient(a: Pt, b: Pt, c: Pt): Int {
    val v = (b.x - a.x).toLong() * (c.y - a.y) - (b.y - a.y).toLong() * (c.x - a.x)
    return if (v > 0) 1 else if (v < 0) -1 else 0
}

private fun onSegment(a: Pt, b: Pt, c: Pt) =
    c.x in minOf(a.x, b.x)..maxOf(a.x, b.x) && c.y in minOf(a.y, b.y)..maxOf(a.y, b.y)

internal fun segmentsIntersect(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
    val o1 = orient(a, b, c)
    val o2 = orient(a, b, d)
    val o3 = orient(c, d, a)
    val o4 = orient(c, d, b)
    if (o1 != o2 && o3 != o4) return true
    return (o1 == 0 && onSegment(a, b, c)) || (o2 == 0 && onSegment(a, b, d)) ||
        (o3 == 0 && onSegment(c, d, a)) || (o4 == 0 && onSegment(c, d, b))
}

data class PlanModel(val rooms: List<PlanRoom> = emptyList()) {
    fun room(id: String) = rooms.firstOrNull { it.id == id }
    fun add(room: PlanRoom) = copy(rooms = rooms + room)
    fun update(room: PlanRoom) = copy(rooms = rooms.map { if (it.id == room.id) room else it })
    fun remove(id: String) = copy(rooms = rooms.filterNot { it.id == id })
}
