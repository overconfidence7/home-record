package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.BreakerField
import com.kurtz.homerecord.housemodel.panel.BreakerSave
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import com.kurtz.homerecord.housemodel.panel.isPanelSubtype
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HM-Breaker (AC-C40): edits the breaker [breakerId] of the panel item [itemId], or a new one starting at [slot].
 * The draft survives process death through [saved].
 */
class BreakerViewModel(
    private val repo: PanelRepository,
    private val itemId: String,
    private val slot: Int,
    private val breakerId: String?,
    homeId: String,
    items: Flow<List<ItemRow>>,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(BreakerUiState())
    val state: StateFlow<BreakerUiState> = _state.asStateFlow()
    private var panelId: String? = null

    init {
        viewModelScope.launch {
            val id = repo.ensurePanel(itemId)
            val panel = repo.panel(id)
            val initial = if (breakerId != null) repo.draftFor(breakerId) else BreakerDraft(startSlot = slot)
            if (panel == null || initial == null) {
                _state.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            panelId = id
            val draft = saved.get<String>(DRAFT)?.let { runCatching { json.decodeFromString(SavedDraft.serializer(), it).toDraft() }.getOrNull() } ?: initial
            _state.update { it.copy(loading = false, draft = draft, initial = initial, layout = panel.layout, slotCount = panel.slotCount) }
        }
        viewModelScope.launch {
            combine(repo.observeRooms(homeId), items) { r, i -> r to i.filterNot { isPanelSubtype(it.subtypeId) } }.collect { (r, i) ->
                _state.update { it.copy(rooms = r, items = i) }
            }
        }
    }

    fun onEvent(e: BreakerEvent) {
        when (e) {
            is BreakerEvent.Label -> edit { it.copy(label = e.text) }
            is BreakerEvent.Amps -> {
                edit { it.copy(amps = e.text.filter(Char::isDigit).take(3)) }
                _state.update { it.copy(ampsError = false) }
            }
            is BreakerEvent.Poles -> {
                edit { it.copy(poles = e.poles) }
                _state.update { it.copy(placement = null) }
            }
            is BreakerEvent.Type -> edit { it.copy(type = e.type) }
            is BreakerEvent.ToggleRoom -> edit { it.copy(roomIds = it.roomIds.toggle(e.roomId)) }
            is BreakerEvent.ToggleItem -> edit { it.copy(itemIds = it.itemIds.toggle(e.itemId)) }
            BreakerEvent.PickItems -> _state.update { it.copy(pickingItems = true) }
            BreakerEvent.ClosePicker -> _state.update { it.copy(pickingItems = false) }
            BreakerEvent.Save -> save()
            BreakerEvent.Delete -> _state.update { it.copy(confirmDelete = true) }
            BreakerEvent.CancelDelete -> _state.update { it.copy(confirmDelete = false) }
            BreakerEvent.ConfirmDelete -> delete()
        }
    }

    private fun edit(change: (BreakerDraft) -> BreakerDraft) {
        val next = change(_state.value.draft)
        _state.update { it.copy(draft = next) }
        saved[DRAFT] = json.encodeToString(SavedDraft.serializer(), SavedDraft.of(next))
    }

    private fun save() {
        val id = panelId ?: return
        viewModelScope.launch {
            when (val r = repo.saveBreaker(id, _state.value.draft)) {
                is BreakerSave.Saved -> _state.update { it.copy(done = true) }
                is BreakerSave.Invalid -> _state.update {
                    it.copy(ampsError = BreakerField.AMPS in r.fields, placement = r.placement?.takeIf { BreakerField.SLOT in r.fields })
                }
            }
        }
    }

    private fun delete() {
        val id = _state.value.draft.id ?: return
        viewModelScope.launch {
            repo.deleteBreaker(id)
            _state.update { it.copy(confirmDelete = false, done = true) }
        }
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    /** The draft as SavedStateHandle keeps it (BreakerDraft lives in :house-model, which has no serialization plugin). */
    @Serializable
    private data class SavedDraft(
        val id: String?, val startSlot: Int, val poles: Int, val amps: String, val type: String, val label: String,
        val tandem: Boolean, val roomIds: List<String>, val itemIds: List<String>,
    ) {
        fun toDraft() = BreakerDraft(
            id, startSlot, poles, amps, BreakerType.entries.firstOrNull { it.name == type } ?: BreakerType.STANDARD, label, tandem,
            roomIds.toSet(), itemIds.toSet(),
        )

        companion object {
            fun of(d: BreakerDraft) = SavedDraft(d.id, d.startSlot, d.poles, d.amps, d.type.name, d.label, d.tandem, d.roomIds.toList(), d.itemIds.toList())
        }
    }

    private companion object {
        const val DRAFT = "breaker_draft"
        val json = Json { ignoreUnknownKeys = true }
    }
}
