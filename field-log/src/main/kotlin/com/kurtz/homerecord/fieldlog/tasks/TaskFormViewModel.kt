package com.kurtz.homerecord.fieldlog.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.task.JobTasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class TaskFormDeps(
    val jobId: String,
    /** Null for a new task. */
    val taskId: String?,
    val tasks: JobTasks,
    val people: suspend () -> List<PersonOption> = { emptyList() },
    val rooms: suspend () -> List<RoomOption> = { emptyList() },
    val photos: suspend () -> List<PhotoOption> = { emptyList() },
    /** RoleGates.leads(jobId): only Owner, Office and the job's Lead add and edit tasks (AC-F3). */
    val canManage: Flow<Boolean> = flowOf(false),
)

/** Add or edit one task (FL-Tasks, AC-F34). Save adds it at the bottom of the open list or saves over it; the typing survives process death. */
class TaskFormViewModel(private val d: TaskFormDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(TaskFormUiState(isNew = d.taskId == null))
    val state: StateFlow<TaskFormUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val existing = d.taskId?.let { d.tasks.get(it) }
            if (!d.canManage.first() || (d.taskId != null && (existing == null || existing.jobId != d.jobId))) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val initial = existing?.let(TaskFormDraft::of) ?: TaskFormDraft()
            val draft = saved.get<String>(DRAFT)?.let { runCatching { json.decodeFromString(TaskFormDraft.serializer(), it) }.getOrNull() } ?: initial
            val people = d.people().sortedWith(compareByDescending<PersonOption> { it.onJob }.thenBy { it.name.lowercase() })
            _state.update {
                it.copy(loading = false, draft = draft, initial = initial, people = people, rooms = d.rooms(), photos = d.photos())
            }
        }
    }

    fun onEvent(e: TaskFormEvent) {
        when (e) {
            is TaskFormEvent.Title -> edit { it.copy(title = e.text) }
            is TaskFormEvent.Assignee -> { edit { it.copy(assigneeId = e.id) }; closeSheet() }
            is TaskFormEvent.Due -> edit { it.copy(due = e.iso) }
            is TaskFormEvent.Room -> { edit { it.copy(roomId = e.id) }; closeSheet() }
            is TaskFormEvent.Photo -> { edit { it.copy(photoId = e.id) }; closeSheet() }
            is TaskFormEvent.Note -> edit { it.copy(note = e.text) }
            is TaskFormEvent.Punch -> edit { it.copy(punch = e.on) }
            is TaskFormEvent.OpenSheet -> if (ready()) _state.update { it.copy(sheet = e.sheet) }
            TaskFormEvent.CloseSheet -> closeSheet()
            TaskFormEvent.Save -> save()
            TaskFormEvent.AskDelete -> if (ready() && !_state.value.isNew) _state.update { it.copy(confirmDelete = true) }
            TaskFormEvent.DismissDelete -> _state.update { it.copy(confirmDelete = false) }
            TaskFormEvent.Delete -> delete()
            TaskFormEvent.Retry -> load()
            TaskFormEvent.Close -> Unit
        }
    }

    private fun ready(): Boolean = _state.value.let { !it.loading && !it.failed && !it.working }

    private fun closeSheet() = _state.update { it.copy(sheet = null) }

    private fun edit(change: (TaskFormDraft) -> TaskFormDraft) {
        if (!ready()) return
        val draft = change(_state.value.draft)
        saved[DRAFT] = json.encodeToString(TaskFormDraft.serializer(), draft)
        _state.update { it.copy(draft = draft) }
    }

    private fun save() {
        val st = _state.value
        if (!ready() || !st.draft.titleOk) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val ok = if (d.taskId == null) d.tasks.add(d.jobId, st.draft.toDraft()) != null else d.tasks.update(d.taskId, st.draft.toDraft())
            if (ok) finish() else _state.update { it.copy(working = false, failed = true) }
        }
    }

    private fun delete() {
        val id = d.taskId ?: return
        if (!ready()) return
        _state.update { it.copy(working = true, confirmDelete = false) }
        viewModelScope.launch {
            d.tasks.delete(id)
            finish()
        }
    }

    private fun finish() {
        saved.remove<String>(DRAFT)
        _state.update { it.copy(working = false, done = true) }
    }

    private companion object {
        const val DRAFT = "taskDraft"
    }
}
