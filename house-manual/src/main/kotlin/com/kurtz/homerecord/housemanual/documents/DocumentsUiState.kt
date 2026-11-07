package com.kurtz.homerecord.housemanual.documents

import com.kurtz.homerecord.housemodel.documents.DocumentFolder

/** The rename / move sheet for one document. */
data class DocumentForm(val id: String, val name: String, val folder: String)

data class DocumentsUiState(
    val loading: Boolean = true,
    /** The documents could not be read; the screen shows what failed and Retry (AC-U4). */
    val loadFailed: Boolean = false,
    val folders: List<DocumentFolder> = emptyList(),
    val form: DocumentForm? = null,
    val showFormErrors: Boolean = false,
    val confirmDeleteId: String? = null,
    /** True after a picked file turned out not to be a PDF or image. */
    val unsupportedNotice: Boolean = false,
    /** True while the FAB menu (Add a file, Scan a document) is open. */
    val addMenuOpen: Boolean = false,
    /** A viewer on a shared home: documents open; no Add, rename or move (AC-H16). */
    val viewOnly: Boolean = false,
)

sealed interface DocumentsEvent {
    data object Back : DocumentsEvent
    data object Upload : DocumentsEvent
    data object Scan : DocumentsEvent
    data class SetAddMenu(val open: Boolean) : DocumentsEvent
    data object ScanFailed : DocumentsEvent
    data class Picked(val uri: String) : DocumentsEvent
    data object DismissNotice : DocumentsEvent
    data class Open(val id: String) : DocumentsEvent
    data class OpenForm(val id: String) : DocumentsEvent
    data class ChangeForm(val form: DocumentForm) : DocumentsEvent
    data object SaveForm : DocumentsEvent
    data object CloseForm : DocumentsEvent
    data class AskDelete(val id: String) : DocumentsEvent
    data object CancelDelete : DocumentsEvent
    data object ConfirmDelete : DocumentsEvent
    data object Retry : DocumentsEvent
}
