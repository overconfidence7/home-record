package com.kurtz.homerecord.fieldlog.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.pages.PageSources
import com.kurtz.homerecord.core.data.JobFileDao
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.photo.OnPhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
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
    /** Every version of the file, newest first (PdfMarkup.history). */
    val history: suspend (fileId: String) -> List<JobFileEntity> = { emptyList() },
    /** A member's name from their id, for the history sheet. */
    val memberName: suspend (memberId: String) -> String? = { null },
    /** Whether this person may change the job's files (RoleGates.canManageFiles). */
    val canManage: (jobId: String) -> Flow<Boolean> = { flowOf(false) },
)

/**
 * FL-File: a job file opened in the app, PDF pages or a photo with pinch zoom (AC-F19); Mark up for a PDF and the
 * version history sheet, each version opening in its own viewer (AC-F21).
 */
class FileViewerViewModel(private val d: FileViewerDeps) : ViewModel() {
    private val _state = MutableStateFlow(FileViewerUiState())
    val state: StateFlow<FileViewerUiState> = _state.asStateFlow()
    private var gate: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    fun onRetry() {
        _state.value = FileViewerUiState()
        viewModelScope.launch { load() }
    }

    fun onShowHistory(show: Boolean) = _state.update { it.copy(showHistory = show) }

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
        val versions = d.history(row.id).map { v ->
            FileVersion(v.id, v.version, v.author?.let { d.memberName(it) }, v.createdAt, shown = v.id == row.id)
        }
        val markable = row.mime == PDF && pages != null
        _state.value = FileViewerUiState(
            loading = false, name = row.name, path = path.orEmpty(), mime = row.mime, pages = pages, problem = problem, versions = versions,
        )
        gate?.cancel()
        if (markable) {
            gate = viewModelScope.launch { d.canManage(row.jobId).collect { can -> _state.update { it.copy(canMarkUp = can) } } }
        }
    }

    override fun onCleared() {
        _state.value.pages?.close()
    }

    companion object {
        const val PDF = "application/pdf"
    }
}
