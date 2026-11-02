package com.kurtz.homerecord.housemodel.overlays

import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.housemodel.walls.WallPoint
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One overlay point: wall space 0..1 when the overlay is on a wall (AC-C27), plan inches when it is on the floor plan. */
data class OverlayPoint(val x: Float, val y: Float) {
    fun toWallPoint(): WallPoint = WallPoint(x, y)
}

fun WallPoint.toOverlayPoint(): OverlayPoint = OverlayPoint(x, y)

/** A pipe, wire, duct, box or other mark on a wall or the plan (AC-C25, AC-C26). */
data class Overlay(
    val id: String,
    val homeId: String,
    val roomId: String?,
    val wallId: String?,
    val kind: OverlayKind,
    val label: String,
    val note: String?,
    val depth: OverlayDepth,
    val points: List<OverlayPoint>,
    val stage: WallStage,
    val photoPath: String?,
    val itemId: String?,
    val updatedAt: Long,
) {
    /** On a wall (points in wall space) rather than on the floor plan (points in plan inches). */
    val onWall: Boolean get() = wallId != null

    /** Drawn as a run when it has two or more points, as a spot otherwise. */
    val shape: OverlayShape get() = if (points.size >= 2) OverlayShape.LINE else OverlayShape.POINT
}

fun OverlayEntity.toOverlay(): Overlay = Overlay(
    id = id,
    homeId = homeId,
    roomId = roomId,
    wallId = wallId,
    kind = OverlayKind.fromWire(kind),
    label = label,
    note = note,
    depth = OverlayDepth.fromStored(depth),
    points = OverlayPoints.decode(pointsJson),
    stage = WallStage.fromStored(stage),
    photoPath = photoPath,
    itemId = itemId,
    updatedAt = updatedAt,
)

/** The `[{"x":..,"y":..},...]` JSON in `overlay.pointsJson`. */
object OverlayPoints {
    fun encode(points: List<OverlayPoint>): String {
        val array = JSONArray()
        points.forEach { array.put(JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())) }
        return array.toString()
    }

    /** Damaged JSON or points read back as an empty list; points without numbers are skipped. */
    fun decode(json: String?): List<OverlayPoint> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val x = o.optDouble("x", Double.NaN)
                val y = o.optDouble("y", Double.NaN)
                if (x.isNaN() || y.isNaN()) null else OverlayPoint(x.toFloat(), y.toFloat())
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
