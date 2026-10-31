package com.kurtz.homerecord.core.model

import java.time.LocalDate

/** A job task or punch-list item (AC-F34). [due] is a calendar day; [doneAt] is set while [done]. */
data class JobTask(
    val id: String,
    val jobId: String,
    val title: String,
    val assigneeId: String? = null,
    val due: LocalDate? = null,
    val roomId: String? = null,
    val photoId: String? = null,
    val note: String? = null,
    val done: Boolean = false,
    val punch: Boolean = false,
    val sortOrder: Int = 0,
    val doneAt: Long? = null,
    val createdAt: Long = 0,
)

/** Which tasks a job's Tasks tab shows; the punch list is a filter, not a separate list (FL-Tasks). */
enum class TaskFilter { ALL, PUNCH }

/** How a task's due day reads against today. */
enum class TaskDue {
    NONE, OVERDUE, TODAY, UPCOMING;

    companion object {
        fun of(due: LocalDate?, today: LocalDate): TaskDue = when {
            due == null -> NONE
            due.isBefore(today) -> OVERDUE
            due == today -> TODAY
            else -> UPCOMING
        }
    }
}

/** A job's tasks split for display: open ones in the crew's order, done ones most recently ticked first. */
data class TaskList(val open: List<JobTask>, val done: List<JobTask>) {
    val isEmpty: Boolean get() = open.isEmpty() && done.isEmpty()

    companion object {
        val OPEN_ORDER: Comparator<JobTask> = compareBy<JobTask>({ it.sortOrder }, { it.createdAt }, { it.id })

        fun build(tasks: List<JobTask>, filter: TaskFilter = TaskFilter.ALL): TaskList {
            val shown = if (filter == TaskFilter.PUNCH) tasks.filter { it.punch } else tasks
            return TaskList(
                open = shown.filterNot { it.done }.sortedWith(OPEN_ORDER),
                done = shown.filter { it.done }.sortedWith(compareByDescending<JobTask> { it.doneAt ?: 0L }.thenBy { it.id }),
            )
        }
    }
}

/**
 * "My tasks" on Today (AC-F34): the open tasks assigned to me that are overdue or due today. Overdue come first,
 * oldest due day first; then today's in the crew's order. Tasks with no due day or due later are not shown.
 */
data class MyTasksDue(val overdue: List<JobTask>, val today: List<JobTask>) {
    val all: List<JobTask> get() = overdue + today
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty()

    companion object {
        fun build(tasks: List<JobTask>, assigneeId: String, today: LocalDate): MyTasksDue {
            val mine = tasks.filter { !it.done && it.assigneeId == assigneeId }
            return MyTasksDue(
                overdue = mine.filter { TaskDue.of(it.due, today) == TaskDue.OVERDUE }
                    .sortedWith(compareBy<JobTask> { it.due }.then(TaskList.OPEN_ORDER)),
                today = mine.filter { TaskDue.of(it.due, today) == TaskDue.TODAY }.sortedWith(TaskList.OPEN_ORDER),
            )
        }
    }
}

/** Drag-to-reorder for the open tasks of a job (FL-Tasks). */
object TaskOrder {
    /**
     * Moves the task at [from] to [to] in [open] (already in display order) and numbers the list 0..n-1. Returns only
     * the tasks whose sortOrder changes, so a move writes and syncs the fewest rows. Out-of-range indexes change nothing.
     */
    fun move(open: List<JobTask>, from: Int, to: Int): Map<String, Int> {
        if (from !in open.indices || to !in open.indices || from == to) return emptyMap()
        val list = open.toMutableList()
        list.add(to, list.removeAt(from))
        return list.withIndex().filter { (i, t) -> t.sortOrder != i }.associate { (i, t) -> t.id to i }
    }
}
