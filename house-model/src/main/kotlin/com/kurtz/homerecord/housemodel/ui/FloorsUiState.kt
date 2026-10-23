package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.housemodel.FloorSummary

/** Which dialog the floors screen has open. */
sealed interface FloorsDialog {
    data object Add : FloorsDialog
    data class Edit(val floor: FloorSummary) : FloorsDialog
    data class Delete(val floor: FloorSummary) : FloorsDialog
}

data class FloorsUiState(
    val loading: Boolean = true,
    /** Bottom floor first, as stored. The screen lists them top floor first. */
    val floors: List<FloorSummary> = emptyList(),
    val dialog: FloorsDialog? = null,
)

sealed interface FloorsEvent {
    data object AddClicked : FloorsEvent
    data class EditClicked(val floor: FloorSummary) : FloorsEvent
    data class DeleteClicked(val floor: FloorSummary) : FloorsEvent
    data class MoveUp(val floor: FloorSummary) : FloorsEvent
    data class MoveDown(val floor: FloorSummary) : FloorsEvent
    data object DismissDialog : FloorsEvent
    data class ConfirmAdd(val name: String) : FloorsEvent
    data class ConfirmEdit(val floorId: String, val name: String, val ceilingHeightIn: Int) : FloorsEvent
    data class ConfirmDelete(val floorId: String) : FloorsEvent
}
