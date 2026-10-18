package com.kurtz.homerecord.housemanual.equipment

import androidx.lifecycle.SavedStateHandle
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.repo.ConsumableDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentFormDraftTest {
    @Test fun restoresTypedInputAfterProcessDeath() {
        val handle = SavedStateHandle()
        val typed = EquipmentEditUiState(
            loaded = true, category = EquipmentCategory.FURNACE, make = "Carrier", model = "59TP6", notes = "attic",
            remindMonths = "6", pendingSpec = "16x25",
            consumables = listOf(ConsumableDraft(ConsumableKind.FILTER, "16x25x1 MERV 8")),
        )
        EquipmentFormDraft.save(handle, typed)

        val restored = EquipmentFormDraft.restore(SavedStateHandle(mapOf<String, Any?>().plus(handle.keys().associateWith { handle.get<Any>(it) })), EquipmentEditUiState(loaded = true))

        assertEquals("Carrier", restored.make)
        assertEquals("59TP6", restored.model)
        assertEquals(EquipmentCategory.FURNACE, restored.category)
        assertEquals("16x25", restored.pendingSpec)
        assertEquals(typed.consumables, restored.consumables)
    }

    @Test fun withoutADraftTheLoadedStateIsKept() {
        val base = EquipmentEditUiState(loaded = true, make = "Existing")
        assertEquals(base, EquipmentFormDraft.restore(SavedStateHandle(), base))
    }
}
