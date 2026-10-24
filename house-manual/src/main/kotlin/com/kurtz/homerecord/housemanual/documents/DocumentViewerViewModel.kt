package com.kurtz.homerecord.housemanual.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads one document for the in-app viewer (AC-C37). [open] is swapped for a fake in tests. */
class DocumentViewerViewModel(
    private val repo: DocumentRepository,
    private val documentId: String,
    private val open: (path: String, mime: String) -> PageSource? = PageSources::open,
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentViewerUiState())
    val state: StateFlow<DocumentViewerUiState> = _state

    init {
        viewModelScope.launch {
            val doc = repo.find(documentId)
            val pages = doc?.let { withContext(Dispatchers.IO) { open(it.path, it.mime) } }
            _state.value = if (doc == null || pages == null) {
                DocumentViewerUiState(loading = false, name = doc?.name.orEmpty(), missing = true)
            } else {
                DocumentViewerUiState(loading = false, name = doc.name, path = doc.path, mime = doc.mime, pages = pages)
            }
        }
    }

    override fun onCleared() {
        _state.value.pages?.close()
    }
}
