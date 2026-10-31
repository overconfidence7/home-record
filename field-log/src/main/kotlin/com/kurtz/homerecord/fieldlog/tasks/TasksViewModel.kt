package com.kurtz.homerecord.fieldlog.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.TaskFilter
import com.kurtz.homerecord.core.task.JobTasks
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksDeps(
    val jobId: String,
    val tasks: JobTasks,
    /** Member id to name, for the assignee line. */
    val names: Flow<Map<String, String>> = flowOf(emptyMap()),
    /** Room id to name on the job's house. */
    val rooms: Flow<Map<String, String>> = flowOf(emptyMap()),
    /** RoleGates.leads(jobId): Owner, Office and the job's Lead manage tasks (AC-F3). */
    val canManage: Flow<Boolean> = flowOf(false),
    /** The signed-in member, so Crew see and tick their own tasks. */
    val me: String? = null,
    val today: () -> LocalDate = LocalDate::now,
)

/** A job's Tasks tab: open and done lists, All / Punch list, tick done with Undo (FL-Tasks, AC-F34). */
class TasksViewModel(private val d: TasksDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val filter = MutableStateFlow(saved.get<String>(FILTER)?.let { f -> TaskFilter.entries.firstOrNull { it.name == f } } ?: TaskFilter.ALL)
    private val undo = MutableStateFlow<TaskUndo?>(null)
    private val canManage = d.canManage.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val state: StateFlow<TasksUiState> = combine(
        d.tasks.observeForJob(d.jobId), filter, combine(d.names, d.rooms, ::Pair), canManage, undo,
    ) { tasks, f, (names, rooms), manage, u ->
        TasksMapper.build(tasks, f, manage, d.me, names, rooms, d.today()).copy(undo = u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun onEvent(e: TasksEvent) {
        when (e) {
            is TasksEvent.SetFilter -> { filter.value = e.filter; saved[FILTER] = e.filter.name }
            is TasksEvent.SetDone -> setDone(e.id, e.done)
            is TasksEvent.Move -> move(e.id, e.to)
            TasksEvent.Undo -> undo.value?.let { u ->
                undo.value = null
                viewModelScope.launch { d.tasks.setDone(u.id, false) }
            }
            TasksEvent.DismissUndo -> undo.value = null
            is TasksEvent.Open, TasksEvent.Add -> Unit
        }
    }

    private fun setDone(id: String, done: Boolean) {
        val row = (state.value.open + state.value.done).firstOrNull { it.id == id }
        viewModelScope.launch {
            val task = d.tasks.get(id) ?: return@launch
            // Crew tick only their own tasks; the server refuses the rest (AC-F3).
            if (!canManage.value && (d.me == null || task.assigneeId != d.me)) return@launch
            if (d.tasks.setDone(id, done)) undo.value = if (done) TaskUndo(id, row?.title ?: task.title) else null
        }
    }

    private fun move(id: String, to: Int) {
        val current = state.value
        if (!current.canReorder) return
        val from = current.open.indexOfFirst { it.id == id }
        if (from < 0 || from == to) return
        viewModelScope.launch { d.tasks.move(d.jobId, from, to.coerceIn(0, current.open.lastIndex)) }
    }

    private companion object {
        const val FILTER = "tasksFilter"
    }
}
