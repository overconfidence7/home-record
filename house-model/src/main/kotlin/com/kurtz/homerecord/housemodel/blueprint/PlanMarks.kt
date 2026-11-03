package com.kurtz.homerecord.housemodel.blueprint

import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.WallWinding
import com.kurtz.homerecord.housemodel.scene.ItemSpot
import com.kurtz.homerecord.housemodel.scene.MarkerTarget
import com.kurtz.homerecord.housemodel.walls.WallKey
import kotlin.math.hypot

/**
 * A wall overlay or an item drawn on the blueprint (AC-C15), in plan inches. A wall overlay seen from above lies along its
 * wall: [points] sit on the wall line and [inward] (a unit vector, plan axes) says which side the room is, so the canvas
 * can set the mark just inside the wall where it reads beside the wall stroke. An item is one point at its plan spot,
 * [inward] null. [layer] is the blueprint layer it draws on (null for studs, blocking and other marks, always shown).
 */
data class PlanMark(
    val target: MarkerTarget,
    val roomId: String,
    val layer: BlueprintLayer?,
    val overlayKind: OverlayKind?,
    val points: List<OverlayPoint>,
    val inward: OverlayPoint? = null,
    /** The item's category icon name (`ItemCategory` icon), for item marks. */
    val iconName: String? = null,
    val label: String = "",
) {
    val isRun: Boolean get() = points.size >= 2
    val isItem: Boolean get() = target is MarkerTarget.ItemMark
}

/**
 * Places the floor's wall overlays and placed items on its blueprint (AC-C15). A wall overlay's points are in wall space
 * (x 0..1 left to right seen from inside the room, y 0 at the ceiling to 1 at the floor); from above only x shows, so each
 * point lands on the wall line at its x, the left end chosen by [WallWinding] exactly as the 3D faces and the photo
 * carriers do. A run that goes straight up the wall (a riser, a drop to an outlet) spans less than [MIN_RUN_IN] along the
 * wall and becomes a spot where it stands. Plan overlays are not here: they already have plan points and draw as they are.
 */
object PlanMarks {
    /** A wall run shorter than this along the wall, seen from above, is drawn as a spot (inches). */
    const val MIN_RUN_IN = 6f

    fun place(
        rooms: List<PlanRoom>,
        overlays: List<Overlay>,
        /** Each wall id's room and plan edge. */
        walls: Map<String, WallKey>,
        items: List<ItemSpot> = emptyList(),
    ): List<PlanMark> {
        val byId = rooms.associateBy { it.id }
        val out = ArrayList<PlanMark>()
        for (o in overlays) {
            val wallId = o.wallId ?: continue
            if (o.points.isEmpty()) continue
            val key = walls[wallId] ?: continue
            val room = byId[key.roomId] ?: continue
            val line = alongWall(room, key.edgeIndex, o.points) ?: continue
            out += PlanMark(MarkerTarget.OverlayMark(o.id), room.id, o.kind.layer, o.kind, line.first, line.second, label = o.label)
        }
        for (item in items) {
            if (item.roomId !in byId) continue
            out += PlanMark(
                MarkerTarget.ItemMark(item.id), item.roomId, BlueprintLayer.ITEMS, null,
                listOf(OverlayPoint(item.planXIn.toFloat(), item.planYIn.toFloat())), iconName = item.iconName, label = item.name,
            )
        }
        return out
    }

    /** The marks shown with [layers] on, the same rule as the 3D view: a mark on no layer always shows. */
    fun visible(marks: List<PlanMark>, layers: LayerSet): List<PlanMark> = marks.filter { m -> m.layer?.let { it in layers } ?: true }

    /**
     * Wall-space [points] laid on edge [edgeIndex] of [room] in plan inches, and the edge's inward unit normal; null when
     * the edge is not there or has no length.
     */
    fun alongWall(room: PlanRoom, edgeIndex: Int, points: List<OverlayPoint>): Pair<List<OverlayPoint>, OverlayPoint>? {
        val n = room.points.size
        if (n < 3 || edgeIndex !in 0 until n || points.isEmpty()) return null
        val a = room.points[edgeIndex]
        val b = room.points[(edgeIndex + 1) % n]
        val dx = (b.x - a.x).toFloat()
        val dy = (b.y - a.y).toFloat()
        val len = hypot(dx, dy)
        if (len == 0f) return null
        val backward = WallWinding.runsBackward(room.points)
        // The interior lies to the right of travel when the corners run clockwise on screen (y down), else to the left.
        val sign = if (backward) -1f else 1f
        // + 0f turns a negative zero into zero, so equal walls give equal normals.
        val inward = OverlayPoint(-dy / len * sign + 0f, dx / len * sign + 0f)
        val along = points.map { WallWinding.along(it.x.coerceIn(0f, 1f), backward) * len }
        val at = { d: Float -> OverlayPoint(a.x + dx / len * d, a.y + dy / len * d) }
        val span = along.max() - along.min()
        if (span < MIN_RUN_IN) return listOf(at(along.average().toFloat())) to inward
        // Up-and-down legs collapse onto the same spot; keep one point per change along the wall.
        val kept = ArrayList<Float>()
        for (d in along) if (kept.isEmpty() || kotlin.math.abs(kept.last() - d) >= 0.5f) kept += d
        return kept.map(at) to inward
    }
}
