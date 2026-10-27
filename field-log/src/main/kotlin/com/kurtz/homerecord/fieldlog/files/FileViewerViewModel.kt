package com.kurtz.homerecord.fieldlog.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.pages.PageSources
import com.kurtz.homerecord.core.data.JobFileDao
import com.kurtz.homerecord.core.photo.OnPhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileViewerDeps(
    val fileId: String,
    val dao: JobFileDao,
    /** Opens a stored PDF or photo as pages; tests pass a fake. */
    val openPages: (path: String, mime: String) -> PageSource? = PageSources::open,
    /** Downloads the bytes when they are not on the phone (AC-F20); tests pass a fake. */
    val ensureOnPhone: suspend (fileId: String) -> OnPhone = { OnPhone.Ready },
)

/** FL-File: a job file opened in the app, PDF pages or a photo with pinch zoom (AC-F19). */
class FileViewerViewModel(private val d: FileViewerDeps) : ViewModel() {
    private val _state = MutableStateFlow(FileViewerUiState())
    val state: StateFlow<FileViewerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onRetry() {
        _state.value = FileViewerUiState()
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        var row = d.dao.get(d.fileId)
        if (row != null && !withContext(Dispatchers.IO) { row.path?.let { File(it).isFile } == true }) {
            // Not on the phone: fetch it now; a file never marked Available offline downloads on open.
            d.ensureOnPhone(d.fileId)
            row = d.dao.get(d.fileId)
        }
        if (row == null) {
            _state.value = FileViewerUiState(loading = false, problem = FileViewerProblem.Missing)
            return
        }
        val path = row.path
        val onPhone = path != null && withContext(Dispatchers.IO) { File(path).isFile }
        val previewable = row.mime == PDF || row.mime.startsWith("image/")
        val pages = if (onPhone && previewable && path != null) withContext(Dispatchers.IO) { d.openPages(path, row.mime) } else null
        val problem = when {
            !onPhone -> FileViewerProblem.NotOnPhone
            !previewable -> FileViewerProblem.NoPreview
            pages == null -> FileViewerProblem.Unreadable
            else -> null
        }
        _state.value = FileViewerUiState(loading = false, name = row.name, path = path.orEmpty(), mime = row.mime, pages = pages, problem = problem)
    }

    override fun onCleared() {
        _state.value.pages?.close()
    }

    companion object {
        const val PDF = "application/pdf"
    }
}
