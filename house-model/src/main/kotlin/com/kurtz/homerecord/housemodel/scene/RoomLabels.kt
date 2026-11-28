package com.kurtz.homerecord.housemodel.scene

/** A room name placed on the 3D view, [x] and [y] in pixels from the view's top left ([judge:hm-3d-rooms-unlabeled]). */
data class RoomLabel(val id: String, val name: String, val x: Float, val y: Float)

/**
 * Where each solid room's name goes on the 3D view (AC-C20): the middle of its footprint just above its floor, projected
 * by [toClip] (world point to clip-space x, y, z, w). A room behind the camera or off the view gets no label, so names
 * never pile up at an edge. With [size] given (a name's label width and height in pixels), a label that would overlap
 * one already placed is left out, like the blueprint's fit rule: rooms on higher floors go first (the floor on top in the
 * all-floors view), then rooms in plan order ([judge:hm-3d-labels-overlap]).
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
    ): List<RoomLabel> = place(rooms, widthPx, heightPx, null, toClip)

    fun place(
        rooms: Collection<SceneRoom>,
        widthPx: Int,
        heightPx: Int,
        size: ((name: String) -> Pair<Float, Float>)?,
        toClip: (x: Float, y: Float, z: Float) -> FloatArray,
    ): List<RoomLabel> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()
        val projected = project(rooms, widthPx, heightPx, toClip)
        if (size == null) return projected
        val floorOf = rooms.associate { it.id to it.floorY }
        val order = projected.withIndex().sortedWith(
            compareByDescending<IndexedValue<RoomLabel>> { floorOf[it.value.id] ?: 0f }.thenBy { it.index },
        )
        val kept = mutableListOf<Pair<IndexedValue<RoomLabel>, FloatArray>>()
        for (entry in order) {
            val (w, h) = size(entry.value.name)
            val box = floatArrayOf(entry.value.x - w / 2, entry.value.y - h / 2, entry.value.x + w / 2, entry.value.y + h / 2)
            if (kept.none { (_, b) -> box[0] < b[2] && b[0] < box[2] && box[1] < b[3] && b[1] < box[3] }) kept += entry to box
        }
        return kept.map { it.first }.sortedBy { it.index }.map { it.value }
    }

    private fun project(
        rooms: Collection<SceneRoom>,
        widthPx: Int,
        heightPx: Int,
        toClip: (x: Float, y: Float, z: Float) -> FloatArray,
    ): List<RoomLabel> {
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
