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
    /** The job the file belongs to; tasks pinned here open in that job (AC-F35). */
    val jobId: String? = null,
    /** Open tasks pinned on this file (any version), numbered in the crew's order; Crew see only their own (AC-F35). */
    val pins: List<PagePin> = emptyList(),
    /** Owner, Office and the job's Lead pin tasks on a page (RoleGates.canManageTasks, AC-F3). */
    val canPinTasks: Boolean = false,
    /** "Pin a task" is on: the next tap on a page marks where the work is. */
    val pinMode: Boolean = false,
    /** Where the page was tapped in pin mode; the task chooser is open while this is set. */
    val spot: PageSpot? = null,
    /** The job's open tasks, for the chooser, in the crew's order. */
    val choices: List<TaskChoice> = emptyList(),
    /** The pin could not be saved (the task was deleted meanwhile). */
    val pinFailed: Boolean = false,
) {
    val canStartPinning: Boolean get() = canPinTasks && pages != null && jobId != null
}

/** A task pin on a page: [page] 0-based, [x] and [y] fractions of the page (AC-F35). */
data class PagePin(val taskId: String, val number: Int, val title: String, val page: Int, val x: Float, val y: Float)

/** A tapped point on a page, fractions of its width and height. */
data class PageSpot(val page: Int, val x: Float, val y: Float)

/** An open task in the "Pin which task?" sheet; [pinnedHere] when it already has a pin on this file (choosing it moves the pin). */
data class TaskChoice(val id: String, val title: String, val pinnedHere: Boolean)

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
    /** "Pin a task" in the top bar (AC-F35). */
    data object StartPinning : FileViewerEvent
    data object CancelPinning : FileViewerEvent
    /** A tap on page [page] (0-based) at fractions [x], [y]; only acts in pin mode. */
    data class TapPage(val page: Int, val x: Float, val y: Float) : FileViewerEvent
    /** Pins an existing task at the tapped spot. */
    data class ChooseTask(val id: String) : FileViewerEvent
    /** "New task here": the route opens the task form with the spot filled in. */
    data object NewTaskHere : FileViewerEvent
    data object DismissChooser : FileViewerEvent
    data object DismissPinFailed : FileViewerEvent
    /** A tap on a pin: the task form for those who manage tasks, the task read only for Crew (R09g). */
    data class OpenPin(val taskId: String) : FileViewerEvent
}
