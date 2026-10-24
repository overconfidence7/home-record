package com.kurtz.homerecord.housemanual.documents

import com.kurtz.homerecord.housemodel.documents.DocumentFolder

/** The rename / move sheet for one document. */
data class DocumentForm(val id: String, val name: String, val folder: String)

data class DocumentsUiState(
    val loading: Boolean = true,
    val folders: List<DocumentFolder> = emptyList(),
    val form: DocumentForm? = null,
    val showFormErrors: Boolean = false,
    val confirmDeleteId: String? = null,
    /** True after a picked file turned out not to be a PDF or image. */
    val unsupportedNotice: Boolean = false,
)

sealed interface DocumentsEvent {
    data object Back : DocumentsEvent
    data object Upload : DocumentsEvent
    data object Scan : DocumentsEvent
    data object ScanFailed : DocumentsEvent
    data class Picked(val uri: String) : DocumentsEvent
    data object DismissNotice : DocumentsEvent
    data class OpenForm(val id: String) : DocumentsEvent
    data class ChangeForm(val form: DocumentForm) : DocumentsEvent
    data object SaveForm : DocumentsEvent
    data object CloseForm : DocumentsEvent
    data class AskDelete(val id: String) : DocumentsEvent
    data object CancelDelete : DocumentsEvent
    data object ConfirmDelete : DocumentsEvent
}
