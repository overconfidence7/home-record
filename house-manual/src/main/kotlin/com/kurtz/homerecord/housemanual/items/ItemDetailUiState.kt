package com.kurtz.homerecord.housemanual.items

import com.kurtz.homerecord.housemodel.items.ItemDetail

data class ItemDetailUiState(
    val loading: Boolean = true,
    val detail: ItemDetail? = null,
    val confirmDelete: Boolean = false,
)

sealed interface ItemDetailEvent {
    data object Edit : ItemDetailEvent
    data object Duplicate : ItemDetailEvent
    data object AskDelete : ItemDetailEvent
    data object CancelDelete : ItemDetailEvent
    data object ConfirmDelete : ItemDetailEvent
    data class SetRegistered(val registered: Boolean) : ItemDetailEvent
    data class OpenLink(val url: String) : ItemDetailEvent
    data object Back : ItemDetailEvent
}
