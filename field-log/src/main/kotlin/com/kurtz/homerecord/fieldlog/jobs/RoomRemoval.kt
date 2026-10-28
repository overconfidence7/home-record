package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.RoomUsage

/** What removing a room chip on FL-JobEdit takes with it; shown in a confirm before the chip goes (cr:fl-jobedit-room-delete). */
data class RoomRemoval(val name: String, val placed: Boolean, val photos: Int, val items: Int, val tasks: Int) {
    companion object {
        /** Null when the room can go at once: not saved yet, or unplaced with nothing filed under it. */
        fun of(name: String, room: RoomEntity?, usage: RoomUsage?): RoomRemoval? {
            if (room == null) return null
            val placed = room.polygonJson.filterNot(Char::isWhitespace).let { it.isNotEmpty() && it != "[]" }
            val u = usage ?: RoomUsage(0, 0, 0)
            if (!placed && u.photos == 0 && u.items == 0 && u.tasks == 0) return null
            return RoomRemoval(name, placed, u.photos, u.items, u.tasks)
        }
    }
}
