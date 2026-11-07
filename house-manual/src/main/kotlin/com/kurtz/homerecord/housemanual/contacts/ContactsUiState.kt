package com.kurtz.homerecord.housemanual.contacts

import com.kurtz.homerecord.housemodel.upkeep.ContactDraft
import com.kurtz.homerecord.housemodel.upkeep.ContactRow

data class ContactsUiState(
    val loading: Boolean = true,
    /** The contacts could not be read; the screen shows what failed and Retry (AC-U4). */
    val loadFailed: Boolean = false,
    val rows: List<ContactRow> = emptyList(),
    val form: ContactDraft? = null,
    /** The open form differs from what it opened with: Save is enabled and Close asks before discarding. */
    val formDirty: Boolean = false,
    /** Save is on for a new contact (a tap shows what is missing, rule 21) and for an edit once something changed. */
    val formCanSave: Boolean = false,
    val showFormErrors: Boolean = false,
    val confirmDeleteId: String? = null,
    /** A viewer on a shared home: rows call and email only; no Add or edit (AC-H16). */
    val viewOnly: Boolean = false,
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
    data object Retry : ContactsEvent
}
