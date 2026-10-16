package com.kurtz.homerecord.housemanual.equipment

import com.kurtz.homerecord.core.logic.EquipmentRule
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Room

data class EquipmentEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val category: EquipmentCategory? = null,
    val roomId: String? = null,
    val make: String = "",
    val model: String = "",
    val serial: String = "",
    val manufacturedOn: String = "",
    val installedOn: String = "",
    val warrantyEnds: String = "",
    val notes: String = "",
    val labelPhotoPath: String? = null,
    val rooms: List<Room> = emptyList(),
    val confirmingDelete: Boolean = false,
) {
    val manufacturedOnValid get() = EquipmentRule.isValidDate(manufacturedOn)
    val installedOnValid get() = EquipmentRule.isValidDate(installedOn)
    val warrantyEndsValid get() = EquipmentRule.isValidDate(warrantyEnds)
    val canSave: Boolean
        get() = EquipmentRule.canSave(category) && manufacturedOnValid && installedOnValid && warrantyEndsValid
}

sealed interface EquipmentEditEvent {
    data class CategoryChanged(val category: EquipmentCategory) : EquipmentEditEvent
    data class RoomChanged(val roomId: String?) : EquipmentEditEvent
    data class MakeChanged(val value: String) : EquipmentEditEvent
    data class ModelChanged(val value: String) : EquipmentEditEvent
    data class SerialChanged(val value: String) : EquipmentEditEvent
    data class ManufacturedOnChanged(val value: String) : EquipmentEditEvent
    data class InstalledOnChanged(val value: String) : EquipmentEditEvent
    data class WarrantyEndsChanged(val value: String) : EquipmentEditEvent
    data class NotesChanged(val value: String) : EquipmentEditEvent
    data object TakeLabelPhoto : EquipmentEditEvent
    data object RemoveLabelPhoto : EquipmentEditEvent
    data object Save : EquipmentEditEvent
    data object AskDelete : EquipmentEditEvent
    data object CancelDelete : EquipmentEditEvent
    data object ConfirmDelete : EquipmentEditEvent
}
