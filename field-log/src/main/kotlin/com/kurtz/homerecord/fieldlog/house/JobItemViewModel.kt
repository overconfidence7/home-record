package com.kurtz.homerecord.fieldlog.house

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.LookupOutcome
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * FL-Item (AC-F50): the item's details and its warranty lookup, which works as on House Manual's item page (AC-C34).
 * The delete confirmation and the closed flag live in [SavedStateHandle] so they survive rotation and process death.
 */
class JobItemViewModel(
    private val items: ItemRepository,
    private val lookup: WarrantyLookupRepository,
    private val itemId: String,
    detail: Flow<ItemDetail?>,
    canEdit: Flow<Boolean>,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private data class Local(
        val confirmDelete: Boolean = false,
        val closed: Boolean = false,
        val busy: Boolean = false,
        val note: JobItemLookupNote? = null,
        val review: WarrantyTerms? = null,
    )

    private val local = MutableStateFlow(Local(confirmDelete = saved[CONFIRM] ?: false, closed = saved[CLOSED] ?: false))

    val state: StateFlow<JobItemUiState> = combine(detail, canEdit, local) { d, edit, l ->
        JobItemUiState(
            loading = false, detail = d, canEdit = edit, confirmDelete = l.confirmDelete && d != null && edit,
            lookupBusy = l.busy, lookupNote = l.note, review = l.review, closed = l.closed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobItemUiState())

    fun onEvent(e: JobItemEvent) {
        when (e) {
            JobItemEvent.AskDelete -> setConfirm(true)
            JobItemEvent.CancelDelete -> setConfirm(false)
            JobItemEvent.ConfirmDelete -> viewModelScope.launch {
                items.delete(itemId)
                setConfirm(false)
                setClosed(true)
            }
            JobItemEvent.ClosedHandled -> setClosed(false)
            is JobItemEvent.LookUp -> lookUp(e.again)
            JobItemEvent.OpenReview -> viewModelScope.launch {
                lookup.reviewTerms(itemId)?.let { t -> local.update { it.copy(review = t) } }
            }
            is JobItemEvent.ConfirmTerms -> viewModelScope.launch {
                val terms = local.value.review ?: return@launch
                val edited = e.partsMonths != terms.partsMonths || e.laborMonths != terms.laborMonths
                lookup.confirm(itemId, terms.copy(partsMonths = e.partsMonths, laborMonths = e.laborMonths), edited)
                local.update { it.copy(busy = false, note = null, review = null) }
            }
            JobItemEvent.DiscardTerms -> viewModelScope.launch {
                lookup.discard(itemId)
                local.update { it.copy(busy = false, note = null, review = null) }
            }
            is JobItemEvent.SetRegistered -> viewModelScope.launch { items.setRegistered(itemId, e.registered) }
            JobItemEvent.Back, JobItemEvent.Edit, JobItemEvent.Place, is JobItemEvent.OpenLink -> Unit // the route navigates
        }
    }

    private fun lookUp(again: Boolean) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, note = null) }
        viewModelScope.launch {
            val outcome = lookup.lookup(itemId, force = again)
            local.update {
                when (outcome) {
                    is LookupOutcome.Found -> it.copy(busy = false, review = outcome.terms)
                    LookupOutcome.NeedsMakeModel -> it.copy(busy = false, note = JobItemLookupNote.NeedsMakeModel)
                    LookupOutcome.Queued -> it.copy(busy = false, note = JobItemLookupNote.Queued)
                    LookupOutcome.Failed -> it.copy(busy = false, note = JobItemLookupNote.Failed)
                    LookupOutcome.AlreadyStored -> it.copy(busy = false)
                }
            }
        }
    }

    private fun setConfirm(v: Boolean) {
        saved[CONFIRM] = v
        local.update { it.copy(confirmDelete = v) }
    }

    private fun setClosed(v: Boolean) {
        saved[CLOSED] = v
        local.update { it.copy(closed = v) }
    }

    private companion object {
        const val CONFIRM = "confirmDelete"
        const val CLOSED = "closed"
    }
}
