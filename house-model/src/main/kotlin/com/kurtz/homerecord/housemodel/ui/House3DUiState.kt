package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.scene.SceneRoom
import com.kurtz.homerecord.housemodel.walls.WallStage

/** HM-3D (AC-C20, AC-C21): one floor of the house (or all of them) as walls, textured with the chosen stage's wall photos. */
data class House3DUiState(
    val loading: Boolean = true,
    val homeName: String = "",
    /** Bottom floor first, as stored. */
    val floors: List<FloorSummary> = emptyList(),
    val floorId: String? = null,
    /** All floors drawn solid and pulled apart (AC-C21); otherwise [floorId] with the floors below faded. */
    val allFloors: Boolean = false,
    val stage: WallStage = WallStage.FINISHED,
    val scene: FloorScene? = null,
    /** The mesh could not be built from the plan. */
    val failed: Boolean = false,
    /** Bumped by Reset view; the renderer puts the camera back to the starting view whenever it changes. */
    val resetCount: Int = 0,
    /** The room the camera stands in (AC-C22), or null for the whole floor. */
    val roomId: String? = null,
    /** The screen opened straight into [roomId] ("View in 3D" on a room page), so Back leaves the screen. */
    val openedOnRoom: Boolean = false,
) {
    /** The room the camera stands in, once the scene holds it. */
    val room: SceneRoom? get() = roomId?.let { scene?.rooms?.get(it) }

    val floor: FloorSummary? get() = floors.firstOrNull { it.id == floorId }

    /** Walls with a photo at the chosen stage, out of all walls on the floor. */
    val photographedWalls: Int get() = scene?.picturesAt(stage)?.size ?: 0
}

sealed interface House3DEvent {
    data class SelectFloor(val floorId: String) : House3DEvent
    data class SelectStage(val stage: WallStage) : House3DEvent
    data object ShowAllFloors : House3DEvent
    data object ResetView : House3DEvent
    data object Retry : House3DEvent

    /** A room tapped in the 3D floor: stand inside it (AC-C22). */
    data class ViewRoom(val roomId: String) : House3DEvent

    /** Leave the room for the whole floor. */
    data object BackToFloor : House3DEvent
}
