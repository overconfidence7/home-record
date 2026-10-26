package com.kurtz.homerecord.fieldlog.files

import com.kurtz.homerecord.coreui.pages.PageSource

/** What the viewer can show for a file (AC-F19). */
enum class FileViewerProblem {
    /** The record is gone or deleted. */
    Missing,
    /** The bytes are not on this phone yet. */
    NotOnPhone,
    /** The file is neither a PDF nor a photo, so it can only be shared. */
    NoPreview,
    /** The file is on the phone but could not be read. */
    Unreadable,
}

data class FileViewerUiState(
    val loading: Boolean = true,
    val name: String = "",
    val path: String = "",
    val mime: String = "",
    val pages: PageSource? = null,
    val problem: FileViewerProblem? = null,
)

sealed interface FileViewerEvent {
    data object Share : FileViewerEvent
    data object Back : FileViewerEvent
}
