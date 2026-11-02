package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.walls.WallStage

/** HM-3D (AC-C20): one floor of the house as walls, textured with the chosen stage's wall photos. */
data class House3DUiState(
    val loading: Boolean = true,
    val homeName: String = "",
    /** Bottom floor first, as stored. */
    val floors: List<FloorSummary> = emptyList(),
    val floorId: String? = null,
    val stage: WallStage = WallStage.FINISHED,
    val scene: FloorScene? = null,
    /** The mesh could not be built from the plan. */
    val failed: Boolean = false,
    /** Bumped by Reset view; the renderer puts the camera back to the starting view whenever it changes. */
    val resetCount: Int = 0,
) {
    val floor: FloorSummary? get() = floors.firstOrNull { it.id == floorId }

    /** Walls with a photo at the chosen stage, out of all walls on the floor. */
    val photographedWalls: Int get() = scene?.picturesAt(stage)?.size ?: 0
}

sealed interface House3DEvent {
    data class SelectFloor(val floorId: String) : House3DEvent
    data class SelectStage(val stage: WallStage) : House3DEvent
    data object ResetView : House3DEvent
    data object Retry : House3DEvent
}
