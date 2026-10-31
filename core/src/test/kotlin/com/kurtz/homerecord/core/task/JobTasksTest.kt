package com.kurtz.homerecord.core.task

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.MyTasksDue
import com.kurtz.homerecord.core.model.TaskList
import com.kurtz.homerecord.core.model.TaskPlanPin
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// AC-F35 (plan pin saved, kept and cleared), AC-F34 (tasks: title, assignee, due, room, photo, note, punch; swipe completes with Undo), AC-F36 (changes sync)
@AcceptanceCriterion("AC-F34", "AC-F35", "AC-F36")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobTasksTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var tasks: JobTasks
    private val changes = mutableListOf<String>()
    private var clock = 1_000L
    private val today = LocalDate.of(2026, 10, 31)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        tasks = JobTasks(db, now = { clock }, onChange = { e, id -> changes += "$e/$id" })
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Garage subpanel", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "x1", companyId = "c2", name = "Other company", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() = db.close()

    @Test fun addKeepsEveryFieldAndQueuesOneChange() = runBlocking<Unit> {
        val id = checkNotNull(
            tasks.add(
                "j1",
                TaskDraft(
                    title = "  Patch drywall by the panel ", assigneeId = "m1", due = today, roomId = "r1", photoId = "p1",
                    note = "  Use the 5/8 board ", punch = true,
                ),
            ),
        )
        val task = checkNotNull(tasks.get(id))
        assertThat(task.title).isEqualTo("Patch drywall by the panel")
        assertThat(task.assigneeId).isEqualTo("m1")
        assertThat(task.due).isEqualTo(today)
        assertThat(task.roomId).isEqualTo("r1")
        assertThat(task.photoId).isEqualTo("p1")
        assertThat(task.note).isEqualTo("Use the 5/8 board")
        assertThat(task.punch).isTrue()
        assertThat(task.done).isFalse()
        assertThat(changes).containsExactly("task/$id")
    }

    @Test fun blankTitleOrMissingJobSavesNothing() = runBlocking<Unit> {
        assertThat(tasks.add("j1", TaskDraft("   "))).isNull()
        assertThat(tasks.add("gone", TaskDraft("Hang door"))).isNull()
        assertThat(db.taskDao().listFor("j1")).isEmpty()
        assertThat(changes).isEmpty()
    }

    @Test fun newTasksGoToTheBottomOfTheList() = runBlocking<Unit> {
        val a = checkNotNull(tasks.add("j1", TaskDraft("Set boxes")))
        val b = checkNotNull(tasks.add("j1", TaskDraft("Pull wire")))
        val c = checkNotNull(tasks.add("j1", TaskDraft("Hang panel")))
        assertThat(TaskList.build(tasks.observeForJob("j1").first()).open.map { it.id }).containsExactly(a, b, c).inOrder()
    }

    @Test fun swipeDoneThenUndoRestoresTheTask() = runBlocking<Unit> {
        val id = checkNotNull(tasks.add("j1", TaskDraft("Caulk the tub")))
        clock = 5_000L
        assertThat(tasks.setDone(id, true)).isTrue()
        assertThat(checkNotNull(tasks.get(id)).doneAt).isEqualTo(5_000L)
        assertThat(tasks.setDone(id, true)).isFalse()
        clock = 6_000L
        assertThat(tasks.setDone(id, false)).isTrue()
        val back = checkNotNull(tasks.get(id))
        assertThat(back.done).isFalse()
        assertThat(back.doneAt).isNull()
        assertThat(changes).containsExactly("task/$id", "task/$id", "task/$id").inOrder()
    }

    @Test fun editKeepsDoneStateAndOrder() = runBlocking<Unit> {
        checkNotNull(tasks.add("j1", TaskDraft("First")))
        val id = checkNotNull(tasks.add("j1", TaskDraft("Hang uppers")))
        tasks.setDone(id, true)
        assertThat(tasks.update(id, TaskDraft("Hang upper cabinets", assigneeId = "m2", punch = true))).isTrue()
        val row = checkNotNull(db.taskDao().get(id))
        assertThat(row.title).isEqualTo("Hang upper cabinets")
        assertThat(row.done).isTrue()
        assertThat(row.sortOrder).isEqualTo(1)
        assertThat(row.punch).isTrue()
        assertThat(tasks.update(id, TaskDraft(" "))).isFalse()
        assertThat(tasks.update("gone", TaskDraft("x"))).isFalse()
    }

    @Test fun dragReorderPersistsAndSyncsOnlyMovedRows() = runBlocking<Unit> {
        val a = checkNotNull(tasks.add("j1", TaskDraft("Set boxes")))
        val b = checkNotNull(tasks.add("j1", TaskDraft("Pull wire")))
        val c = checkNotNull(tasks.add("j1", TaskDraft("Hang panel")))
        changes.clear()
        tasks.move("j1", from = 2, to = 0)
        assertThat(TaskList.build(tasks.observeForJob("j1").first()).open.map { it.id }).containsExactly(c, a, b).inOrder()
        assertThat(changes).containsExactly("task/$c", "task/$a", "task/$b")
        changes.clear()
        tasks.move("j1", from = 1, to = 1)
        assertThat(changes).isEmpty()
    }

    @Test fun myTasksComeFromThisCompanysJobsOnly() = runBlocking<Unit> {
        val overdue = checkNotNull(tasks.add("j1", TaskDraft("Order breakers", assigneeId = "m1", due = today.minusDays(2))))
        val due = checkNotNull(tasks.add("j2", TaskDraft("Label panel", assigneeId = "m1", due = today)))
        tasks.add("j2", TaskDraft("Pull permit", assigneeId = "m2", due = today))
        tasks.add("x1", TaskDraft("Not ours", assigneeId = "m1", due = today))
        val deleted = checkNotNull(tasks.add("j1", TaskDraft("Deleted", assigneeId = "m1", due = today)))
        tasks.delete(deleted)
        val mine = MyTasksDue.build(tasks.observeAssigned("c1", "m1").first(), "m1", today)
        assertThat(mine.all.map { it.id }).containsExactly(overdue, due).inOrder()
    }

    @Test fun deleteHidesTheTaskAndSyncs() = runBlocking<Unit> {
        val id = checkNotNull(tasks.add("j1", TaskDraft("Remove debris")))
        changes.clear()
        tasks.delete(id)
        assertThat(tasks.get(id)).isNull()
        assertThat(changes).containsExactly("task/$id")
        tasks.delete(id)
        assertThat(changes).hasSize(1)
    }

    @Test fun planPinIsSavedWithTheFormAndClearedWhenRemoved() = runBlocking<Unit> {
        val pin = TaskPlanPin("f1", 72, 30)
        val id = checkNotNull(tasks.add("j1", TaskDraft(title = "Move the outlet", planPin = pin)))
        assertThat(tasks.get(id)?.planPin).isEqualTo(pin)
        assertThat(db.taskDao().get(id)?.planPinJson).isEqualTo(pin.encode())
        // A file pin set elsewhere survives saving the form.
        db.taskDao().get(id)?.let { db.taskDao().upsert(it.copy(fileId = "file1", page = 2, x = 0.4f, y = 0.6f)) }
        val moved = TaskPlanPin("f1", 96, 30)
        assertThat(tasks.update(id, TaskDraft(title = "Move the outlet", planPin = moved))).isTrue()
        assertThat(tasks.get(id)?.planPin).isEqualTo(moved)
        assertThat(db.taskDao().get(id)?.fileId).isEqualTo("file1")
        changes.clear()
        assertThat(tasks.update(id, TaskDraft(title = "Move the outlet"))).isTrue()
        assertThat(tasks.get(id)?.planPin).isNull()
        assertThat(changes).containsExactly("task/$id")
    }
}
