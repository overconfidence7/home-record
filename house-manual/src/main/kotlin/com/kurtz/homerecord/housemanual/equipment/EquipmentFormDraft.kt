package com.kurtz.homerecord.housemanual.equipment

import androidx.lifecycle.SavedStateHandle
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.repo.ConsumableDraft

/** Keeps the equipment form's input in a [SavedStateHandle] so it survives process death. */
object EquipmentFormDraft {
    private const val MARK = "draft"

    fun save(handle: SavedStateHandle, s: EquipmentEditUiState) {
        handle[MARK] = true
        handle["category"] = s.category?.name
        handle["roomId"] = s.roomId
        handle["make"] = s.make
        handle["model"] = s.model
        handle["serial"] = s.serial
        handle["manufacturedOn"] = s.manufacturedOn
        handle["installedOn"] = s.installedOn
        handle["warrantyEnds"] = s.warrantyEnds
        handle["notes"] = s.notes
        handle["remindMonths"] = s.remindMonths
        handle["pendingKind"] = s.pendingKind.name
        handle["pendingSpec"] = s.pendingSpec
        handle["consumableKinds"] = ArrayList(s.consumables.map { it.kind.name })
        handle["consumableSpecs"] = ArrayList(s.consumables.map { it.spec })
    }

    /** Overlays a saved draft on [base]; returns [base] unchanged when nothing was saved. */
    fun restore(handle: SavedStateHandle, base: EquipmentEditUiState): EquipmentEditUiState {
        if (handle.get<Boolean>(MARK) != true) return base
        val kinds = handle.get<ArrayList<String>>("consumableKinds").orEmpty()
        val specs = handle.get<ArrayList<String>>("consumableSpecs").orEmpty()
        val consumables = kinds.zip(specs).mapNotNull { (k, spec) ->
            ConsumableKind.entries.firstOrNull { it.name == k }?.let { ConsumableDraft(it, spec) }
        }
        return base.copy(
            category = handle.get<String>("category")?.let { n -> EquipmentCategory.entries.firstOrNull { it.name == n } } ?: base.category,
            roomId = handle.get<String>("roomId"),
            make = handle.get<String>("make") ?: base.make,
            model = handle.get<String>("model") ?: base.model,
            serial = handle.get<String>("serial") ?: base.serial,
            manufacturedOn = handle.get<String>("manufacturedOn") ?: base.manufacturedOn,
            installedOn = handle.get<String>("installedOn") ?: base.installedOn,
            warrantyEnds = handle.get<String>("warrantyEnds") ?: base.warrantyEnds,
            notes = handle.get<String>("notes") ?: base.notes,
            remindMonths = handle.get<String>("remindMonths") ?: base.remindMonths,
            pendingKind = handle.get<String>("pendingKind")?.let { n -> ConsumableKind.entries.firstOrNull { it.name == n } } ?: base.pendingKind,
            pendingSpec = handle.get<String>("pendingSpec") ?: base.pendingSpec,
            consumables = consumables,
        )
    }
}
