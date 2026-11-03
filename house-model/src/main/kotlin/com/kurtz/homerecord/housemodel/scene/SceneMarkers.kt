package com.kurtz.homerecord.housemodel.scene

import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.walls.WallKey
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** What a 3D marker opens when tapped (AC-C23). */
sealed interface MarkerTarget {
    data class OverlayMark(val overlayId: String) : MarkerTarget
    data class ItemMark(val itemId: String) : MarkerTarget
}

/** An installed item with a spot on the plan (AC-C30 position): plan inches, on its room's floor. */
data class ItemSpot(val id: String, val roomId: String, val planXIn: Int, val planYIn: Int, val name: String = "")

/**
 * One overlay or item placed in the 3D scene (AC-C23), in world meters like the mesh. A run has two or more [points],
 * a spot one. [facing] is the direction the marker is seen from: into the room for a wall, up for the floor. [layer] is
 * the blueprint layer it draws on (null for studs, blocking and other marks, which are always shown, as on the plan).
 */
data class SceneMarker(
    val target: MarkerTarget,
    val roomId: String,
    val layer: BlueprintLayer?,
    val overlayKind: OverlayKind?,
    val points: List<ScenePoint>,
    val facing: ScenePoint,
    val onWall: Boolean,
    /** What the marker is called: the overlay's label (blank when it has none) or the item's name. */
    val label: String = "",
) {
    val isRun: Boolean get() = points.size >= 2
}

/**
 * Places overlays and item markers on the wall or floor they belong to in a [FloorScene] (AC-C23). A wall overlay's
 * points are in wall space (x 0..1 left to right seen from inside the room, y 0 at the ceiling to 1 at the floor) and
 * land on the wall's inside face exactly where the mesh puts the wall photo: along the face set in by half the wall
 * thickness, the left end chosen by [com.kurtz.homerecord.housemodel.plan.WallWinding]. A plan overlay or an item spot
 * sits just above its room's floor. Rooms not solid in the scene (faded floors below) get no markers.
 */
object SceneMarkers {
    /** How far a marker stands off its surface so it never fights the wall or floor for depth, in meters. */
    const val LIFT_M = 0.01f

    fun place(
        scene: FloorScene,
        overlays: List<Overlay>,
        /** Each wall id's room and plan edge, for wall overlays. */
        walls: Map<String, WallKey>,
        items: List<ItemSpot> = emptyList(),
        wallThicknessIn: Double = PlanMeshBuilder.DEFAULT_WALL_THICKNESS_IN,
    ): List<SceneMarker> {
        val out = ArrayList<SceneMarker>()
        for (o in overlays) {
            if (o.points.isEmpty()) continue
            val target = MarkerTarget.OverlayMark(o.id)
            val wallId = o.wallId
            if (wallId != null) {
                val key = walls[wallId] ?: continue
                val room = scene.rooms[key.roomId] ?: continue
                val face = WallFaceFrame.of(room, key.edgeIndex, wallThicknessIn) ?: continue
                out += SceneMarker(target, room.id, o.kind.layer, o.kind, o.points.map { face.at(it.x, it.y) }, face.inward, onWall = true, label = o.label)
            } else {
                val room = o.roomId?.let { scene.rooms[it] } ?: continue
                val pts = o.points.map { floorPoint(scene, room, it.x, it.y) }
                out += SceneMarker(target, room.id, o.kind.layer, o.kind, pts, UP, onWall = false, label = o.label)
            }
        }
        for (item in items) {
            val room = scene.rooms[item.roomId] ?: continue
            val at = floorPoint(scene, room, item.planXIn.toFloat(), item.planYIn.toFloat())
            out += SceneMarker(MarkerTarget.ItemMark(item.id), room.id, BlueprintLayer.ITEMS, null, listOf(at), UP, onWall = false, label = item.name)
        }
        return out
    }

    /** The markers shown with [layers] on, the same rule as the blueprint: a marker on no layer always shows. */
    fun visible(markers: List<SceneMarker>, layers: LayerSet): List<SceneMarker> =
        markers.filter { m -> m.layer?.let { it in layers } ?: true }

