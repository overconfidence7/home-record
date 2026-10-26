package com.kurtz.homerecord.fieldlog.photos

import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotationTool
import com.kurtz.homerecord.core.photo.NormPoint

/** A text or measure mark waiting for its typed value. */
sealed interface PendingMark {
    data class Text(val at: NormPoint) : PendingMark
    data class Measure(val from: NormPoint, val to: NormPoint) : PendingMark
}

/** FL-Annotate: marks drawn over one photo, saved as a copy (AC-F26). */
data class AnnotateUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val imagePath: String = "",
    /** Photo width over height, so marks line up with the picture. */
    val aspect: Float = 4f / 3f,
    val tool: AnnotationTool = AnnotationTool.Arrow,
    val marks: List<Annotation> = emptyList(),
    val pending: PendingMark? = null,
    val pendingText: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
) {
    val canUndo: Boolean get() = marks.isNotEmpty()
    val canSave: Boolean get() = marks.isNotEmpty() && !saving
}

sealed interface AnnotateEvent {
    data class Tool(val tool: AnnotationTool) : AnnotateEvent
    /** A finished gesture: the touch points as fractions of the photo. */
    data class Gesture(val points: List<NormPoint>) : AnnotateEvent
    data class PendingText(val text: String) : AnnotateEvent
    data object ConfirmPending : AnnotateEvent
    data object CancelPending : AnnotateEvent
    data object Undo : AnnotateEvent
    data object Save : AnnotateEvent
}
