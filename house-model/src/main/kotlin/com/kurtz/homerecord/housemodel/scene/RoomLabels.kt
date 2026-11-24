package com.kurtz.homerecord.housemodel.scene

/** A room name placed on the 3D view, [x] and [y] in pixels from the view's top left ([judge:hm-3d-rooms-unlabeled]). */
data class RoomLabel(val id: String, val name: String, val x: Float, val y: Float)

/**
 * Where each solid room's name goes on the 3D view (AC-C20): the middle of its footprint just above its floor, projected
 * by [toClip] (world point to clip-space x, y, z, w). A room behind the camera or off the view gets no label, so names
 * never pile up at an edge.
 */
object RoomLabels {
    /** Above the floor, so the name sits over the room rather than on its floor line. */
    const val LIFT_M = 0.3f

    fun anchor(room: SceneRoom): ScenePoint? {
        if (room.footprint.isEmpty()) return null
        val x = room.footprint.sumOf { it.first.toDouble() }.toFloat() / room.footprint.size
        val z = room.footprint.sumOf { it.second.toDouble() }.toFloat() / room.footprint.size
        return ScenePoint(x, room.floorY + LIFT_M, z)
    }

    fun place(
        rooms: Collection<SceneRoom>,
        widthPx: Int,
        heightPx: Int,
        toClip: (x: Float, y: Float, z: Float) -> FloatArray,
    ): List<RoomLabel> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()
        return rooms.mapNotNull { room ->
            val name = room.name.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val a = anchor(room) ?: return@mapNotNull null
            val c = toClip(a.x, a.y, a.z)
            val w = c[3]
            if (w <= 1e-4f) return@mapNotNull null
            val nx = c[0] / w
            val ny = c[1] / w
            if (nx !in -1f..1f || ny !in -1f..1f) return@mapNotNull null
            RoomLabel(room.id, name, (nx + 1f) / 2f * widthPx, (1f - ny) / 2f * heightPx)
        }
    }
}
