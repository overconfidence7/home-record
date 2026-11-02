package com.kurtz.homerecord.fieldlog.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.pages.PageSources
import com.kurtz.homerecord.core.data.JobFileDao
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.model.JobTask
import com.kurtz.homerecord.core.model.TaskFilePin
import com.kurtz.homerecord.core.model.TaskList
import com.kurtz.homerecord.core.model.TaskPins
import com.kurtz.homerecord.core.photo.OnPhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    /** The job's tasks (JobTasks.observeForJob), for the pins on this file (AC-F35). */
    val tasks: (jobId: String) -> Flow<List<JobTask>> = { flowOf(emptyList()) },
    /** Whether this person manages the job's tasks (RoleGates.canManageTasks). */
    val canManageTasks: (jobId: String) -> Flow<Boolean> = { flowOf(false) },
    /** The signed-in member, so Crew see their own pins. */
    val me: String? = null,
    /** Pins an existing task on this file (JobTasks.pinOnFile). */
    val pinOnFile: suspend (taskId: String, pin: TaskFilePin, fileJobId: String) -> Boolean = { _, _, _ -> false },
)

/**
 * FL-File: a job file opened in the app, PDF pages or a photo with pinch zoom (AC-F19); Mark up for a PDF and the
 * version history sheet, each version opening in its own viewer (AC-F21).
 */
class FileViewerViewModel(private val d: FileViewerDeps) : ViewModel() {
    private val _state = MutableStateFlow(FileViewerUiState())
    val state: StateFlow<FileViewerUiState> = _state.asStateFlow()
    private var gate: Job? = null
    private var pinsJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    fun onRetry() {
        _state.value = FileViewerUiState()
        viewModelScope.launch { load() }
    }

    fun onShowHistory(show: Boolean) = _state.update { it.copy(showHistory = show) }

    /** Pin-a-task events (AC-F35); [FileViewerEvent.NewTaskHere] and [FileViewerEvent.OpenPin] are the route's. */
    fun onPinEvent(e: FileViewerEvent) {
        when (e) {
            FileViewerEvent.StartPinning -> if (_state.value.canStartPinning) _state.update { it.copy(pinMode = true, spot = null) }
            FileViewerEvent.CancelPinning -> _state.update { it.copy(pinMode = false, spot = null) }
            is FileViewerEvent.TapPage -> if (_state.value.pinMode) {
                val pin = TaskFilePin.at(d.fileId, e.page, e.x, e.y)
                _state.update { it.copy(spot = PageSpot(pin.page, pin.x, pin.y)) }
            }
            FileViewerEvent.DismissChooser -> _state.update { it.copy(spot = null) }
            FileViewerEvent.DismissPinFailed -> _state.update { it.copy(pinFailed = false) }
            is FileViewerEvent.ChooseTask -> choose(e.id)
            else -> Unit
        }
    }

    /** The spot as a pin on this file, for "New task here". */
    fun spotPin(): TaskFilePin? = _state.value.spot?.let { TaskFilePin(d.fileId, it.page, it.x, it.y) }

    /** Leaves pin mode once "New task here" has opened the form. */
    fun onNewTaskOpened() = _state.update { it.copy(pinMode = false, spot = null) }

    private fun choose(taskId: String) {
        val st = _state.value
        val pin = spotPin() ?: return
        val jobId = st.jobId ?: return
        _state.update { it.copy(pinMode = false, spot = null) }
        viewModelScope.launch {
            // The chooser may have stayed open while the role changed (Lead taken off the job): ask again before writing (R09g).
            if (!d.canManageTasks(jobId).first()) return@launch
            if (!d.pinOnFile(taskId, pin, jobId)) _state.update { it.copy(pinFailed = true) }
        }
    }

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
            jobId = row.jobId,
        )
        gate?.cancel()
        if (markable) {
            gate = viewModelScope.launch { d.canManage(row.jobId).collect { can -> _state.update { it.copy(canMarkUp = can) } } }
        }
        pinsJob?.cancel()
        if (pages != null) {
            val fileIds = (versions.map { it.id } + row.id).toSet()
            val pageCount = pages.pageCount
            pinsJob = viewModelScope.launch {
                combine(d.tasks(row.jobId), d.canManageTasks(row.jobId)) { tasks, manage -> tasks to manage }.collect { (tasks, manage) ->
                    val pinned = TaskPins.onFile(tasks, fileIds, manage, d.me)
                    val pins = pinned.mapIndexedNotNull { i, t ->
                        t.filePin?.takeIf { it.page < pageCount }?.let { PagePin(t.id, i + 1, t.title, it.page, it.x, it.y) }
                    }
                    val choices = if (manage) TaskList.build(tasks).open.map { TaskChoice(it.id, it.title, it.filePin?.fileId in fileIds) } else emptyList()
                    _state.update { it.copy(pins = pins, canPinTasks = manage, choices = choices, pinMode = it.pinMode && manage, spot = it.spot.takeIf { manage }) }
                }
            }
        }
    }

    override fun onCleared() {
        _state.value.pages?.close()
    }

    companion object {
        const val PDF = "application/pdf"
    }
}
