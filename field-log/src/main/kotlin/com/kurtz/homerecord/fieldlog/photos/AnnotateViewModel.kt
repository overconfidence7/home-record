package com.kurtz.homerecord.fieldlog.photos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.PhotoDao
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotationCodec
import com.kurtz.homerecord.core.photo.AnnotationEditor
import com.kurtz.homerecord.core.photo.AnnotationTool
import com.kurtz.homerecord.core.photo.MarkSketch
import com.kurtz.homerecord.core.photo.PhotoAnnotator
import com.kurtz.homerecord.core.photo.SketchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnnotateDeps(
    val photoId: String,
    val photos: PhotoDao,
    val annotator: PhotoAnnotator,
    /** Width over height of the image file, or null when it cannot be read. */
    val aspectOf: suspend (path: String) -> Float?,
)

/**
 * FL-Annotate: draws arrows, circles, rectangles, freehand, text and measure labels, with undo, then saves a copy (AC-F26).
 * The marks drawn so far and the chosen tool live in [saved], so a crew member who leaves for another app and comes back
 * after the system killed Field Log finds the markup where they left it (S5c, rule 33).
 */
class AnnotateViewModel(private val d: AnnotateDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private var editor = AnnotationEditor(saved.get<String>(KEY_MARKS)?.let(AnnotationCodec::decode).orEmpty())
    private val _state = MutableStateFlow(
        AnnotateUiState(
            marks = editor.marks,
            tool = saved.get<String>(KEY_TOOL)?.let { name -> AnnotationTool.entries.firstOrNull { it.name == name } } ?: AnnotationTool.Arrow,
        ),
    )
    val state: StateFlow<AnnotateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = d.photos.get(d.photoId)
            if (p == null || p.deletedAt != null) {
                _state.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            val aspect = d.aspectOf(p.path) ?: (4f / 3f)
            _state.update { it.copy(loading = false, imagePath = p.path, aspect = aspect) }
        }
    }

    fun onEvent(event: AnnotateEvent) {
        when (event) {
            is AnnotateEvent.Tool -> {
                saved[KEY_TOOL] = event.tool.name
                _state.update { it.copy(tool = event.tool) }
            }
            is AnnotateEvent.Gesture -> when (val r = MarkSketch.finish(_state.value.tool, event.points, _state.value.aspect)) {
                is SketchResult.Mark -> add(r.mark)
                is SketchResult.AskText -> _state.update { it.copy(pending = PendingMark.Text(r.at), pendingText = "") }
                is SketchResult.AskMeasure -> _state.update { it.copy(pending = PendingMark.Measure(r.from, r.to), pendingText = "") }
                SketchResult.Nothing -> Unit
            }
            is AnnotateEvent.PendingText -> _state.update { it.copy(pendingText = event.text) }
            AnnotateEvent.ConfirmPending -> {
                val s = _state.value
                when (val p = s.pending) {
                    is PendingMark.Text -> add(Annotation.Text(p.at, s.pendingText.trim()))
                    is PendingMark.Measure -> add(Annotation.Measure(p.from, p.to, s.pendingText.trim()))
                    null -> Unit
                }
                _state.update { it.copy(pending = null, pendingText = "") }
            }
            AnnotateEvent.CancelPending -> _state.update { it.copy(pending = null, pendingText = "") }
            AnnotateEvent.Undo -> {
                editor = editor.undo()
                saved[KEY_MARKS] = AnnotationCodec.encode(editor.marks)
                _state.update { it.copy(marks = editor.marks) }
            }
            AnnotateEvent.Save -> save()
        }
    }

    private fun add(mark: Annotation) {
        editor = editor.add(mark)
        saved[KEY_MARKS] = AnnotationCodec.encode(editor.marks)
        _state.update { it.copy(marks = editor.marks, failed = false) }
    }

    private fun save() {
        if (!_state.value.canSave) return
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val id = d.annotator.saveCopy(d.photoId, editor.marks)
            _state.update { if (id != null) it.copy(saving = false, saved = true) else it.copy(saving = false, failed = true) }
        }
    }
}

private const val KEY_MARKS = "annotate_marks"
private const val KEY_TOOL = "annotate_tool"
