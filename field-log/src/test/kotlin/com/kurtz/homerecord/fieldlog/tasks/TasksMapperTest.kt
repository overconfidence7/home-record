package com.kurtz.homerecord.fieldlog.tasks

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.JobTask
import com.kurtz.homerecord.core.model.TaskDue
import com.kurtz.homerecord.core.model.TaskFilter
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import org.junit.Test

// AC-F34: the Tasks tab shows title, assignee, due, room, photo, note and punch; punch list is a filter; Crew see and tick their own (AC-F3)
@AcceptanceCriterion("AC-F34")
class TasksMapperTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val names = mapOf("m1" to "Dana Ortiz", "m2" to "Marcus Bell")
    private val rooms = mapOf("r1" to "Kitchen")
    private val tasks = listOf(
        JobTask("t1", "j1", "Set outlets", assigneeId = "m1", due = today.minusDays(1), roomId = "r1", photoId = "p1", note = "GFCI by the sink", sortOrder = 1),
        JobTask("t2", "j1", "Cover plate", assigneeId = "m2", due = today, punch = true, sortOrder = 0),
        JobTask("t3", "j1", "Order meter base", assigneeId = "m2", done = true, doneAt = 5, due = today.minusDays(3)),
    )

    @Test fun officeSeesEveryTaskWithNamesRoomAndDue() {
        val s = TasksMapper.build(tasks, TaskFilter.ALL, canManage = true, me = "m9", names = names, rooms = rooms, today = today)
        assertThat(s.open.map { it.id }).containsExactly("t2", "t1").inOrder()
        assertThat(s.done.map { it.id }).containsExactly("t3")
        val t1 = s.open.single { it.id == "t1" }
        assertThat(t1.assignee).isEqualTo("Dana Ortiz")
        assertThat(t1.room).isEqualTo("Kitchen")
        assertThat(t1.dueKind).isEqualTo(TaskDue.OVERDUE)
        assertThat(t1.hasPhoto).isTrue()
        assertThat(t1.note).isEqualTo("GFCI by the sink")
        assertThat(s.open.single { it.id == "t2" }.dueKind).isEqualTo(TaskDue.TODAY)
        // a done task is never shown as overdue
        assertThat(s.done.single().dueKind).isEqualTo(TaskDue.NONE)
        assertThat(s.open.all { it.canTick }).isTrue()
        assertThat(s.total).isEqualTo(3)
        assertThat(s.canManage).isTrue()
    }

    @Test fun punchFilterKeepsOnlyPunchItems() {
        val s = TasksMapper.build(tasks, TaskFilter.PUNCH, canManage = true, me = null, names = names, rooms = rooms, today = today)
        assertThat(s.open.map { it.id }).containsExactly("t2")
        assertThat(s.done).isEmpty()
        assertThat(s.total).isEqualTo(3)
    }

    @Test fun crewSeeOnlyTheirOwnTasksAndMayTickThem() {
        val s = TasksMapper.build(tasks, TaskFilter.ALL, canManage = false, me = "m2", names = names, rooms = rooms, today = today)
        assertThat((s.open + s.done).map { it.id }).containsExactly("t2", "t3")
        assertThat((s.open + s.done).all { it.canTick }).isTrue()
        assertThat(s.canManage).isFalse()
        assertThat(s.total).isEqualTo(2)
    }

    @Test fun signedOutCrewSeeNothing() {
        val s = TasksMapper.build(tasks, TaskFilter.ALL, canManage = false, me = null, names = names, rooms = rooms, today = today)
        assertThat(s.isEmpty).isTrue()
        assertThat(s.total).isEqualTo(0)
    }

    @Test fun unknownAssigneeAndRoomShowNothingRatherThanAnId() {
        val s = TasksMapper.build(
            listOf(JobTask("t9", "j1", "Fish wire", assigneeId = "gone", roomId = "gone")), TaskFilter.ALL,
            canManage = true, me = null, names = names, rooms = rooms, today = today,
        )
        assertThat(s.open.single().assignee).isNull()
        assertThat(s.open.single().room).isNull()
    }
}
