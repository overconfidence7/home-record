package com.kurtz.homerecord.housemanual.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.items.LookupOutcome
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.housemodel.items.ItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Item page (AC-H7). The delete confirmation lives in [SavedStateHandle] so it survives rotation and process death. */
class ItemDetailViewModel(
    private val repo: ItemRepository,
    private val lookup: WarrantyLookupRepository,
    private val itemId: String,
    detail: Flow<ItemDetail?>,
    private val copySuffix: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val onClose: () -> Unit = {},
    private val onOpenCopy: (String) -> Unit = {},
) : ViewModel() {
    private val confirmDelete = MutableStateFlow(saved[CONFIRM] ?: false)

    private val lookupState = MutableStateFlow(LookupUi())

    private data class LookupUi(val busy: Boolean = false, val note: LookupNote? = null, val review: WarrantyTerms? = null)

    val state: StateFlow<ItemDetailUiState> = combine(detail, confirmDelete, lookupState) { d, c, l ->
        ItemDetailUiState(loading = false, detail = d, confirmDelete = c && d != null, lookupBusy = l.busy, lookupNote = l.note, review = l.review)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    fun onEvent(e: ItemDetailEvent) {
        when (e) {
            ItemDetailEvent.AskDelete -> setConfirm(true)
            ItemDetailEvent.CancelDelete -> setConfirm(false)
            ItemDetailEvent.ConfirmDelete -> viewModelScope.launch {
                repo.delete(itemId)
                setConfirm(false)
                onClose()
            }
            ItemDetailEvent.Duplicate -> viewModelScope.launch { repo.duplicate(itemId, copySuffix)?.let(onOpenCopy) }
            is ItemDetailEvent.SetRegistered -> viewModelScope.launch { repo.setRegistered(itemId, e.registered) }
            is ItemDetailEvent.LookUp -> lookUp(e.again)
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
            ItemDetailEvent.Back, ItemDetailEvent.Edit, is ItemDetailEvent.OpenLink -> Unit // navigation is the route's job
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

    private companion object { const val CONFIRM = "confirmDelete" }
}
