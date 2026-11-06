package com.kurtz.homerecord.housemanual.documents

import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.pages.PageSources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.photo.OnPhone
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads one document for the in-app viewer (AC-C37). A document added on the home's other phone has no file here
 * yet: [fetch] brings it down from the cloud first (T075a2, AC-H15), and with no connection the viewer says so and
 * offers Retry. [open] is swapped for a fake in tests.
 */
class DocumentViewerViewModel(
    private val repo: DocumentRepository,
    private val documentId: String,
    private val open: (path: String, mime: String) -> PageSource? = PageSources::open,
    private val fetch: suspend (documentId: String) -> OnPhone = { OnPhone.NotUploaded },
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentViewerUiState())
    val state: StateFlow<DocumentViewerUiState> = _state

    init {
        load()
    }

    fun onEvent(event: DocumentViewerEvent) {
        if (event == DocumentViewerEvent.Retry && !_state.value.loading) load()
    }

    private fun load() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            var doc = repo.find(documentId)
            var pages = doc?.let { openPages(it.path, it.mime) }
            var fetched: OnPhone? = null
            if (doc != null && pages == null) {
                fetched = fetch(documentId)
                if (fetched == OnPhone.Ready) {
                    doc = repo.find(documentId)
                    pages = doc?.let { openPages(it.path, it.mime) }
                }
            }
            _state.value = if (doc == null || pages == null) {
                DocumentViewerUiState(loading = false, name = doc?.name.orEmpty(), missing = true, offline = fetched == OnPhone.NoNetwork)
            } else {
                DocumentViewerUiState(loading = false, name = doc.name, path = doc.path, mime = doc.mime, pages = pages)
            }
        }
    }

    private suspend fun openPages(path: String, mime: String): PageSource? =
        if (path.isEmpty()) null else withContext(Dispatchers.IO) { open(path, mime) }

    override fun onCleared() {
        _state.value.pages?.close()
    }
}
