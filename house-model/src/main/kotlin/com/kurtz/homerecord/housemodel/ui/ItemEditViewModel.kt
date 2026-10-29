package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.core.model.DataPlateParser
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.PlateField
import com.kurtz.homerecord.core.model.SerialAgeDecoder
import com.kurtz.homerecord.core.ocr.TextRecognizer
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.lookup.DataPlateVisionApi
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.items.DraftPhoto
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PhotoKind
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
    private val media: MediaFiles,
    private val recognizer: TextRecognizer,
    private val vision: DataPlateVisionApi? = null,
    initialRoomId: String? = null,
    private val afterCreate: (String) -> Unit = {},
    initialSubtypeId: String? = null,
) : ViewModel() {
    private var original = ItemDraft(
        roomId = initialRoomId,
        subtypeId = initialSubtypeId ?: ItemDraft().subtypeId,
        name = initialSubtypeId?.let { ItemCategory.subtype(it)?.label }.orEmpty(),
    )

    private val _state = MutableStateFlow(
        ItemEditUiState(draft = restore() ?: original, decodedRule = saved.get<String>(DECODED)),
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
            is ItemEditEvent.Change -> change(event.draft)
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
            is ItemEditEvent.OpenPhotoSheet -> _state.update { it.copy(photoSheetFor = event.kind) }
            ItemEditEvent.ClosePhotoSheet -> _state.update { it.copy(photoSheetFor = null) }
            is ItemEditEvent.TakePhoto -> {
                val target = media.newCaptureTarget()
                keepPending(event.kind, target.path)
                _state.update { it.copy(photoSheetFor = null, photoLaunch = PhotoLaunch(event.kind, target)) }
            }
            is ItemEditEvent.ChoosePhoto -> {
                keepPending(event.kind, null)
                _state.update { it.copy(photoSheetFor = null, photoLaunch = PhotoLaunch(event.kind, null)) }
            }
            is ItemEditEvent.CameraResult -> {
                val kind = pendingKind()
                val path = saved.get<String>(PENDING_PATH)
                keepPending(null, null)
                if (kind != null && path != null) {
                    if (event.ok) onEvent(ItemEditEvent.PhotoCaptured(kind, path)) else media.discard(path)
                }
            }
            is ItemEditEvent.GalleryResult -> {
                val kind = pendingKind() ?: PhotoKind.ITEM
                keepPending(null, null)
                event.uri?.let { onEvent(ItemEditEvent.PhotoPicked(kind, it)) }
            }
            ItemEditEvent.PhotoLaunchHandled -> _state.update { it.copy(photoLaunch = null) }
            is ItemEditEvent.PhotoCaptured -> if (media.exists(event.path)) addPhoto(event.kind, event.path)
            is ItemEditEvent.PhotoPicked -> viewModelScope.launch { media.import(event.uri)?.let { addPhoto(event.kind, it) } }
            is ItemEditEvent.RemovePhoto -> {
                val draft = _state.value.draft
                draft.photos.firstOrNull { it.path == event.path && it.id == null }?.let { media.discard(it.path) }
                setDraft(draft.copy(photos = draft.photos.filterNot { it.path == event.path }))
            }
            is ItemEditEvent.PlateTarget -> _state.update { s -> s.copy(plateScan = s.plateScan?.copy(target = event.field)) }
            is ItemEditEvent.PlateAssign -> _state.update { s ->
                val scan = s.plateScan ?: return@update s
                val next = PlateField.entries.firstOrNull { it.ordinal > scan.target.ordinal } ?: scan.target
                s.copy(plateScan = scan.with(scan.target, event.line).copy(target = next))
            }
            ItemEditEvent.PlateApply -> {
                val scan = _state.value.plateScan ?: return
                val draft = _state.value.draft
                change(
                    draft.copy(
                        make = scan.make.ifBlank { draft.make },
                        model = scan.model.ifBlank { draft.model },
                        serial = scan.serial.ifBlank { draft.serial },
                    ),
                )
                _state.update { it.copy(plateScan = null) }
            }
            ItemEditEvent.PlateDismiss -> _state.update { it.copy(plateScan = null) }
            ItemEditEvent.Save -> save()
        }
    }

    /** Keeps the open camera or gallery request in [saved], so a result that lands after rotation or process death is filed right. */
    private fun keepPending(kind: PhotoKind?, path: String?) {
        saved[PENDING_KIND] = kind?.name
        saved[PENDING_PATH] = path
    }

    private fun pendingKind(): PhotoKind? = saved.get<String>(PENDING_KIND)?.let { k -> PhotoKind.entries.firstOrNull { it.name == k } }

    private fun addPhoto(kind: PhotoKind, path: String) {
        val draft = _state.value.draft
        setDraft(draft.copy(photos = draft.photos + DraftPhoto(null, path, kind)))
        if (kind == PhotoKind.DATA_PLATE) scan(path)
    }

    /** Reads the data plate on-device and opens the recognized-lines sheet with the parser's proposal. */
    private fun scan(path: String) {
        _state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val lines = recognizer.recognizeLines(path)
            val proposal = DataPlateParser.parse(lines)
            if (proposal.model == null) {
                // AC-B11: on-device OCR found no model, so ask the server to read the photo; offline or failed keeps the OCR result.
                val read = (vision?.read(path) as? ApiResult.Ok)?.value
                val found = listOfNotNull(read?.make, read?.model, read?.serial)
                val readModel = read?.model
                if (read != null && readModel != null) {
                    _state.update {
                        it.copy(
                            scanning = false,
                            plateScan = PlateScan(
                                (found + proposal.lines).distinct(), read.make ?: proposal.make.orEmpty(), readModel, read.serial ?: proposal.serial.orEmpty(),
                                target = PlateField.entries.firstOrNull { f -> f != PlateField.MODEL && (if (f == PlateField.MAKE) read.make ?: proposal.make else read.serial ?: proposal.serial) == null } ?: PlateField.MODEL,
                            ),
                        )
                    }
                    return@launch
                }
            }
            _state.update {
                it.copy(
                    scanning = false,
                    plateScan = if (proposal.lines.isEmpty()) null else PlateScan(
                        proposal.lines, proposal.make.orEmpty(), proposal.model.orEmpty(), proposal.serial.orEmpty(),
                        target = PlateField.entries.firstOrNull { f -> proposal.valueFor(f) == null } ?: PlateField.MODEL,
                    ),
                )
            }
        }
    }

    private fun restore(): ItemDraft? {
        val base = saved.get<ArrayList<String>>(KEY)?.let(::decode) ?: return null
        val photos = saved.get<ArrayList<String>>(PHOTOS).orEmpty().mapNotNull(::decodePhoto)
        return base.copy(photos = photos)
    }

    /**
     * Applies a form edit. When make or serial changes and the manufacture date is blank or was itself decoded, it is
     * (re)filled from the serial; typing or picking a date by hand makes it the homeowner's and stops the decoding (AC-C32).
     */
    private fun change(draft: ItemDraft) {
        val old = _state.value.draft
        val rule = _state.value.decodedRule
        var next = draft
        var nextRule = rule
        if (draft.manufacturedOn != old.manufacturedOn) {
            nextRule = null
        } else if ((draft.make != old.make || draft.serial != old.serial) && (old.manufacturedOn.isBlank() || rule != null)) {
            val decoded = SerialAgeDecoder.decode(draft.make, draft.serial)
            if (decoded != null) {
                next = draft.copy(manufacturedOn = decoded.date)
                nextRule = decoded.rule
            } else if (rule != null) {
                next = draft.copy(manufacturedOn = "")
                nextRule = null
            }
        }
        saved[DECODED] = nextRule
        _state.update { it.copy(decodedRule = nextRule) }
        setDraft(next)
    }

    private fun setDraft(draft: ItemDraft) {
        saved[KEY] = encode(draft)
        saved[PHOTOS] = ArrayList(draft.photos.map(::encodePhoto))
        _state.update { it.copy(draft = draft, dirty = draft != original) }
    }

    /** The form closed for good (not rotation or process death): files of photos that were never saved go too (cr:orphan-media). */
    override fun onCleared() {
        val current = _state.value
        if (current.saving || current.savedItemId != null) return
        current.draft.photos.filter { it.id == null }.forEach { media.discard(it.path) }
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
            if (id != null && current.draft.id == null) afterCreate(id)
            _state.update { it.copy(saving = false, savedItemId = id, dirty = false) }
        }
    }

    private companion object {
        const val KEY = "draft"
        const val PHOTOS = "photos"
        const val DECODED = "decoded"
        const val PENDING_KIND = "pendingKind"
        const val PENDING_PATH = "pendingPath"

        fun encodePhoto(p: DraftPhoto) = "${p.id.orEmpty()}|${p.kind.name}|${p.path}"

        fun decodePhoto(v: String): DraftPhoto? {
            val parts = v.split('|', limit = 3)
            val kind = PhotoKind.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: return null
            return DraftPhoto(parts[0].ifEmpty { null }, parts[2], kind)
        }

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
