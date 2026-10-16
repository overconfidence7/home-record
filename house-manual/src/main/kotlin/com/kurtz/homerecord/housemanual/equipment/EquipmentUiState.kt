package com.kurtz.homerecord.housemanual.equipment

import com.kurtz.homerecord.core.logic.AgeAndWarrantyFormatter
import com.kurtz.homerecord.core.logic.WarrantyStatus
import com.kurtz.homerecord.core.model.Equipment
import java.time.LocalDate

data class EquipmentItem(val equipment: Equipment, val ageYears: Int?, val warranty: WarrantyStatus?)

data class EquipmentUiState(
    val loaded: Boolean = false,
    val items: List<EquipmentItem> = emptyList(),
    val roomNames: Map<String, String> = emptyMap(),
)

fun Equipment.toItem(today: LocalDate) = EquipmentItem(
    this,
    AgeAndWarrantyFormatter.ageYears(installedOn, today),
    AgeAndWarrantyFormatter.warranty(warrantyEnds, today),
)

sealed interface EquipmentEvent {
    data object Add : EquipmentEvent
    data class Open(val equipmentId: String) : EquipmentEvent
}
