package com.kurtz.homerecord.fieldlog.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.JobFileDao
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.photo.JobFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A file picked from the device, already copied into app storage. */
data class ImportedFile(val name: String, val path: String, val mime: String, val size: Long)

class FilesDeps(
    val jobId: String,
    val dao: JobFileDao,
    val files: JobFiles,
    val author: String?,
    /** Queues the stored bytes for upload to R2 (AC-F19); a no-op when no company is signed in. */
    val queueUpload: suspend (JobFileEntity) -> Unit = {},
)

/** The job's Files tab: folders, search by name, add, rename, delete (AC-F19, AC-F23). */
class FilesViewModel(private val d: FilesDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val folder = MutableStateFlow(saved.get<String>(FOLDER).orEmpty())
    private val query = MutableStateFlow(saved.get<String>(QUERY).orEmpty())
    private val dialog = MutableStateFlow<FilesDialog?>(null)
    private val notice = MutableStateFlow<FilesNotice?>(null)

    val state: StateFlow<FilesUiState> = combine(d.dao.observeFor(d.jobId), folder, query, dialog, notice) { rows, f, q, dlg, note ->
        val live = rows.filter { it.mime != JobFiles.FOLDER_MIME }
        val here = f.prefixOf()
        val subs = rows.map { it.folder }.filter { it.startsWith(here) && it != f }.map { it.removePrefix(here).substringBefore('/') }.distinct().sorted()
        FilesUiState(
            loading = false, folder = f, folders = subs,
            files = live.filter { it.folder == f }.sortedBy { it.name.lowercase() }.map { it.row() },
            query = q,
            results = if (q.isBlank()) emptyList() else live.filter { it.name.lowercase().contains(q.trim().lowercase()) }.sortedBy { it.name.lowercase() }.map { it.row() },
            dialog = dlg, notice = note,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState())

    fun onEvent(e: FilesEvent) {
        when (e) {
            FilesEvent.AddFile -> Unit
            FilesEvent.NewFolder -> dialog.value = FilesDialog.NewFolder
            is FilesEvent.CreateFolder -> viewModelScope.launch {
                dialog.value = null
                if (!d.files.createFolder(d.jobId, folder.value, e.name)) notice.value = FilesNotice.FolderExists
            }
            is FilesEvent.OpenFolder -> setFolder(if (folder.value.isEmpty()) e.name else "${folder.value}/${e.name}")
            FilesEvent.Up -> setFolder(folder.value.substringBeforeLast('/', ""))
            is FilesEvent.Query -> { query.value = e.text; saved[QUERY] = e.text }
            is FilesEvent.OpenFile -> Unit
            is FilesEvent.AskRename -> viewModelScope.launch { d.dao.get(e.id)?.let { dialog.value = FilesDialog.Rename(it.id, it.name) } }
            is FilesEvent.Rename -> viewModelScope.launch { dialog.value = null; d.files.rename(e.id, e.name) }
            is FilesEvent.AskDelete -> viewModelScope.launch { d.dao.get(e.id)?.let { dialog.value = FilesDialog.Delete(it.id, it.name) } }
            is FilesEvent.Delete -> viewModelScope.launch { dialog.value = null; d.files.delete(e.id) }
            FilesEvent.DismissDialog -> dialog.value = null
            FilesEvent.DismissNotice -> notice.value = null
        }
    }

    /** Stores a picked file in the open folder and queues its upload. Null result from the importer shows a notice. */
    fun added(file: ImportedFile?) {
        if (file == null) { notice.value = FilesNotice.AddFailed; return }
        viewModelScope.launch {
            val row = d.files.add(d.jobId, folder.value, file.name, file.path, file.mime, file.size, d.author)
            d.queueUpload(row)
        }
    }

    private fun setFolder(path: String) { folder.value = path; saved[FOLDER] = path }

    private fun String.prefixOf() = if (isEmpty()) "" else "$this/"
    private fun JobFileEntity.row() = FileRow(id, name, folder, mime, size, offline)

    private companion object {
        const val FOLDER = "folder"
        const val QUERY = "query"
    }
}
