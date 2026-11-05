package com.kurtz.homerecord.housemanual.panel

import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.SlotOccupancy
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.RoomChoice

/** HM-Breaker (AC-C40): one breaker's label, amps, poles, type and what it feeds, on a full-screen form (rule 15). */
data class BreakerUiState(
    val loading: Boolean = true,
    /** The panel or the breaker is gone (deleted on another phone). */
    val missing: Boolean = false,
    val draft: BreakerDraft = BreakerDraft(),
    val initial: BreakerDraft = BreakerDraft(),
    val layout: PanelLayout = PanelLayout.NORTH_AMERICAN,
    val slotCount: Int = SlotOccupancy.DEFAULT_SLOTS,
    val rooms: List<RoomChoice> = emptyList(),
    /** Items of the home it can feed (the panels themselves left out). */
    val items: List<ItemRow> = emptyList(),
    /** Why the last Save could not place the breaker; cleared when poles change. */
    val placement: SlotPlacement? = null,
    val ampsError: Boolean = false,
    val pickingItems: Boolean = false,
    val confirmDelete: Boolean = false,
    /** Saved or deleted: the route closes the form. */
    val done: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == null
    val dirty: Boolean get() = draft != initial

    /** The slots the breaker fills as drafted ("12" or "12 and 14"); null when a two-pole would run off the panel. */
    val slots: List<Int>? get() = SlotOccupancy.slotsFor(layout, slotCount, draft.startSlot, draft.poles)

    val selectedItems: List<ItemRow> get() = draft.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
}

sealed interface BreakerEvent {
    data class Label(val text: String) : BreakerEvent
    data class Amps(val text: String) : BreakerEvent
    data class Poles(val poles: Int) : BreakerEvent
    data class Type(val type: BreakerType) : BreakerEvent
    data class ToggleRoom(val roomId: String) : BreakerEvent
    data class ToggleItem(val itemId: String) : BreakerEvent
    data object PickItems : BreakerEvent
    data object ClosePicker : BreakerEvent
    data object Save : BreakerEvent
    data object Delete : BreakerEvent
    data object ConfirmDelete : BreakerEvent
    data object CancelDelete : BreakerEvent
}
