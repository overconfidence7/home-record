package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.bundle.RecordBundle
import com.kurtz.homerecord.core.model.PhotoStage

/** AC-71: what FL-Handoff lists for a job's Home. */
data class HandoffCounts(
    val rooms: Int,
    val walls: Int,
    val behindWallPhotos: Int,
    val equipment: Int,
    val finishes: Int,
    val shutoffs: Int,
    val panels: Int,
) {
    companion object {
        /** Finishes are not stored yet (SPEC "Finish (M2)" has no screen), so the count is always 0. */
        fun of(bundle: RecordBundle) = HandoffCounts(
            rooms = bundle.rooms.size,
            walls = bundle.walls.size,
            behindWallPhotos = bundle.photos.count { it.stage == PhotoStage.BEHIND_WALL },
            equipment = bundle.equipment.size,
            finishes = 0,
            shutoffs = bundle.shutoffs.size,
            panels = bundle.panels.size,
        )
    }
}
