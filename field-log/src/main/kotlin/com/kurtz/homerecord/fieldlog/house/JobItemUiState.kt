package com.kurtz.homerecord.fieldlog.house

import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemodel.items.ItemDetail

/** FL-Item: one item installed on the job, with make, model, serial and its warranty (AC-F50). */
data class JobItemUiState(
    val loading: Boolean = true,
    val detail: ItemDetail? = null,
    /** Owner, Office and Lead edit the job's house; Crew and Sub read it (backend policy.ts JOB_HOUSE). */
    val canEdit: Boolean = false,
    val confirmDelete: Boolean = false,
    val lookupBusy: Boolean = false,
    val lookupNote: JobItemLookupNote? = null,
    /** Terms waiting for the user to confirm, edit or discard (AC-C34). */
    val review: WarrantyTerms? = null,
    /** Set once the item is deleted; the route goes back and answers with [JobItemEvent.ClosedHandled]. */
    val closed: Boolean = false,
)

enum class JobItemLookupNote { NeedsMakeModel, Failed, Queued }

sealed interface JobItemEvent {
    data object Back : JobItemEvent
    data object Edit : JobItemEvent
    /** The Where row: Place on plan (AC-C30 position). */
    data object Place : JobItemEvent
    data object AskDelete : JobItemEvent
    data object CancelDelete : JobItemEvent
    data object ConfirmDelete : JobItemEvent
    /** "Look up warranty" ([again] false) or "Look up again" ([again] true). */
    data class LookUp(val again: Boolean) : JobItemEvent
    data object OpenReview : JobItemEvent
    data class ConfirmTerms(val partsMonths: Int?, val laborMonths: Int?) : JobItemEvent
    data object DiscardTerms : JobItemEvent
    data class SetRegistered(val registered: Boolean) : JobItemEvent
    data class OpenLink(val url: String) : JobItemEvent
    data object ClosedHandled : JobItemEvent
}
