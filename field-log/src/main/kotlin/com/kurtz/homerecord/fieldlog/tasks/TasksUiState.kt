package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.model.JobTask
import com.kurtz.homerecord.core.model.TaskDue
import com.kurtz.homerecord.core.model.TaskFilter
import com.kurtz.homerecord.core.model.TaskList
import java.time.LocalDate

/** One row on a job's Tasks tab (FL-Tasks, AC-F34). */
@Immutable
data class TaskRow(
    val id: String,
    val title: String,
    val assignee: String? = null,
    val due: LocalDate? = null,
    val dueKind: TaskDue = TaskDue.NONE,
    val room: String? = null,
    val hasPhoto: Boolean = false,
    val note: String? = null,
    val punch: Boolean = false,
    val done: Boolean = false,
    /** This person may tick it: Owner, Office and the job's Lead any task, Crew only their own (AC-F3). */
    val canTick: Boolean = true,
)

/** A task just ticked done; the snackbar offers Undo (SPEC FL-Tasks: swipe to complete with Undo). */
data class TaskUndo(val id: String, val title: String)

@Immutable
data class TasksUiState(
    val loading: Boolean = true,
    val filter: TaskFilter = TaskFilter.ALL,
    val open: List<TaskRow> = emptyList(),
    val done: List<TaskRow> = emptyList(),
    /** Tasks this person sees on the job before the filter; 0 means the job has none for them yet. */
    val total: Int = 0,
    /** Add, edit and assign: Owner, Office and the job's Lead (AC-F3). Crew see only their own tasks and tick them. */
    val canManage: Boolean = false,
    val undo: TaskUndo? = null,
) {
    val isEmpty: Boolean get() = open.isEmpty() && done.isEmpty()
}

sealed interface TasksEvent {
    data class SetFilter(val filter: TaskFilter) : TasksEvent
    data class SetDone(val id: String, val done: Boolean) : TasksEvent
    data object Undo : TasksEvent
    data object DismissUndo : TasksEvent
    /** Opens the task form for a task (the route navigates). */
    data class Open(val id: String) : TasksEvent
    /** Opens the empty task form (the route navigates). */
    data object Add : TasksEvent
}

/** Turns a job's tasks into the rows this person sees (AC-F34, AC-F3). */
object TasksMapper {
    fun build(
        tasks: List<JobTask>,
        filter: TaskFilter,
        canManage: Boolean,
        me: String?,
        names: Map<String, String>,
        rooms: Map<String, String>,
        today: LocalDate,
    ): TasksUiState {
        // Crew see only what is assigned to them, as the server sends them nothing else (backend policy.ts).
        val visible = if (canManage) tasks else tasks.filter { me != null && it.assigneeId == me }
        val list = TaskList.build(visible, filter)
        fun JobTask.row() = TaskRow(
            id = id, title = title,
            assignee = assigneeId?.let { names[it] },
            due = due, dueKind = if (done) TaskDue.NONE else TaskDue.of(due, today),
            room = roomId?.let { rooms[it] },
            hasPhoto = photoId != null, note = note, punch = punch, done = done,
            canTick = canManage || (me != null && assigneeId == me),
        )
        return TasksUiState(
            loading = false, filter = filter,
            open = list.open.map { it.row() }, done = list.done.map { it.row() },
            total = visible.size, canManage = canManage,
        )
    }
}
