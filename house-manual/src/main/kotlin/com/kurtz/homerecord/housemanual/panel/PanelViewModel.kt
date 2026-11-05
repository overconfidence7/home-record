package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.SlotOccupancy
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.BreakerSave
import com.kurtz.homerecord.housemodel.panel.FeedChoices
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import com.kurtz.homerecord.housemodel.panel.PanelState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HM-Panel and HM-Breaker (AC-C40). Opening the screen creates the panel with its defaults the first time
 * ([PanelRepository.ensurePanel]), then follows it. [itemName] is the panel item's name, null once the item is gone;
 * [choices] are the rooms and items a breaker can feed. The open breaker form lives in [saved] so it survives rotation
 * and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModel(
    private val repo: PanelRepository,
    private val itemId: String,
    itemName: Flow<String?>,
    choices: Flow<FeedChoices> = flowOf(FeedChoices()),
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    // A panel row is only made for an item that still exists.
    private val panel: Flow<PanelState?> = itemName.map { it != null }.distinctUntilChanged().flatMapLatest { exists ->
        if (!exists) flowOf(null) else flow { emit(repo.ensurePanel(itemId)) }.flatMapLatest { repo.observe(itemId) }
    }

    private val form = MutableStateFlow(saved.get<String>(FORM)?.let { runCatching { json.decodeFromString<SavedForm>(it) }.getOrNull() })

    /** The latest panel, for the editor's slot checks and saves. */
    private var current: PanelState? = null

    val state: StateFlow<PanelUiState> = combine(itemName, panel, form, choices.onStart { emit(FeedChoices()) }) { name, p, f, c ->
        current = p
        when {
            name == null -> PanelUiState(loading = false, missing = true)
            p == null -> PanelUiState(loading = true, name = name)
            else -> PanelUiState(
                loading = false, name = name, panel = p.panel, breakers = p.breakers, grid = p.grid,
                editor = f?.let { editorState(it, p, c) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUiState())

    private fun editorState(f: SavedForm, p: PanelState, c: FeedChoices): BreakerEditorState {
        val draft = f.draft.toDraft()
        val others = p.breakers.filter { it.id != draft.id }
        return BreakerEditorState(
            draft = draft,
            dirty = f.draft != f.original,
            showErrors = f.showErrors,
            placement = SlotOccupancy.place(p.panel.layout, p.panel.slotCount, others, draft.startSlot, draft.poles, draft.tandem),
            rooms = c.rooms,
            items = c.items,
            confirmDelete = f.confirmDelete,
        )
    }

    fun onEvent(e: PanelEvent) {
        when (e) {
            PanelEvent.Back -> Unit // the route's job
            is PanelEvent.OpenSlot -> open(BreakerDraft(startSlot = e.slot))
            is PanelEvent.AddTandem -> {
                // Only beside a single-pole breaker that has the slot to itself.
                val sharing = current?.breakers.orEmpty().filter { e.slot in it.slots }
                if (sharing.size == 1 && sharing.single().poles == 1) open(BreakerDraft(startSlot = e.slot, tandem = true))
            }
            is PanelEvent.OpenBreaker -> viewModelScope.launch { repo.draftFor(e.id)?.let(::open) }
            is PanelEvent.ChangeBreaker -> setForm { it.copy(draft = SavedDraft.of(if (it.draft.tandem) e.draft.copy(poles = 1) else e.draft)) }
            PanelEvent.SaveBreaker -> save()
            PanelEvent.CloseEditor -> setForm { null }
            PanelEvent.AskDeleteBreaker -> setForm { it.copy(confirmDelete = true) }
            PanelEvent.CancelDeleteBreaker -> setForm { it.copy(confirmDelete = false) }
            PanelEvent.ConfirmDeleteBreaker -> {
                val id = form.value?.draft?.id ?: return
                setForm { null }
                viewModelScope.launch { repo.deleteBreaker(id) }
            }
        }
    }

    private fun open(draft: BreakerDraft) {
        val d = SavedDraft.of(draft)
        form.value = SavedForm(d, d)
        persist()
    }

    private fun save() {
        val f = form.value ?: return
        val panelId = current?.panel?.id ?: return
        val draft = f.draft.toDraft()
        if (draft.ampsError()) {
            setForm { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            when (repo.saveBreaker(panelId, draft)) {
                is BreakerSave.Saved -> if (form.value?.draft == f.draft) setForm { null }
                is BreakerSave.Invalid -> setForm { it.copy(showErrors = true) }
            }
        }
    }

    private fun setForm(change: (SavedForm) -> SavedForm?) {
        form.update { it?.let(change) }
        persist()
    }

    private fun persist() {
        saved[FORM] = form.value?.let { json.encodeToString(SavedForm.serializer(), it) }
    }

    @Serializable
    private data class SavedDraft(
        val id: String? = null,
        val startSlot: Int = 1,
        val poles: Int = 1,
        val amps: String = "",
        val type: String = BreakerType.STANDARD.name,
        val label: String = "",
        val tandem: Boolean = false,
        val roomIds: Set<String> = emptySet(),
        val itemIds: Set<String> = emptySet(),
    ) {
        fun toDraft() = BreakerDraft(
            id, startSlot, poles, amps, runCatching { BreakerType.valueOf(type) }.getOrDefault(BreakerType.STANDARD), label, tandem, roomIds, itemIds,
        )

        companion object {
            fun of(d: BreakerDraft) = SavedDraft(d.id, d.startSlot, d.poles, d.amps, d.type.name, d.label, d.tandem, d.roomIds, d.itemIds)
        }
    }

    @Serializable
    private data class SavedForm(
        val draft: SavedDraft,
        val original: SavedDraft,
        val showErrors: Boolean = false,
        val confirmDelete: Boolean = false,
    )

    private companion object {
        const val FORM = "breakerForm"
        val json = Json { ignoreUnknownKeys = true }
    }
}
