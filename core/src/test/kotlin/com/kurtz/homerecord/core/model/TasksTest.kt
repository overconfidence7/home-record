package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test
import java.time.LocalDate

// AC-F34: tasks with due, assignee and punch flag; My tasks shows due today and overdue; the punch list is a filter.
@AcceptanceCriterion("AC-F34")
class TasksTest {
    private val today = LocalDate.of(2026, 10, 31)

    private fun task(
        id: String, order: Int = 0, due: LocalDate? = null, who: String? = "m1", done: Boolean = false,
        punch: Boolean = false, doneAt: Long? = null,
    ) = JobTask(id = id, jobId = "j1", title = id, assigneeId = who, due = due, done = done, punch = punch, sortOrder = order, doneAt = doneAt)

    @Test fun dueReadsAgainstToday() {
        assertThat(TaskDue.of(null, today)).isEqualTo(TaskDue.NONE)
        assertThat(TaskDue.of(today.minusDays(1), today)).isEqualTo(TaskDue.OVERDUE)
        assertThat(TaskDue.of(today, today)).isEqualTo(TaskDue.TODAY)
        assertThat(TaskDue.of(today.plusDays(1), today)).isEqualTo(TaskDue.UPCOMING)
    }

    @Test fun openTasksFollowTheCrewOrderAndDoneTasksNewestFirst() {
        val list = TaskList.build(
            listOf(task("c", order = 2), task("a", order = 0), task("old", done = true, doneAt = 10), task("b", order = 1), task("new", done = true, doneAt = 20)),
        )
        assertThat(list.open.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(list.done.map { it.id }).containsExactly("new", "old").inOrder()
    }

    @Test fun punchFilterKeepsOnlyPunchItems() {
        val list = TaskList.build(listOf(task("rough", order = 0), task("touch-up", order = 1, punch = true), task("caulk", done = true, punch = true, doneAt = 5)), TaskFilter.PUNCH)
        assertThat(list.open.map { it.id }).containsExactly("touch-up")
        assertThat(list.done.map { it.id }).containsExactly("caulk")
        assertThat(TaskList.build(emptyList()).isEmpty).isTrue()
    }

    @Test fun myTasksShowsOverdueThenTodayAndNothingElse() {
        val mine = MyTasksDue.build(
            listOf(
                task("today", due = today),
                task("lastWeek", due = today.minusDays(7)),
                task("yesterday", due = today.minusDays(1)),
                task("tomorrow", due = today.plusDays(1)),
                task("noDue"),
                task("someoneElse", due = today, who = "m2"),
                task("unassigned", due = today, who = null),
                task("doneOverdue", due = today.minusDays(2), done = true, doneAt = 1),
            ),
            assigneeId = "m1", today = today,
        )
        assertThat(mine.overdue.map { it.id }).containsExactly("lastWeek", "yesterday").inOrder()
        assertThat(mine.today.map { it.id }).containsExactly("today")
        assertThat(mine.all.map { it.id }).containsExactly("lastWeek", "yesterday", "today").inOrder()
    }

    @Test fun moveRenumbersOnlyTheTasksThatShift() {
        val open = listOf(task("a", 0), task("b", 1), task("c", 2), task("d", 3))
        assertThat(TaskOrder.move(open, from = 3, to = 1)).containsExactly("d", 1, "b", 2, "c", 3)
        assertThat(TaskOrder.move(open, from = 0, to = 0)).isEmpty()
        assertThat(TaskOrder.move(open, from = 5, to = 0)).isEmpty()
    }

    @Test fun moveFixesGapsAndTiesFromSyncedRows() {
        // two phones added a task at the same time, both at order 1
        val open = listOf(task("a", 0), task("b", 1), task("c", 1))
        // c already sits at 1, so only b and a are written
        assertThat(TaskOrder.move(open, from = 0, to = 2)).containsExactly("b", 0, "a", 2)
        assertThat(TaskOrder.move(listOf(task("a", 0), task("b", 1), task("c", 1)), from = 2, to = 1)).containsExactly("b", 2)
    }
}
