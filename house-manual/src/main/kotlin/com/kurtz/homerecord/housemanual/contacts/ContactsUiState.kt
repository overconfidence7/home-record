package com.kurtz.homerecord.housemanual.contacts

import com.kurtz.homerecord.housemodel.upkeep.ContactDraft
import com.kurtz.homerecord.housemodel.upkeep.ContactRow

data class ContactsUiState(
    val loading: Boolean = true,
    val rows: List<ContactRow> = emptyList(),
    val form: ContactDraft? = null,
    val showFormErrors: Boolean = false,
    val confirmDeleteId: String? = null,
)

sealed interface ContactsEvent {
    data object Back : ContactsEvent
    data class Call(val phone: String) : ContactsEvent
    data class Email(val address: String) : ContactsEvent
    data class OpenForm(val id: String?) : ContactsEvent
    data class ChangeForm(val draft: ContactDraft) : ContactsEvent
    data object SaveForm : ContactsEvent
    data object CloseForm : ContactsEvent
    data class AskDelete(val id: String) : ContactsEvent
    data object CancelDelete : ContactsEvent
    data object ConfirmDelete : ContactsEvent
}
