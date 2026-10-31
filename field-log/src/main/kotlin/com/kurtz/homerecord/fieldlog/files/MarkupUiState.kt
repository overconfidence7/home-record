package com.kurtz.homerecord.fieldlog.files

import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.core.photo.PdfMark
import com.kurtz.homerecord.core.photo.PdfTool
import com.kurtz.homerecord.coreui.pages.PageSource

/** Why a file cannot be marked up. */
enum class MarkupProblem {
    /** The record is gone or deleted. */
    Missing,
    /** Not a PDF, or its bytes are not on this phone. */
    NotAvailable,
    /** The PDF is on the phone but could not be opened. */
    Unreadable,
}

/** FL-Markup: marks drawn over the pages of a PDF, saved as a new version (AC-F21). */
data class MarkupUiState(
    val loading: Boolean = true,
    val problem: MarkupProblem? = null,
    val name: String = "",
    val pages: PageSource? = null,
    /** The page shown, 0-based. */
    val page: Int = 0,
    val tool: PdfTool = PdfTool.Pen,
    /** Marks on every page, oldest first. */
    val marks: List<PdfMark> = emptyList(),
    /** Where a text mark goes, waiting for its words. */
    val pendingText: NormPoint? = null,
    val pendingValue: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
    /** The new version's id once it is saved. */
    val savedId: String? = null,
) {
    val pageCount: Int get() = pages?.pageCount ?: 0
    val pageMarks: List<PdfMark> get() = marks.filter { it.page == page }
    val canUndo: Boolean get() = marks.any { it.page == page }
    val canSave: Boolean get() = marks.isNotEmpty() && !saving && savedId == null
}

sealed interface MarkupEvent {
    data class Tool(val tool: PdfTool) : MarkupEvent
    /** A finished gesture: the touch points as fractions of the page. */
    data class Gesture(val points: List<NormPoint>) : MarkupEvent
    data class PendingValue(val text: String) : MarkupEvent
    data object ConfirmText : MarkupEvent
    data object CancelText : MarkupEvent
    data object Undo : MarkupEvent
    data object PreviousPage : MarkupEvent
    data object NextPage : MarkupEvent
    data object Save : MarkupEvent
    data object Close : MarkupEvent
}
