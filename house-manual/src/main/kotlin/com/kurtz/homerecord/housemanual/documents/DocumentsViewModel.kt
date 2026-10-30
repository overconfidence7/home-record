package com.kurtz.homerecord.housemanual.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import com.kurtz.homerecord.housemodel.documents.DocumentScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Documents (AC-C37). The open rename sheet lives in [SavedStateHandle] so it survives process death. */
class DocumentsViewModel(
    private val repo: DocumentRepository,
    private val homeId: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val ui = MutableStateFlow(Ui(form = restoreForm(), showErrors = saved[ERRORS] ?: false))

    val state: StateFlow<DocumentsUiState> = combine(repo.observeFolders(homeId), ui) { folders, u ->
        DocumentsUiState(
            loading = false, folders = folders, form = u.form, showFormErrors = u.showErrors,
            confirmDeleteId = u.confirmDeleteId, unsupportedNotice = u.unsupported, addMenuOpen = u.addMenuOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

    fun onEvent(e: DocumentsEvent) {
        when (e) {
            is DocumentsEvent.Picked -> viewModelScope.launch {
                val id = repo.add(homeId, DocumentScope.Home, e.uri)
                ui.update { it.copy(unsupported = id == null) }
            }
            DocumentsEvent.DismissNotice -> ui.update { it.copy(unsupported = false) }
            is DocumentsEvent.OpenForm -> {
                val doc = state.value.folders.flatMap { it.documents }.firstOrNull { it.id == e.id } ?: return
                setForm(DocumentForm(doc.id, doc.name, doc.folder), false)
            }
            is DocumentsEvent.ChangeForm -> setForm(e.form, ui.value.showErrors)
            DocumentsEvent.SaveForm -> viewModelScope.launch {
                val form = ui.value.form ?: return@launch
                if (!repo.rename(form.id, form.name)) {
                    setForm(form, true)
                } else {
                    repo.moveToFolder(form.id, form.folder)
                    setForm(null, false)
                }
            }
            DocumentsEvent.CloseForm -> setForm(null, false)
            is DocumentsEvent.AskDelete -> ui.update { it.copy(confirmDeleteId = e.id) }
            DocumentsEvent.CancelDelete -> ui.update { it.copy(confirmDeleteId = null) }
            DocumentsEvent.ConfirmDelete -> viewModelScope.launch {
                ui.value.confirmDeleteId?.let { repo.delete(it) }
                ui.update { it.copy(confirmDeleteId = null) }
                setForm(null, false)
            }
            DocumentsEvent.ScanFailed -> ui.update { it.copy(unsupported = true) }
            is DocumentsEvent.SetAddMenu -> ui.update { it.copy(addMenuOpen = e.open) }
            // The route launches the picker or scanner; the menu closes either way.
            DocumentsEvent.Upload, DocumentsEvent.Scan -> ui.update { it.copy(addMenuOpen = false) }
            is DocumentsEvent.Open, DocumentsEvent.Back -> Unit // the route's job
        }
    }

    private fun setForm(form: DocumentForm?, errors: Boolean) {
        ui.update { it.copy(form = form, showErrors = errors) }
        saved[ERRORS] = errors
        if (form == null) saved.remove<List<String>>(FORM) else saved[FORM] = arrayListOf(form.id, form.name, form.folder)
    }

    private fun restoreForm(): DocumentForm? =
        saved.get<List<String>>(FORM)?.takeIf { it.size == 3 }?.let { DocumentForm(it[0], it[1], it[2]) }

    private data class Ui(
        val form: DocumentForm? = null,
        val showErrors: Boolean = false,
        val confirmDeleteId: String? = null,
        val unsupported: Boolean = false,
        val addMenuOpen: Boolean = false,
    )

    private companion object {
        const val FORM = "form"; const val ERRORS = "errors"
    }
}
