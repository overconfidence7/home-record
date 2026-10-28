package com.kurtz.homerecord.housemanual.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.lookup.RecallApi
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import com.kurtz.homerecord.housemodel.documents.DocumentScope
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.items.LookupOutcome
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.upkeep.ConsumableDraft
import com.kurtz.homerecord.housemodel.upkeep.ServiceDraft
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Item page (AC-H7). The delete confirmation, a pending navigation and an open upkeep form (its draft and what it opened with)
 * live in [SavedStateHandle] so they survive rotation and process death; the route reads [ItemDetailUiState.nav] and navigates, so the ViewModel never holds a composition's lambdas.
 */
class ItemDetailViewModel(
    private val repo: ItemRepository,
    private val lookup: WarrantyLookupRepository,
    private val upkeep: UpkeepRepository,
    private val documents: DocumentRepository,
    private val itemId: String,
    private val homeId: String,
    detail: Flow<ItemDetail?>,
    private val copySuffix: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val recallApi: RecallApi? = null,
) : ViewModel() {
    private val confirmDelete = MutableStateFlow(saved[CONFIRM] ?: false)

    private val nav = MutableStateFlow(
        when {
            saved[NAV_CLOSED] ?: false -> ItemDetailNav.Closed
            else -> saved.get<String>(NAV_COPY)?.let { ItemDetailNav.OpenCopy(it) }
        },
    )

    private val lookupState = MutableStateFlow(LookupUi())

    private data class LookupUi(val busy: Boolean = false, val note: LookupNote? = null, val review: WarrantyTerms? = null)

    private val recallState = MutableStateFlow<RecallUi>(RecallUi.Idle)

    private val forms = MutableStateFlow(restoreForms())

    private data class FormsUi(
        val consumable: ConsumableDraft? = null,
        val service: ServiceDraft? = null,
        val showErrors: Boolean = false,
        /** The draft the open form started from; the form is dirty while it differs. */
        val original: Any? = null,
    ) {
        val dirty: Boolean get() = (consumable ?: service)?.let { it != original } ?: false
        val canSave: Boolean get() = (consumable ?: service) != null && (dirty || (consumable?.id ?: service?.id) == null)
    }

    /** The record a Delete confirm is open for, kept as kind and id so it survives process death. */
    private data class PendingDelete(val kind: String, val id: String)

    private val upkeepDelete = MutableStateFlow(
        saved.get<List<String>>(UPKEEP_DELETE)?.takeIf { it.size == 2 }?.let { PendingDelete(it[0], it[1]) },
    )

    private val upkeepRows = combine(upkeep.observeConsumables(itemId), upkeep.observeService(itemId)) { c, s -> c to s }

    private val contactRows = upkeep.observeContacts(homeId).map { rows -> rows.map { it.contact } }

    private val upkeepAndContacts = combine(upkeepRows, contactRows) { u, c -> u to c }

    private val documentUi = MutableStateFlow(DocumentUi(showAttach = saved[ATTACH] ?: false))

    private data class DocumentUi(val showAttach: Boolean = false, val notice: Boolean = false)

    private val documentRows = combine(documents.observeFolders(homeId), documentUi) { folders, ui ->
        val all = folders.flatMap { it.documents }
        Triple(all.filter { it.itemId == itemId }.sortedByDescending { it.createdAt }, all.filter { it.itemId != itemId }, ui)
    }

    private val upkeepContactsDocs = combine(upkeepAndContacts, documentRows, recallState) { uc, docs, r -> Triple(uc, docs, r) }

    private val content = combine(detail, confirmDelete, lookupState, upkeepContactsDocs, forms) { d, c, l, ucd, f ->
        val (uc, docs, r) = ucd
        val (u, contacts) = uc
        ItemDetailUiState(
            loading = false, detail = d, confirmDelete = c && d != null, lookupBusy = l.busy, lookupNote = l.note, review = l.review,
            consumables = u.first, service = u.second, consumableForm = f.consumable, serviceForm = f.service, formDirty = f.dirty, formCanSave = f.canSave, showFormErrors = f.showErrors, contacts = contacts,
            documents = docs.first, attachable = docs.second, showAttach = docs.third.showAttach, documentNotice = docs.third.notice, recalls = r,
        )
    }

    val state: StateFlow<ItemDetailUiState> = combine(content, nav, upkeepDelete) { s, n, p ->
        s.copy(nav = n, confirmUpkeepDelete = p?.let { upkeepDeleteFor(it, s) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    fun onEvent(e: ItemDetailEvent) {
        when (e) {
            ItemDetailEvent.AskDelete -> setConfirm(true)
            ItemDetailEvent.CancelDelete -> setConfirm(false)
            ItemDetailEvent.ConfirmDelete -> viewModelScope.launch {
                repo.delete(itemId)
                setConfirm(false)
                setNav(ItemDetailNav.Closed)
            }
            ItemDetailEvent.Duplicate -> viewModelScope.launch { repo.duplicate(itemId, copySuffix)?.let { setNav(ItemDetailNav.OpenCopy(it)) } }
            ItemDetailEvent.NavHandled -> setNav(null)
            is ItemDetailEvent.SetRegistered -> viewModelScope.launch { repo.setRegistered(itemId, e.registered) }
            is ItemDetailEvent.LookUp -> lookUp(e.again)
            ItemDetailEvent.CheckRecalls -> checkRecalls()
            ItemDetailEvent.OpenReview -> viewModelScope.launch {
                lookup.reviewTerms(itemId)?.let { t -> lookupState.update { it.copy(review = t) } }
            }
            is ItemDetailEvent.ConfirmTerms -> viewModelScope.launch {
                val terms = lookupState.value.review ?: return@launch
                val edited = e.partsMonths != terms.partsMonths || e.laborMonths != terms.laborMonths
                lookup.confirm(itemId, terms.copy(partsMonths = e.partsMonths, laborMonths = e.laborMonths), edited)
                lookupState.value = LookupUi()
            }
            ItemDetailEvent.DiscardTerms -> viewModelScope.launch {
                lookup.discard(itemId)
                lookupState.value = LookupUi()
            }
            is ItemDetailEvent.OpenConsumable -> viewModelScope.launch {
                val row = e.id?.let { id -> state.value.consumables.firstOrNull { it.id == id } }
                val draft = row?.let {
                    ConsumableDraft(it.id, runCatching { ConsumableKind.valueOf(it.kind) }.getOrDefault(ConsumableKind.OTHER), it.spec, it.quantity.toString(), it.reorderUrl.orEmpty())
                } ?: ConsumableDraft()
                setForms(FormsUi(consumable = draft, original = draft))
            }
            is ItemDetailEvent.ChangeConsumable -> if (forms.value.consumable != null) setForms(forms.value.copy(consumable = e.draft))
            ItemDetailEvent.SaveConsumable -> viewModelScope.launch {
                val draft = forms.value.consumable ?: return@launch
                setForms(if (upkeep.saveConsumable(itemId, draft) == null) forms.value.copy(showErrors = true) else FormsUi())
            }
            is ItemDetailEvent.AskDeleteConsumable -> setUpkeepDelete(PendingDelete(CONSUMABLE, e.id))
            is ItemDetailEvent.OpenService -> {
                val row = e.id?.let { id -> state.value.service.firstOrNull { it.id == id } }
                val draft = row?.let {
                    ServiceDraft(it.id, it.date, it.contactId, it.description, it.costCents?.let { c -> "%d.%02d".format(c / 100, c % 100) }.orEmpty())
                } ?: ServiceDraft()
                setForms(FormsUi(service = draft, original = draft))
            }
            is ItemDetailEvent.ChangeService -> if (forms.value.service != null) setForms(forms.value.copy(service = e.draft))
            ItemDetailEvent.SaveService -> viewModelScope.launch {
                val draft = forms.value.service ?: return@launch
                setForms(if (upkeep.saveService(itemId, draft) == null) forms.value.copy(showErrors = true) else FormsUi())
            }
            is ItemDetailEvent.AskDeleteService -> setUpkeepDelete(PendingDelete(SERVICE, e.id))
            ItemDetailEvent.CancelUpkeepDelete -> setUpkeepDelete(null)
            ItemDetailEvent.ConfirmUpkeepDelete -> viewModelScope.launch {
                val p = upkeepDelete.value ?: return@launch
                if (p.kind == CONSUMABLE) upkeep.deleteConsumable(p.id) else upkeep.deleteService(p.id)
                setUpkeepDelete(null)
                setForms(FormsUi())
            }
            ItemDetailEvent.CloseForm -> {
                setUpkeepDelete(null)
                setForms(FormsUi())
            }
            is ItemDetailEvent.DocumentPicked -> viewModelScope.launch {
                val id = documents.add(homeId, DocumentScope.Item(itemId), e.uri)
                documentUi.update { it.copy(notice = id == null) }
            }
            ItemDetailEvent.DocumentScanFailed -> documentUi.update { it.copy(notice = true) }
            ItemDetailEvent.DismissDocumentNotice -> documentUi.update { it.copy(notice = false) }
            ItemDetailEvent.ShowAttach -> setAttach(true)
            ItemDetailEvent.HideAttach -> setAttach(false)
            is ItemDetailEvent.AttachDocument -> viewModelScope.launch {
                documents.attach(e.id, DocumentScope.Item(itemId))
                setAttach(false)
            }
            ItemDetailEvent.Back, ItemDetailEvent.Edit, is ItemDetailEvent.OpenLink, ItemDetailEvent.UploadDocument, ItemDetailEvent.ScanDocument, is ItemDetailEvent.OpenDocument -> Unit // navigation is the route's job
        }
    }

    /** Names the pending record from its saved row; a record deleted elsewhere shows no confirm. */
    private fun upkeepDeleteFor(p: PendingDelete, s: ItemDetailUiState): UpkeepDelete? = when (p.kind) {
        CONSUMABLE -> s.consumables.firstOrNull { it.id == p.id }?.let { UpkeepDelete.Consumable(it.id, it.spec) }
        else -> s.service.firstOrNull { it.id == p.id }?.let { UpkeepDelete.Service(it.id, it.description) }
    }

    private fun setUpkeepDelete(p: PendingDelete?) {
        upkeepDelete.value = p
        saved[UPKEEP_DELETE] = p?.let { arrayListOf(it.kind, it.id) }
    }

    private fun checkRecalls() {
        if (recallState.value == RecallUi.Busy) return
        viewModelScope.launch {
            val item = repo.get(itemId) ?: return@launch
            val make = item.make?.trim().orEmpty()
            if (make.isEmpty()) {
                recallState.value = RecallUi.NeedsMake
                return@launch
            }
            recallState.value = RecallUi.Busy
            recallState.value = when (val r = recallApi?.check(make, item.model)) {
                is ApiResult.Ok -> RecallUi.Done(r.value)
                is ApiResult.Err -> if (r.failure == ApiFailure.Offline) RecallUi.Offline else RecallUi.Failed
                null -> RecallUi.Failed
            }
        }
    }

    private fun lookUp(again: Boolean) {
        if (lookupState.value.busy) return
        lookupState.value = LookupUi(busy = true)
        viewModelScope.launch {
            lookupState.value = when (val r = lookup.lookup(itemId, force = again)) {
                is LookupOutcome.Found -> LookupUi(review = r.terms)
                LookupOutcome.NeedsMakeModel -> LookupUi(note = LookupNote.NeedsMakeModel)
                LookupOutcome.Queued -> LookupUi(note = LookupNote.Queued)
                LookupOutcome.Failed -> LookupUi(note = LookupNote.Failed)
                LookupOutcome.AlreadyStored -> LookupUi()
            }
        }
    }

    private fun setConfirm(v: Boolean) {
        confirmDelete.value = v
        saved[CONFIRM] = v
    }

    private fun setNav(v: ItemDetailNav?) {
        nav.value = v
        saved[NAV_CLOSED] = v == ItemDetailNav.Closed
        saved[NAV_COPY] = (v as? ItemDetailNav.OpenCopy)?.id
    }

    private fun setForms(f: FormsUi) {
        forms.value = f
        saved[FORM_ERRORS] = f.showErrors
        saved[FORM_CONSUMABLE] = f.consumable?.let(::consumableToList)
        saved[FORM_SERVICE] = f.service?.let(::serviceToList)
        saved[FORM_ORIGINAL] = when (val o = f.original) {
            is ConsumableDraft -> consumableToList(o)
            is ServiceDraft -> serviceToList(o)
            else -> null
        }
    }

    private fun restoreForms(): FormsUi {
        val consumable = saved.get<List<String>>(FORM_CONSUMABLE)?.let(::consumableFromList)
        val service = saved.get<List<String>>(FORM_SERVICE)?.let(::serviceFromList)
        val original = saved.get<List<String>>(FORM_ORIGINAL)?.let { if (consumable != null) consumableFromList(it) else serviceFromList(it) }
        return FormsUi(consumable, service, saved[FORM_ERRORS] ?: false, original)
    }

    private fun setAttach(v: Boolean) {
        documentUi.update { it.copy(showAttach = v) }
        saved[ATTACH] = v
    }

    private companion object {
        const val CONFIRM = "confirmDelete"
        const val ATTACH = "showAttach"
        const val NAV_CLOSED = "navClosed"
        const val NAV_COPY = "navCopy"
        const val FORM_CONSUMABLE = "consumableForm"
        const val FORM_SERVICE = "serviceForm"
        const val FORM_ORIGINAL = "formOriginal"
        const val FORM_ERRORS = "formErrors"
        const val UPKEEP_DELETE = "upkeepDelete"
        const val CONSUMABLE = "consumable"
        const val SERVICE = "service"

        fun consumableToList(d: ConsumableDraft) = arrayListOf(d.id.orEmpty(), d.kind.name, d.spec, d.quantity, d.reorderUrl)

        fun consumableFromList(l: List<String>): ConsumableDraft? = l.takeIf { it.size == 5 }?.let {
            ConsumableDraft(it[0].ifEmpty { null }, runCatching { ConsumableKind.valueOf(it[1]) }.getOrDefault(ConsumableKind.OTHER), it[2], it[3], it[4])
        }

        fun serviceToList(d: ServiceDraft) = arrayListOf(d.id.orEmpty(), d.date, d.contactId.orEmpty(), d.description, d.cost)

        fun serviceFromList(l: List<String>): ServiceDraft? = l.takeIf { it.size == 5 }?.let {
            ServiceDraft(it[0].ifEmpty { null }, it[1], it[2].ifEmpty { null }, it[3], it[4])
        }
    }
}
