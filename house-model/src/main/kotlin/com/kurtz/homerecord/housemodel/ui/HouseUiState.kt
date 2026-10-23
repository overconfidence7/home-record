package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.plan.PlanRoom

sealed interface HouseDialog {
    /** Long-press sheet for a room: Rename, Edit shape, Delete (AC-C14). */
    data class Menu(val room: PlanRoom) : HouseDialog
    data class Rename(val room: PlanRoom) : HouseDialog
    data class ConfirmDelete(val room: PlanRoom) : HouseDialog
}

data class HouseUiState(
    val loading: Boolean = true,
    val homeName: String = "",
    val summary: String = "",
    /** Bottom floor first, as stored. */
    val floors: List<FloorSummary> = emptyList(),
    val floorId: String? = null,
    val rooms: List<PlanRoom> = emptyList(),
    val layers: LayerSet = LayerSet(),
    val system: UnitSystem = UnitSystem.IMPERIAL,
    val dialog: HouseDialog? = null,
    val exporting: Boolean = false,
    /** Content URI of the finished PDF, waiting for the route to open the share sheet. */
    val shareUri: String? = null,
    val exportFailed: Boolean = false,
)

sealed interface HouseEvent {
    data class SelectFloor(val floorId: String) : HouseEvent
    data class ToggleLayer(val layer: BlueprintLayer) : HouseEvent
    data class LongPressRoom(val room: PlanRoom) : HouseEvent
    data object DismissDialog : HouseEvent
    data class StartRename(val room: PlanRoom) : HouseEvent
    data class Rename(val roomId: String, val name: String) : HouseEvent
    data class StartDelete(val room: PlanRoom) : HouseEvent
    data class ConfirmDelete(val roomId: String) : HouseEvent
    data object ExportPdf : HouseEvent
    data object ExportHandled : HouseEvent
}
