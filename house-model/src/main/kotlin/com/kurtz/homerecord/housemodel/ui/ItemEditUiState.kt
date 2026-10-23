package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.housemodel.RoomChoice
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemField

/** Which picker sheet the item form has open. */
enum class ItemPicker { SUBTYPE, ROOM }

data class ItemEditUiState(
    val loading: Boolean = true,
    val draft: ItemDraft = ItemDraft(),
    val rooms: List<RoomChoice> = emptyList(),
    val dirty: Boolean = false,
    val showErrors: Boolean = false,
    val picker: ItemPicker? = null,
    val saving: Boolean = false,
    /** Set once the item is stored; the route closes the form. */
    val savedItemId: String? = null,
    val missing: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == null
    val errors: Set<ItemField> get() = if (showErrors) draft.errors() else emptySet()
}

sealed interface ItemEditEvent {
    data class Change(val draft: ItemDraft) : ItemEditEvent
    data class PickSubtype(val subtypeId: String) : ItemEditEvent
    data class PickRoom(val roomId: String) : ItemEditEvent
    data class OpenPicker(val picker: ItemPicker) : ItemEditEvent
    data object ClosePicker : ItemEditEvent
    data object Save : ItemEditEvent
}
