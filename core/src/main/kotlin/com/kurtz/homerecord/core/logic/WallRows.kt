package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Wall

/** One selectable wall in FL-BehindWall; [wallId] is null for a standard wall with no row yet. */
data class WallRow(val wallId: String?, val label: String, val count: Int)

/** AC-63/AC-65: standard N/E/S/W walls plus saved custom walls, each with its behind-wall photo count. */
object WallRows {
    fun build(standardLabels: List<String>, walls: List<Wall>, photos: List<Photo>): List<WallRow> {
        val counts = photos.filter { it.stage == PhotoStage.BEHIND_WALL && it.wallId != null }
            .groupingBy { it.wallId }.eachCount()
        val used = mutableSetOf<String>()
        val standard = standardLabels.map { label ->
            val wall = walls.firstOrNull { it.label.equals(label, ignoreCase = true) }
            wall?.let { used += it.id }
            WallRow(wall?.id, label, wall?.let { counts[it.id] } ?: 0)
        }
        val custom = walls.filter { it.id !in used }.map { WallRow(it.id, it.label, counts[it.id] ?: 0) }
        return standard + custom
    }
}
