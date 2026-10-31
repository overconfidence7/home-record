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
    /** A Lead, Office or Owner may mark up a PDF that is on the phone (AC-F21, AC-F3). */
    val canMarkUp: Boolean = false,
    /** Every version of this file, newest first; one entry when it was never marked up (AC-F21). */
    val versions: List<FileVersion> = emptyList(),
    val showHistory: Boolean = false,
)

/** One version in the history sheet: who saved it and when; [shown] is the one open in the viewer. */
data class FileVersion(val id: String, val version: Int, val author: String?, val createdAt: Long, val shown: Boolean)

sealed interface FileViewerEvent {
    data object Share : FileViewerEvent
    data object Retry : FileViewerEvent
    data object Back : FileViewerEvent
    data object MarkUp : FileViewerEvent
    data object ShowHistory : FileViewerEvent
    data object HideHistory : FileViewerEvent
    data class OpenVersion(val id: String) : FileViewerEvent
}
