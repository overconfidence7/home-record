package com.kurtz.homerecord.housemanual.items

import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemodel.items.ItemDetail

data class ItemDetailUiState(
    val loading: Boolean = true,
    val detail: ItemDetail? = null,
    val confirmDelete: Boolean = false,
    val lookupBusy: Boolean = false,
    val lookupNote: LookupNote? = null,
    /** Terms waiting for the user to confirm, edit or discard (AC-C34). */
    val review: WarrantyTerms? = null,
)

enum class LookupNote { NeedsMakeModel, Failed, Queued }

sealed interface ItemDetailEvent {
    data object Edit : ItemDetailEvent
    data object Duplicate : ItemDetailEvent
    data object AskDelete : ItemDetailEvent
    data object CancelDelete : ItemDetailEvent
    data object ConfirmDelete : ItemDetailEvent
    /** "Look up warranty" ([again] false) or "Look up again" ([again] true). */
    data class LookUp(val again: Boolean) : ItemDetailEvent
    data object OpenReview : ItemDetailEvent
    data class ConfirmTerms(val partsMonths: Int?, val laborMonths: Int?) : ItemDetailEvent
    data object DiscardTerms : ItemDetailEvent
    data class SetRegistered(val registered: Boolean) : ItemDetailEvent
    data class OpenLink(val url: String) : ItemDetailEvent
    data object Back : ItemDetailEvent
}
