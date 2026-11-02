package com.kurtz.homerecord.housemodel.overlays

import com.kurtz.homerecord.housemodel.blueprint.RoomHitTester
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import kotlin.math.hypot

/**
 * Where an overlay drawn on the floor plan belongs (AC-C25). A plan overlay has no floor of its own, so it is filed under a
 * room: the smallest room its first point lies in, else the room whose walls are nearest that point (a gas line run just
 * outside the kitchen wall belongs to the kitchen). A floor's plan overlays are the ones filed under its rooms.
 */
object PlanOverlayPlacer {
    /** The room overlay [points] (plan inches) is filed under, or null when the floor has no rooms. */
    fun roomFor(rooms: List<PlanRoom>, points: List<OverlayPoint>): String? {
        val first = points.firstOrNull() ?: return null
        val shaped = rooms.filter { it.points.size >= 3 }
        RoomHitTester.roomAt(shaped, first.x, first.y)?.let { return it.id }
        return shaped.minByOrNull { distanceToWalls(it, first.x, first.y) }?.id
    }

    /** The plan overlays (no wall) filed under one of [rooms], in the order given. */
    fun onFloor(overlays: List<Overlay>, rooms: List<PlanRoom>): List<Overlay> {
        val ids = rooms.mapTo(HashSet()) { it.id }
        return overlays.filter { !it.onWall && it.roomId in ids }
    }

    /** Distance in inches from ([x], [y]) to the nearest of [room]'s walls. */
    fun distanceToWalls(room: PlanRoom, x: Float, y: Float): Float {
        if (room.points.isEmpty()) return Float.MAX_VALUE
        return room.points.indices.minOf { i ->
            val a = room.points[i]
            val b = room.points[(i + 1) % room.points.size]
            val dx = (b.x - a.x).toFloat()
            val dy = (b.y - a.y).toFloat()
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0f, 1f)
            hypot(x - (a.x + t * dx), y - (a.y + t * dy))
        }
    }
}
