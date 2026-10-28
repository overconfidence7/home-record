package com.kurtz.homerecord.housemanual.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.upkeep.ContactDraft
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Contacts (AC-C39). The open form and what it opened with live in [SavedStateHandle] so the draft and its dirty state survive process death. */
class ContactsViewModel(
    private val repo: UpkeepRepository,
    private val homeId: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val ui = MutableStateFlow(Ui(form = restore(FORM), original = restore(ORIGINAL), showErrors = saved[ERRORS] ?: false))

    val state: StateFlow<ContactsUiState> = combine(repo.observeContacts(homeId), ui) { rows, u ->
        ContactsUiState(loading = false, rows = rows, form = u.form, formDirty = u.form != null && u.form != u.original,
            formCanSave = u.form != null && (u.form.id == null || u.form != u.original), showFormErrors = u.showErrors, confirmDeleteId = u.confirmDeleteId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun onEvent(e: ContactsEvent) {
        when (e) {
            is ContactsEvent.OpenForm ->
                if (e.id == null) openForm(ContactDraft())
                else viewModelScope.launch { openForm(repo.draftFor(e.id) ?: ContactDraft()) }
            is ContactsEvent.ChangeForm -> if (ui.value.form != null) setForm(e.draft, ui.value.showErrors)
            ContactsEvent.SaveForm -> viewModelScope.launch {
                val draft = ui.value.form ?: return@launch
                if (repo.saveContact(homeId, draft) == null) setForm(draft, true) else setForm(null, false)
            }
            ContactsEvent.CloseForm -> setForm(null, false)
            is ContactsEvent.AskDelete -> ui.update { it.copy(confirmDeleteId = e.id) }
            ContactsEvent.CancelDelete -> ui.update { it.copy(confirmDeleteId = null) }
            ContactsEvent.ConfirmDelete -> viewModelScope.launch {
                ui.value.confirmDeleteId?.let { repo.deleteContact(it) }
                ui.update { it.copy(confirmDeleteId = null) }
                setForm(null, false)
            }
            ContactsEvent.Back, is ContactsEvent.Call, is ContactsEvent.Email -> Unit // the route's job
        }
    }

    private fun openForm(draft: ContactDraft) {
        ui.update { it.copy(original = draft) }
        store(ORIGINAL, draft)
        setForm(draft, false)
    }

    private fun setForm(draft: ContactDraft?, errors: Boolean) {
        ui.update { it.copy(form = draft, original = if (draft == null) null else it.original, showErrors = errors) }
        saved[ERRORS] = errors
        store(FORM, draft)
        if (draft == null) store(ORIGINAL, null)
    }

    private fun store(key: String, draft: ContactDraft?) {
        if (draft == null) saved.remove<List<String>>(key)
        else saved[key] = arrayListOf(draft.id.orEmpty(), draft.name, draft.company, draft.trade, draft.phone, draft.email, draft.notes)
    }

    private fun restore(key: String): ContactDraft? = saved.get<List<String>>(key)?.takeIf { it.size == 7 }?.let {
        ContactDraft(it[0].ifEmpty { null }, it[1], it[2], it[3], it[4], it[5], it[6])
    }

    private data class Ui(
        val form: ContactDraft? = null,
        val original: ContactDraft? = null,
        val showErrors: Boolean = false,
        val confirmDeleteId: String? = null,
    )

    private companion object {
        const val FORM = "form"; const val ORIGINAL = "formOriginal"; const val ERRORS = "errors"
    }
}
