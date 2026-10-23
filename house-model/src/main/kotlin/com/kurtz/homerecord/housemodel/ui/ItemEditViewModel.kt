package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-ItemEdit (AC-C30): add a new item (or edit [itemId]). The draft survives process death through [SavedStateHandle],
 * stored as a flat list of strings because [ItemDraft] is not parcelable.
 */
class ItemEditViewModel(
    private val items: ItemRepository,
    private val homes: HomeRepository,
    private val homeId: String,
    private val itemId: String?,
    private val saved: SavedStateHandle,
    initialRoomId: String? = null,
) : ViewModel() {
    private var original = ItemDraft(roomId = initialRoomId)

    private val _state = MutableStateFlow(
        ItemEditUiState(draft = saved.get<ArrayList<String>>(KEY)?.let(::decode) ?: original),
    )
    val state: StateFlow<ItemEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val rooms = homes.roomChoices(homeId)
            val stored = itemId?.let { items.draftFor(it) }
            if (itemId != null && stored == null) {
                _state.update { it.copy(loading = false, rooms = rooms, missing = true) }
                return@launch
            }
            if (stored != null) original = stored
            _state.update {
                val draft = if (saved.contains(KEY)) it.draft else original
                it.copy(loading = false, rooms = rooms, draft = draft, dirty = draft != original)
            }
        }
    }

    fun onEvent(event: ItemEditEvent) {
        when (event) {
            is ItemEditEvent.Change -> setDraft(event.draft)
            is ItemEditEvent.PickSubtype -> {
                val draft = _state.value.draft
                val subtype = ItemCategory.subtype(event.subtypeId)
                // A blank name starts as the subtype's own, which the homeowner usually refines ("Basement furnace").
                val name = if (draft.name.isBlank() && subtype != null) subtype.label else draft.name
                setDraft(draft.copy(subtypeId = event.subtypeId, name = name))
                _state.update { it.copy(picker = null) }
            }
            is ItemEditEvent.PickRoom -> {
                setDraft(_state.value.draft.copy(roomId = event.roomId))
                _state.update { it.copy(picker = null) }
            }
            is ItemEditEvent.OpenPicker -> _state.update { it.copy(picker = event.picker) }
            ItemEditEvent.ClosePicker -> _state.update { it.copy(picker = null) }
            ItemEditEvent.Save -> save()
        }
    }

    private fun setDraft(draft: ItemDraft) {
        saved[KEY] = encode(draft)
        _state.update { it.copy(draft = draft, dirty = draft != original) }
    }

    private fun save() {
        val current = _state.value
        if (current.saving) return
        if (current.draft.errors().isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = items.save(homeId, current.draft)
            _state.update { it.copy(saving = false, savedItemId = id, dirty = false) }
        }
    }

    private companion object {
        const val KEY = "draft"

        fun encode(d: ItemDraft) = arrayListOf(
            d.id.orEmpty(), d.subtypeId.orEmpty(), d.roomId.orEmpty(), d.name, d.make, d.model, d.serial,
            d.manufacturedOn, d.purchasedOn, d.inServiceOn, d.price, d.vendor, d.manualUrl, d.notes,
        )

        fun decode(v: List<String>): ItemDraft? {
            if (v.size != 14) return null
            fun nul(i: Int) = v[i].ifEmpty { null }
            return ItemDraft(
                id = nul(0), subtypeId = nul(1), roomId = nul(2), name = v[3], make = v[4], model = v[5], serial = v[6],
                manufacturedOn = v[7], purchasedOn = v[8], inServiceOn = v[9], price = v[10], vendor = v[11],
                manualUrl = v[12], notes = v[13],
            )
        }
    }
}
