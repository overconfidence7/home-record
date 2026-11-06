package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.housemodel.panel.PanelDraft
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
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
    private val itemName: Flow<String?>,
    private val choices: Flow<FeedChoices> = flowOf(FeedChoices()),
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val media: MediaFiles? = null,
    /** Opened from an item's breaker chip (AC-H7): the slot to scroll to and mark for [highlightMillis]. */
    highlightSlot: Int? = null,
    private val highlightMillis: Long = HIGHLIGHT_MILLIS,
    /** Legend PDF (AC-C43). */
    private val legendExporter: com.kurtz.homerecord.housemodel.pdf.LegendExporter = com.kurtz.homerecord.housemodel.pdf.LegendExporter { _, _, _, _ -> null },
    /** Where the legend reads the database and writes the PDF; off the main thread so it never waits on the UI. */
    private val legendDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
) : ViewModel() {
    // Shown once: after rotation or process death the panel opens without marking the slot again.
    private val highlight = MutableStateFlow(highlightSlot?.takeUnless { saved[HIGHLIGHT_SHOWN] ?: false })

    // A panel row is only made for an item that still exists.
    private val panel: Flow<PanelState?> = itemName.map { it != null }.distinctUntilChanged().flatMapLatest { exists ->
        if (!exists) flowOf(null) else flow { emit(repo.ensurePanel(itemId)) }.flatMapLatest { repo.observe(itemId) }
    }

    private val form = MutableStateFlow(saved.get<String>(FORM)?.let { runCatching { json.decodeFromString<SavedForm>(it) }.getOrNull() })

    private val facts = MutableStateFlow(saved.get<String>(FACTS)?.let { runCatching { json.decodeFromString<SavedFacts>(it) }.getOrNull() })
    private val photo = MutableStateFlow(PhotoUi())

    private data class PhotoUi(val sheet: Boolean = false, val launch: PanelPhotoLaunch? = null, val failed: Boolean = false)

    /** The latest panel, for the editor's slot checks and saves. */
    private var current: PanelState? = null

    /** Legend share: the URI for the share sheet, or failed. */
    private data class LegendUi(val uri: String? = null, val failed: Boolean = false, val busy: Boolean = false)

    private val legend = MutableStateFlow(LegendUi())

    private val extras = combine(facts, photo, highlight, legend) { fa, ph, h, lg -> Extras(fa, ph, h, lg) }

    private data class Extras(val fa: SavedFacts?, val ph: PhotoUi, val h: Int?, val lg: LegendUi)

    init {
        if (highlight.value != null) {
            viewModelScope.launch {
                delay(highlightMillis)
                highlight.value = null
                saved[HIGHLIGHT_SHOWN] = true
            }
        }
    }

    val state: StateFlow<PanelUiState> = combine(itemName, panel, form, choices.onStart { emit(FeedChoices()) }, extras) { name, p, f, c, (fa, ph, h, lg) ->
        current = p
        when {
            name == null -> PanelUiState(loading = false, missing = true)
            p == null -> PanelUiState(loading = true, name = name)
            else -> PanelUiState(
                loading = false, name = name, panel = p.panel, breakers = p.breakers, grid = p.grid,
                editor = f?.let { editorState(it, p, c) },
                facts = fa?.let {
                    PanelFactsState(
                        it.draft.toDraft(), dirty = it.draft != it.original, showErrors = it.showErrors,
                        highestUsed = SlotOccupancy.highestUsed(p.breakers), layoutLocked = p.breakers.isNotEmpty(),
                    )
                },
                photoSheet = ph.sheet, photoLaunch = ph.launch, photoFailed = ph.failed,
                highlightSlot = h,
                legendUri = lg.uri, legendFailed = lg.failed, legendBusy = lg.busy,
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
            PanelEvent.Map -> Unit
            PanelEvent.ShareLegend -> shareLegend()
            PanelEvent.LegendHandled -> legend.value = LegendUi()
            PanelEvent.EditFacts -> current?.panel?.let { p ->
                val d = SavedFactsDraft.of(PanelDraft.of(p))
                setFacts { SavedFacts(d, d) }
            }
            is PanelEvent.ChangeFacts -> setFacts { it?.copy(draft = SavedFactsDraft.of(e.draft)) }
            PanelEvent.CloseFacts -> setFacts { null }
            PanelEvent.SaveFacts -> saveFacts()
            else -> onPhotoEvent(e)
        }
    }

    /**
     * Reads the panel, its links and the feed names straight from the repository, so the PDF always has the breakers'
     * latest links even when the screen's state has not caught up yet.
     */
    private fun shareLegend() {
        if (legend.value.busy) return
        legend.value = LegendUi(busy = true)
        viewModelScope.launch(legendDispatcher) {
            val p = repo.observe(itemId).first()
            val uri = if (p == null) null else {
                val c = choices.first()
                val names = (c.rooms + c.items).associate { it.id to it.name }
                val feeds = p.links.mapValues { (_, links) -> links.mapNotNull { l -> (l.roomId ?: l.itemId)?.let(names::get) }.distinct() }
                legendExporter.export(itemName.first().orEmpty(), p.panel, p.breakers, feeds)
            }
            legend.value = LegendUi(uri = uri, failed = uri == null)
        }
    }

    private fun saveFacts() {
        val f = facts.value ?: return
        val p = current ?: return
        val draft = f.draft.toDraft()
        val locked = p.breakers.isNotEmpty() && draft.layout != p.panel.layout
        if (locked || draft.errors(SlotOccupancy.highestUsed(p.breakers)).isNotEmpty()) {
            setFacts { it?.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            if (repo.savePanel(p.panel.id, draft)) {
                if (facts.value?.draft == f.draft) setFacts { null }
            } else {
                setFacts { it?.copy(showErrors = true) }
            }
        }
    }

    private fun setFacts(change: (SavedFacts?) -> SavedFacts?) {
        facts.update(change)
        saved[FACTS] = facts.value?.let { json.encodeToString(SavedFacts.serializer(), it) }
    }

    /** Panel photo (AC-C40): camera or photo picker; the camera's file waits in [saved] so a result after process death lands. */
    private fun onPhotoEvent(e: PanelEvent) {
        when (e) {
            PanelEvent.OpenPhotoSheet -> photo.update { it.copy(sheet = true, failed = false) }
            PanelEvent.ClosePhotoSheet -> photo.update { it.copy(sheet = false) }
            PanelEvent.TakePhoto -> {
                val files = media ?: return
                val target = files.newCaptureTarget()
                saved[PENDING_PHOTO] = target.path
                photo.update { it.copy(sheet = false, launch = PanelPhotoLaunch(target)) }
            }
            PanelEvent.ChoosePhoto -> photo.update { it.copy(sheet = false, launch = PanelPhotoLaunch(null)) }
            PanelEvent.RemovePhoto -> {
                photo.update { it.copy(sheet = false) }
                val id = current?.panel?.id ?: return
                viewModelScope.launch { repo.setPhoto(id, null) }
            }
            PanelEvent.PhotoLaunchHandled -> photo.update { it.copy(launch = null) }
            is PanelEvent.CameraResult -> {
                val path = saved.get<String>(PENDING_PHOTO) ?: return
                saved[PENDING_PHOTO] = null
                val files = media ?: return
                if (e.ok && files.exists(path)) setPhoto(path) else files.discard(path)
            }
            is PanelEvent.GalleryResult -> {
                val uri = e.uri ?: return
                val files = media ?: return
                viewModelScope.launch {
                    val path = files.import(uri)
                    if (path == null) photo.update { it.copy(failed = true) } else setPhoto(path)
                }
            }
            else -> Unit
        }
    }

    private fun setPhoto(path: String) {
        viewModelScope.launch {
            val id = current?.panel?.id ?: repo.ensurePanel(itemId)
            repo.setPhoto(id, path)
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

    @Serializable
    private data class SavedFactsDraft(val slotCount: String, val layout: String, val mainAmps: String, val brand: String) {
        fun toDraft() = PanelDraft(slotCount, PanelLayout.of(layout), mainAmps, brand)

        companion object {
            fun of(d: PanelDraft) = SavedFactsDraft(d.slotCount, d.layout.name, d.mainAmps, d.brand)
        }
    }

    @Serializable
    private data class SavedFacts(val draft: SavedFactsDraft, val original: SavedFactsDraft, val showErrors: Boolean = false)

    private companion object {
        const val FORM = "breakerForm"
        const val HIGHLIGHT_SHOWN = "highlightShown"
        const val HIGHLIGHT_MILLIS = 2_000L
        const val FACTS = "panelFacts"
        const val PENDING_PHOTO = "pendingPanelPhoto"
        val json = Json { ignoreUnknownKeys = true }
    }
}