    private fun floorPoint(scene: FloorScene, room: SceneRoom, xIn: Float, yIn: Float) = ScenePoint(
        (xIn + scene.mesh.offsetXIn) * M_PER_IN,
        room.floorY + LIFT_M,
        (yIn + scene.mesh.offsetZIn) * M_PER_IN,
    )

    private val UP = ScenePoint(0f, 1f, 0f)
    internal const val M_PER_IN = 0.0254f
}

/**
 * A room edge's inside wall face in world meters, as [PlanMeshBuilder] builds it: from [start] along [dir] for [length],
 * [height] tall from [floorY], facing [inward]; [backward] when wall x runs from the far corner.
 */
internal class WallFaceFrame(
    private val start: Pair<Float, Float>,
    private val dir: Pair<Float, Float>,
    private val length: Float,
    private val floorY: Float,
    private val height: Float,
    private val backward: Boolean,
    val inward: ScenePoint,
) {
    /** The world point at wall-space ([x], [y]), lifted off the face toward the room. */
    fun at(x: Float, y: Float): ScenePoint {
        val along = (if (backward) 1f - x else x).coerceIn(0f, 1f) * length
        val down = y.coerceIn(0f, 1f)
        return ScenePoint(
            start.first + dir.first * along + inward.x * SceneMarkers.LIFT_M,
            floorY + (1f - down) * height,
            start.second + dir.second * along + inward.z * SceneMarkers.LIFT_M,
        )
    }

    companion object {
        private const val MITER_LIMIT = 4f
        private const val PARALLEL_EPS = 1e-6f

        fun of(room: SceneRoom, edge: Int, wallThicknessIn: Double): WallFaceFrame? {
            val pts = room.footprint
            val n = pts.size
            if (n < 3 || edge !in 0 until n) return null
            var area2 = 0f
            for (i in 0 until n) {
                val a = pts[i]
                val b = pts[(i + 1) % n]
                area2 += a.first * b.second - b.first * a.second
            }
            if (area2 == 0f) return null
            val sign = if (area2 > 0f) 1f else -1f
            val inward = List(n) { i ->
                val a = pts[i]
                val b = pts[(i + 1) % n]
                val dx = b.first - a.first
                val dz = b.second - a.second
                val len = sqrt(dx * dx + dz * dz)
                if (len == 0f) 0f to 0f else (-dz / len * sign) to (dx / len * sign)
            }
            val half = (wallThicknessIn / 2 * SceneMarkers.M_PER_IN).toFloat()
            fun inset(i: Int): Pair<Float, Float> {
                val pi = (i - 1 + n) % n
                val n1 = inward[pi].takeUnless { it.isZero() } ?: inward[i]
                val n2 = inward[i].takeUnless { it.isZero() } ?: n1
                val det = n1.first * n2.second - n1.second * n2.first
                val p = pts[i]
                if (abs(det) < PARALLEL_EPS) return (p.first + n2.first * half) to (p.second + n2.second * half)
                var sx = (half * n2.second - half * n1.second) / det
                var sz = (n1.first * half - n2.first * half) / det
                val l = sqrt(sx * sx + sz * sz)
                val limit = MITER_LIMIT * half
                if (l > limit) {
                    sx = sx / l * limit
                    sz = sz / l * limit
                }
                return (p.first + sx) to (p.second + sz)
            }
            val a = pts[edge]
            val b = pts[(edge + 1) % n]
            val ex = b.first - a.first
            val ez = b.second - a.second
            val len = sqrt(ex * ex + ez * ez)
            if (len == 0f) return null
            val dir = (ex / len) to (ez / len)
            val s = inset(edge)
            val e = inset((edge + 1) % n)
            val s0 = (s.first - a.first) * dir.first + (s.second - a.second) * dir.second
            val s1 = (e.first - a.first) * dir.first + (e.second - a.second) * dir.second
            val minHeight = PlanMeshBuilder.MIN_WALL_HEIGHT_IN * SceneMarkers.M_PER_IN
            return WallFaceFrame(
                start = s,
                dir = dir,
                length = max(0f, s1 - s0),
                floorY = room.floorY,
                height = max(room.ceilingY - room.floorY, minHeight),
                backward = sign < 0f,
                inward = ScenePoint(inward[edge].first, 0f, inward[edge].second),
            )
        }

        private fun Pair<Float, Float>.isZero() = first == 0f && second == 0f
    }
}
