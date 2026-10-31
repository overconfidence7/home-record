package com.kurtz.homerecord.fieldlog.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.JobFileDao
import com.kurtz.homerecord.core.photo.PdfMark
import com.kurtz.homerecord.core.photo.PdfMarkSketch
import com.kurtz.homerecord.core.photo.PdfMarkup
import com.kurtz.homerecord.core.photo.PdfSketchResult
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.pages.PageSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MarkupDeps(
    val fileId: String,
    val dao: JobFileDao,
    val markup: PdfMarkup,
    /** The signed-in member's id, stored as the new version's author. */
    val author: String?,
    /** Opens a stored PDF as pages; tests pass a fake. */
    val openPages: (path: String, mime: String) -> PageSource? = PageSources::open,
    val isOnPhone: (path: String) -> Boolean = { File(it).isFile },
)

/** FL-Markup: pen, arrow, box, text and highlight on a PDF's pages, with undo, saved as a new version (AC-F21). */
class MarkupViewModel(private val d: MarkupDeps) : ViewModel() {
    private val _state = MutableStateFlow(MarkupUiState())
    val state: StateFlow<MarkupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val row = d.dao.get(d.fileId)
        if (row == null) {
            _state.value = MarkupUiState(loading = false, problem = MarkupProblem.Missing)
            return
        }
        val path = row.path
        val available = row.mime == PdfMarkup.PDF_MIME && path != null && withContext(Dispatchers.IO) { d.isOnPhone(path) }
        val pages = if (available && path != null) withContext(Dispatchers.IO) { d.openPages(path, row.mime) } else null
        val problem = when {
            !available -> MarkupProblem.NotAvailable
            pages == null || pages.pageCount == 0 -> MarkupProblem.Unreadable
            else -> null
        }
        _state.value = MarkupUiState(loading = false, problem = problem, name = row.name, pages = pages)
    }

    fun onEvent(event: MarkupEvent) {
        when (event) {
            is MarkupEvent.Tool -> _state.update { it.copy(tool = event.tool) }
            is MarkupEvent.Gesture -> {
                val s = _state.value
                when (val r = PdfMarkSketch.finish(s.tool, s.page, event.points)) {
                    is PdfSketchResult.Mark -> add(r.mark)
                    is PdfSketchResult.AskText -> _state.update { it.copy(pendingText = r.at, pendingValue = "") }
                    PdfSketchResult.Nothing -> Unit
                }
            }
            is MarkupEvent.PendingValue -> _state.update { it.copy(pendingValue = event.text) }
            MarkupEvent.ConfirmText -> {
                val s = _state.value
                val at = s.pendingText
                val words = s.pendingValue.trim()
                if (at != null && words.isNotEmpty()) add(PdfMark.Text(s.page, at, words))
                _state.update { it.copy(pendingText = null, pendingValue = "") }
            }
            MarkupEvent.CancelText -> _state.update { it.copy(pendingText = null, pendingValue = "") }
            MarkupEvent.Undo -> _state.update { s ->
                val last = s.marks.indexOfLast { it.page == s.page }
                if (last < 0) s else s.copy(marks = s.marks.filterIndexed { i, _ -> i != last })
            }
            MarkupEvent.PreviousPage -> _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
            MarkupEvent.NextPage -> _state.update { it.copy(page = (it.page + 1).coerceAtMost((it.pageCount - 1).coerceAtLeast(0))) }
            MarkupEvent.Save -> save()
            MarkupEvent.Close -> Unit // the route navigates
        }
    }

    private fun add(mark: PdfMark) = _state.update { it.copy(marks = it.marks + mark, failed = false) }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val id = d.markup.saveVersion(d.fileId, s.marks, d.author)
            _state.update { if (id != null) it.copy(saving = false, savedId = id) else it.copy(saving = false, failed = true) }
        }
    }

    override fun onCleared() {
        _state.value.pages?.close()
    }
}
