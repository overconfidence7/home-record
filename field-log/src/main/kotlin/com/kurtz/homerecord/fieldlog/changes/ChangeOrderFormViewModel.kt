package com.kurtz.homerecord.fieldlog.changes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.changeorder.JobChangeOrders
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** The share link of one change order (AC-F40): the URL while live, make it, turn it off, and the share sheet. */
interface CoLinks {
    fun observe(changeOrderId: String): Flow<String?>
    suspend fun make(changeOrderId: String): String?
    suspend fun turnOff(changeOrderId: String): Boolean
    /** Opens the share sheet with the link; [forSigning] words it as a request to sign. False when it could not open. */
    fun share(url: String, title: String, forSigning: Boolean): Boolean
}

class ChangeOrderFormDeps(
    val jobId: String,
    /** Null for a new change order. */
    val changeOrderId: String?,
    val orders: JobChangeOrders,
    /** RoleGates.leads(jobId): only Owner, Office and the job's Lead write change orders (AC-F3). */
    val canManage: Flow<Boolean> = flowOf(false),
    /** The job's client, filled in on a new change order. */
    val clientName: suspend () -> String? = { null },
    /** Whether this phone has speech to text (Speak button). */
    val canDictate: Boolean = false,
    val newLineId: () -> String = Ids::newId,
    /** The job's photos, newest first (T056b). */
    val photos: Flow<List<CoPhoto>> = flowOf(emptyList()),
    /** Makes the PDF and opens the share sheet (AC-F39). */
    val sharer: ChangeOrderSharer? = null,
    /** Share links for this order (AC-F40); null hides Share link. */
    val links: CoLinks? = null,
    /** RoleGates.isAdmin: only Owner and Office write share links. */
    val canLink: Flow<Boolean> = flowOf(false),
    /** The signature PNG of a Signed order, drawn from the synced strokes when signed elsewhere (T058b). */
    val signatureImage: suspend (changeOrderId: String) -> String? = { null },
    val now: () -> Long = System::currentTimeMillis,
)

