package com.kurtz.homerecord.housemanual.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.items.ItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Item page (AC-H7). The delete confirmation lives in [SavedStateHandle] so it survives rotation and process death. */
class ItemDetailViewModel(
    private val repo: ItemRepository,
    private val itemId: String,
    detail: Flow<ItemDetail?>,
    private val copySuffix: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val onClose: () -> Unit = {},
    private val onOpenCopy: (String) -> Unit = {},
) : ViewModel() {
    private val confirmDelete = MutableStateFlow(saved[CONFIRM] ?: false)

    val state: StateFlow<ItemDetailUiState> = combine(detail, confirmDelete) { d, c ->
        ItemDetailUiState(loading = false, detail = d, confirmDelete = c && d != null)
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
            ItemDetailEvent.Back, ItemDetailEvent.Edit, is ItemDetailEvent.OpenLink -> Unit // navigation is the route's job
        }
    }

    private fun setConfirm(v: Boolean) {
        confirmDelete.value = v
        saved[CONFIRM] = v
    }

    private companion object { const val CONFIRM = "confirmDelete" }
}
