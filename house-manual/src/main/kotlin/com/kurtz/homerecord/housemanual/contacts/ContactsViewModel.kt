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

/** Contacts (AC-C39). The open form lives in [SavedStateHandle] so it survives process death. */
class ContactsViewModel(
    private val repo: UpkeepRepository,
    private val homeId: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val ui = MutableStateFlow(Ui(form = restoreForm(), showErrors = saved[ERRORS] ?: false))

    val state: StateFlow<ContactsUiState> = combine(repo.observeContacts(homeId), ui) { rows, u ->
        ContactsUiState(loading = false, rows = rows, form = u.form, showFormErrors = u.showErrors, confirmDeleteId = u.confirmDeleteId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun onEvent(e: ContactsEvent) {
        when (e) {
            is ContactsEvent.OpenForm ->
                if (e.id == null) setForm(ContactDraft(), false)
                else viewModelScope.launch { setForm(repo.draftFor(e.id) ?: ContactDraft(), false) }
            is ContactsEvent.ChangeForm -> setForm(e.draft, ui.value.showErrors)
            ContactsEvent.SaveForm -> viewModelScope.launch {
                val draft = ui.value.form ?: return@launch
                if (repo.saveContact(homeId, draft) == null) setForm(draft, true) else setForm(null, false)
            }
            ContactsEvent.CloseForm -> setForm(null, false)
            is ContactsEvent.AskDelete -> ui.update { it.copy(confirmDeleteId = e.id) }
            ContactsEvent.CancelDelete -> ui.update { it.copy(confirmDeleteId = null) }
            ContactsEvent.ConfirmDelete -> viewModelScope.launch {
                ui.value.confirmDeleteId?.let { repo.deleteContact(it) }
                ui.update { it.copy(confirmDeleteId = null, form = null, showErrors = false) }
                saved.remove<List<String>>(FORM)
            }
            ContactsEvent.Back, is ContactsEvent.Call, is ContactsEvent.Email -> Unit // the route's job
        }
    }

    private fun setForm(draft: ContactDraft?, errors: Boolean) {
        ui.update { it.copy(form = draft, showErrors = errors) }
        saved[ERRORS] = errors
        if (draft == null) saved.remove<List<String>>(FORM)
        else saved[FORM] = arrayListOf(draft.id.orEmpty(), draft.name, draft.company, draft.trade, draft.phone, draft.email, draft.notes)
    }

    private fun restoreForm(): ContactDraft? = saved.get<List<String>>(FORM)?.takeIf { it.size == 7 }?.let {
        ContactDraft(it[0].ifEmpty { null }, it[1], it[2], it[3], it[4], it[5], it[6])
    }

    private data class Ui(val form: ContactDraft? = null, val showErrors: Boolean = false, val confirmDeleteId: String? = null)

    private companion object {
        const val FORM = "form"; const val ERRORS = "errors"
    }
}
