package com.kurtz.homerecord.fieldlog.files

/** One file row on the Files tab (AC-F19, AC-F23). */
data class FileRow(
    val id: String,
    val name: String,
    val folder: String,
    val mime: String,
    val size: Long,
    val offline: Boolean,
    val upload: com.kurtz.homerecord.core.photo.UploadStatus = com.kurtz.homerecord.core.photo.UploadStatus.None,
)

/** A pending rename or delete shown as a dialog. */
sealed interface FilesDialog {
    data object NewFolder : FilesDialog
    data class Rename(val id: String, val name: String) : FilesDialog
    data class Delete(val id: String, val name: String) : FilesDialog
}

data class FilesUiState(
    val loading: Boolean = true,
    /** Path of the open folder, "" for the top. */
    val folder: String = "",
    val folders: List<String> = emptyList(),
    val files: List<FileRow> = emptyList(),
    val query: String = "",
    /** Rows matching [query] across all folders; only used while [query] is not blank. */
    val results: List<FileRow> = emptyList(),
    val dialog: FilesDialog? = null,
    val notice: FilesNotice? = null,
)

enum class FilesNotice { FolderExists, AddFailed, DownloadFailed }

sealed interface FilesEvent {
    data object AddFile : FilesEvent
    data object NewFolder : FilesEvent
    data class CreateFolder(val name: String) : FilesEvent
    data class OpenFolder(val name: String) : FilesEvent
    data object Up : FilesEvent
    data class Query(val text: String) : FilesEvent
    data class OpenFile(val id: String) : FilesEvent
    data class ToggleOffline(val id: String, val on: Boolean) : FilesEvent
    data class AskRename(val id: String) : FilesEvent
    data class Rename(val id: String, val name: String) : FilesEvent
    data class AskDelete(val id: String) : FilesEvent
    data class Delete(val id: String) : FilesEvent
    data object DismissDialog : FilesEvent
    data object DismissNotice : FilesEvent
}