/** Add or edit one change order (AC-F37). The total is MoneyMath over the lines; the typing survives process death. */
class ChangeOrderFormViewModel(private val d: ChangeOrderFormDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(ChangeOrderFormUiState(isNew = d.changeOrderId == null, canDictate = d.canDictate))
    val state: StateFlow<ChangeOrderFormUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { d.photos.collect(::onJobPhotos) }
        viewModelScope.launch { d.canLink.collect { ok -> _state.update { it.copy(linkAllowed = ok) } } }
        val links = d.links
        if (links != null && d.changeOrderId != null) {
            viewModelScope.launch { links.observe(d.changeOrderId).collect { url -> _state.update { it.copy(linkUrl = url) } } }
        }
    }

    /**
     * New job photos. After Take photo, a photo taken since then joins the change order once (T056b); one the person
     * takes off again stays off. The time and the photos already handled live in the saved state, so a camera trip
     * that outlives the process still lands.
     */
    private fun onJobPhotos(photos: List<CoPhoto>) {
        _state.update { it.copy(jobPhotos = photos) }
        val since = saved.get<Long>(TAKING_SINCE) ?: return
        val handled = saved.get<ArrayList<String>>(HANDLED).orEmpty()
        val fresh = photos.filter { it.createdAt >= since && it.id !in handled }.sortedBy { it.createdAt }.map { it.id }
        if (fresh.isEmpty() || !ready()) return
        saved[HANDLED] = ArrayList(handled + fresh)
        edit { dr -> dr.copy(photoIds = dr.photoIds + fresh.filterNot { it in dr.photoIds }) }
    }

    private fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val existing = d.changeOrderId?.let { d.orders.get(it) }?.takeIf { it.jobId == d.jobId }
            if (!d.canManage.first() || (d.changeOrderId != null && existing == null)) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val initial = existing?.let(ChangeOrderFormDraft::of)
                ?: ChangeOrderFormDraft(clientName = d.clientName().orEmpty(), lines = listOf(FormLine(d.newLineId())))
            val locked = existing != null && existing.status != ChangeOrderStatus.DRAFT
            val draft = if (locked) initial else saved.get<String>(DRAFT)
                ?.let { runCatching { json.decodeFromString(ChangeOrderFormDraft.serializer(), it) }.getOrNull() } ?: initial
            val signature = existing?.takeIf { it.status == ChangeOrderStatus.SIGNED }
                ?.let { CoSignature(it.signedBy.orEmpty(), it.signedAt ?: it.updatedAt, it.signaturePath, it.signedOnWeb) }
            _state.update {
                it.copy(
                    loading = false, locked = locked, draft = draft, initial = initial, signature = signature,
                    declined = existing?.status == ChangeOrderStatus.DECLINED,
                )
            }
            onJobPhotos(_state.value.jobPhotos)
            // Signed on another phone or on the share page: only the strokes came with sync; draw the image once.
            if (signature != null && existing != null && existing.signature.isNotEmpty()) {
                d.signatureImage(existing.id)?.let { path -> _state.update { st -> st.copy(signature = st.signature?.copy(imagePath = path)) } }
            }
        }
    }

    fun onEvent(e: ChangeOrderFormEvent) {
        when (e) {
            is ChangeOrderFormEvent.Title -> edit { it.copy(title = e.text) }
            is ChangeOrderFormEvent.Client -> edit { it.copy(clientName = e.text) }
            is ChangeOrderFormEvent.Description -> edit { it.copy(description = e.text) }
            is ChangeOrderFormEvent.Dictated -> e.text.trim().takeIf { it.isNotEmpty() }?.let { heard ->
                edit { d -> d.copy(description = listOf(d.description.trimEnd(), heard).filter { it.isNotEmpty() }.joinToString(" ")) }
            }
            ChangeOrderFormEvent.AddLine -> edit { it.copy(lines = it.lines + FormLine(d.newLineId())) }
            is ChangeOrderFormEvent.LineDescription -> line(e.id) { it.copy(description = e.text) }
            is ChangeOrderFormEvent.LineQty -> line(e.id) { it.copy(qty = e.text) }
            is ChangeOrderFormEvent.LinePrice -> line(e.id) { it.copy(price = e.text) }
            is ChangeOrderFormEvent.RemoveLine -> edit { it.copy(lines = it.lines.filterNot { l -> l.id == e.id }) }
            ChangeOrderFormEvent.OpenPhotos -> if (ready()) _state.update { it.copy(pickingPhotos = true) }
            ChangeOrderFormEvent.ClosePhotos -> _state.update { it.copy(pickingPhotos = false) }
            is ChangeOrderFormEvent.TogglePhoto -> edit { dr ->
                dr.copy(photoIds = if (e.id in dr.photoIds) dr.photoIds - e.id else dr.photoIds + e.id)
            }
            is ChangeOrderFormEvent.RemovePhoto -> edit { it.copy(photoIds = it.photoIds - e.id) }
            ChangeOrderFormEvent.TakePhoto -> if (ready()) {
                if (saved.get<Long>(TAKING_SINCE) == null) saved[TAKING_SINCE] = d.now()
                _state.update { it.copy(pickingPhotos = false) }
            }
            ChangeOrderFormEvent.Save -> save()
            ChangeOrderFormEvent.SignOnPhone -> signOnPhone()
            ChangeOrderFormEvent.Duplicate -> duplicate()
            ChangeOrderFormEvent.Share -> share()
            ChangeOrderFormEvent.ShareLink -> shareLink()
            ChangeOrderFormEvent.AskLinkOff -> if (_state.value.linkUrl != null && _state.value.linkAllowed) _state.update { it.copy(confirmLinkOff = true) }
            ChangeOrderFormEvent.DismissLinkOff -> _state.update { it.copy(confirmLinkOff = false) }
            ChangeOrderFormEvent.LinkOff -> linkOff()
            ChangeOrderFormEvent.AskDelete -> if (ready() && !_state.value.isNew) _state.update { it.copy(confirmDelete = true) }
            ChangeOrderFormEvent.DismissDelete -> _state.update { it.copy(confirmDelete = false) }
            ChangeOrderFormEvent.Delete -> delete()
            ChangeOrderFormEvent.Retry -> load()
            ChangeOrderFormEvent.Speak, ChangeOrderFormEvent.Close -> Unit
        }
    }

    private fun ready(): Boolean = _state.value.let { !it.loading && !it.failed && !it.working && !it.locked }

    private fun line(id: String, change: (FormLine) -> FormLine) = edit { d -> d.copy(lines = d.lines.map { if (it.id == id) change(it) else it }) }

    private fun edit(change: (ChangeOrderFormDraft) -> ChangeOrderFormDraft) {
        if (!ready()) return
        val draft = change(_state.value.draft)
        saved[DRAFT] = json.encodeToString(ChangeOrderFormDraft.serializer(), draft)
        _state.update { it.copy(draft = draft) }
    }

    private fun save() {
        val st = _state.value
        if (!st.canSave) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val ok = if (d.changeOrderId == null) d.orders.add(d.jobId, st.draft.toDraft()) != null else d.orders.update(d.changeOrderId, st.draft.toDraft())
            if (ok) finish() else _state.update { it.copy(working = false, failed = true) }
        }
    }

    /** Saves what is typed (nothing to write when an existing Draft is unchanged), then hands its id to the pad. */
    private fun signOnPhone() {
        val st = _state.value
        if (!st.canSign) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val id = when {
                d.changeOrderId == null -> d.orders.add(d.jobId, st.draft.toDraft())
                !st.dirty -> d.changeOrderId
                d.orders.update(d.changeOrderId, st.draft.toDraft()) -> d.changeOrderId
                else -> null
            }
            if (id == null) {
                _state.update { it.copy(working = false, failed = true) }
                return@launch
            }
            clearSaved()
            _state.update { it.copy(working = false, signId = id) }
        }
    }

    /**
     * Saves a Draft's typed changes (the PDF shows what is saved), then makes the PDF and opens the share sheet. The
     * form stays open; a PDF that could not be made says so and Share can be tried again.
     */
    private fun share() {
        val id = d.changeOrderId ?: return
        val sharer = d.sharer ?: return
        val st = _state.value
        if (!st.canShare) return
        _state.update { it.copy(sharing = true, shareFailed = false) }
        viewModelScope.launch {
            if (!saveTyped(id, st)) {
                _state.update { it.copy(sharing = false, failed = true) }
                return@launch
            }
            val pdf = sharer.make(id)
            val shared = pdf != null && sharer.share(pdf)
            _state.update { it.copy(sharing = false, shareFailed = !shared) }
        }
    }

    /** A Draft's typed changes saved, so what is shared matches the form. False when the save failed. */
    private suspend fun saveTyped(id: String, st: ChangeOrderFormUiState): Boolean {
        if (st.locked || !st.dirty) return true
        if (!d.orders.update(id, st.draft.toDraft())) return false
        saved.remove<String>(DRAFT)
        _state.update { it.copy(initial = st.draft) }
        return true
    }

    /**
     * Share link (AC-F40): saves a Draft's typing, makes the link if there is none (it works once it has synced),
     * and opens the share sheet. The client opens it, reads the order and signs; the order shows Signed on next sync.
     */
    private fun shareLink() {
        val id = d.changeOrderId ?: return
        val links = d.links ?: return
        val st = _state.value
        if (!st.canShareLink) return
        _state.update { it.copy(linkWorking = true, linkFailed = false) }
        viewModelScope.launch {
            if (!saveTyped(id, st)) {
                _state.update { it.copy(linkWorking = false, failed = true) }
                return@launch
            }
            val url = links.make(id)
            val shared = url != null && links.share(url, st.draft.title.trim(), forSigning = st.signature == null)
            _state.update { it.copy(linkWorking = false, linkFailed = !shared, linkUrl = url ?: it.linkUrl) }
        }
    }

    private fun linkOff() {
        val id = d.changeOrderId ?: return
        val links = d.links ?: return
        _state.update { it.copy(confirmLinkOff = false, linkWorking = true) }
        viewModelScope.launch {
            links.turnOff(id)
            _state.update { it.copy(linkWorking = false, linkUrl = null, linkFailed = false) }
        }
    }

    private fun duplicate() {
        val id = d.changeOrderId ?: return
        val st = _state.value
        if (!st.locked || st.working || st.duplicateId != null) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val copy = d.orders.duplicate(id, d.newLineId)
            _state.update { if (copy == null) it.copy(working = false, failed = true) else it.copy(working = false, duplicateId = copy) }
        }
    }

    private fun delete() {
        val id = d.changeOrderId ?: return
        if (!ready()) return
        _state.update { it.copy(working = true, confirmDelete = false) }
        viewModelScope.launch {
            d.orders.delete(id)
            finish()
        }
    }

    private fun finish() {
        clearSaved()
        _state.update { it.copy(working = false, done = true) }
    }

    private fun clearSaved() {
        saved.remove<String>(DRAFT)
        saved.remove<Long>(TAKING_SINCE)
        saved.remove<ArrayList<String>>(HANDLED)
    }

    private companion object {
        const val DRAFT = "changeOrderDraft"
        const val TAKING_SINCE = "changeOrderTakingSince"
        const val HANDLED = "changeOrderPhotosHandled"
    }
}
