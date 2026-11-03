package com.kurtz.homerecord.housemodel.items

import com.kurtz.homerecord.housemodel.blueprint.RoomHitTester
import com.kurtz.homerecord.housemodel.overlays.PlanOverlayPlacer
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.WallWinding
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Where an item sits on the plan (AC-C30 position): the room it is in and its spot in plan inches. */
data class ItemPlace(val roomId: String, val xIn: Int, val yIn: Int)

/**
 * Turns a tap on a floor's plan into an item's spot (AC-C30 position). An item is in the room it is placed in, so a tap
 * inside a room (the smallest one, as the blueprint picks rooms) puts it there; a tap outside every room lands in the
 * nearest room, [INSET_IN] inside its nearest wall, so the marker never sits on a wall line or outside the house.
 */
object ItemPlacement {
    /** How far inside the wall a tap outside the rooms is moved, in inches. */
    const val INSET_IN = 6

    /** The spot a tap at ([xIn], [yIn]) gives, or null when the floor has no drawn rooms. */
    fun place(rooms: List<PlanRoom>, xIn: Int, yIn: Int): ItemPlace? {
        val shaped = rooms.filter { it.points.size >= 3 }
        val x = xIn.toFloat()
        val y = yIn.toFloat()
        RoomHitTester.roomAt(shaped, x, y)?.let { return ItemPlace(it.id, xIn, yIn) }
        val room = shaped.minByOrNull { PlanOverlayPlacer.distanceToWalls(it, x, y) } ?: return null
        val (px, py) = inside(room, x, y)
        return ItemPlace(room.id, px.roundToInt(), py.roundToInt())
    }

    /**
     * The plan spot of an item hung on wall [edge] of [room] at wall-space x [wallX] (AC-C30 position): the point that
     * far along the wall seen from inside the room ([WallWinding]), [INSET_IN] inside it, so the blueprint draws the
     * item against its wall. Null when the room has no such wall.
     */
    fun onWall(room: PlanRoom, edge: Int, wallX: Float): ItemPlace? {
        val pts = room.points
        if (pts.size < 3 || edge !in pts.indices) return null
        val a = pts[edge]
        val b = pts[(edge + 1) % pts.size]
        val dx = (b.x - a.x).toFloat()
        val dy = (b.y - a.y).toFloat()
        val len = hypot(dx, dy)
        if (len == 0f) return null
        val t = WallWinding.along(wallX.coerceIn(0f, 1f), WallWinding.runsBackward(pts))
        val px = a.x + t * dx
        val py = a.y + t * dy
        // The wall's inward normal: the side a step off the wall's middle lands inside the room.
        var nx = -dy / len
        var ny = dx / len
        if (!RoomHitTester.contains(room, a.x + dx / 2 + nx, a.y + dy / 2 + ny)) {
            nx = -nx
            ny = -ny
        }
        val (cx, cy) = nudgeFromCorner(room, px + nx * INSET_IN, py + ny * INSET_IN)
        return ItemPlace(room.id, cx.roundToInt(), cy.roundToInt())
    }

    /** The spot of an item already placed in one of [rooms], or null when it is not on this floor. */
    fun onFloor(rooms: List<PlanRoom>, roomId: String?, xIn: Int?, yIn: Int?): ItemPlace? {
        if (roomId == null || xIn == null || yIn == null) return null
        return if (rooms.any { it.id == roomId }) ItemPlace(roomId, xIn, yIn) else null
    }

    /** The point [INSET_IN] inside [room] nearest to ([x], [y]), which lies outside it. */
    private fun inside(room: PlanRoom, x: Float, y: Float): Pair<Float, Float> {
        var best: Triple<Float, Float, Int>? = null
        var bestDist = Float.MAX_VALUE
        for (i in room.points.indices) {
            val a = room.points[i]
            val b = room.points[(i + 1) % room.points.size]
            val dx = (b.x - a.x).toFloat()
            val dy = (b.y - a.y).toFloat()
            val len2 = dx * dx + dy * dy
            if (len2 == 0f) continue
            val t = (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0f, 1f)
            val px = a.x + t * dx
            val py = a.y + t * dy
            val d = hypot(x - px, y - py)
            if (d < bestDist) {
                bestDist = d
                best = Triple(px, py, i)
            }
        }
        val (px, py, i) = best ?: return x to y
        val a = room.points[i]
        val b = room.points[(i + 1) % room.points.size]
        val len = hypot((b.x - a.x).toFloat(), (b.y - a.y).toFloat())
        val nx = -(b.y - a.y) / len
        val ny = (b.x - a.x) / len
        // The wall's two sides: the one inside the room wins; at a corner step toward the room's middle as well.
        for (sign in floatArrayOf(1f, -1f)) {
            val cx = px + sign * nx * INSET_IN
            val cy = py + sign * ny * INSET_IN
            if (RoomHitTester.contains(room, cx, cy)) {
                return nudgeFromCorner(room, cx, cy)
            }
        }
        val mx = (room.points.minOf { it.x } + room.points.maxOf { it.x }) / 2f
        val my = (room.points.minOf { it.y } + room.points.maxOf { it.y }) / 2f
        return if (RoomHitTester.contains(room, mx, my)) mx to my else px to py
    }

    /** A point inside the room that is still within [INSET_IN] of another wall (a corner) is moved off that wall too. */
    private fun nudgeFromCorner(room: PlanRoom, x: Float, y: Float): Pair<Float, Float> {
        var cx = x
        var cy = y
        for (i in room.points.indices) {
            val a = room.points[i]
            val b = room.points[(i + 1) % room.points.size]
            val dx = (b.x - a.x).toFloat()
            val dy = (b.y - a.y).toFloat()
            val len2 = dx * dx + dy * dy
            if (len2 == 0f) continue
            val t = (((cx - a.x) * dx + (cy - a.y) * dy) / len2).coerceIn(0f, 1f)
            val d = hypot(cx - (a.x + t * dx), cy - (a.y + t * dy))
            if (d >= INSET_IN - 0.01f) continue
            val len = kotlin.math.sqrt(len2)
            var nx = -dy / len
            var ny = dx / len
            // The wall's inward side: a step off its middle that lands inside the room.
            if (!RoomHitTester.contains(room, a.x + dx / 2 + nx, a.y + dy / 2 + ny)) {
                nx = -nx
                ny = -ny
            }
            val px = cx + nx * (INSET_IN - d)
            val py = cy + ny * (INSET_IN - d)
            if (RoomHitTester.contains(room, px, py)) {
                cx = px
                cy = py
            }
        }
        return cx to cy
    }
}
