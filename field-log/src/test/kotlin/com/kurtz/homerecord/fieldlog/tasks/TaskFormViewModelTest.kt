package com.kurtz.homerecord.fieldlog.tasks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.TaskPlanPin
import com.kurtz.homerecord.core.task.JobTasks
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.core.task.TaskDraft
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// AC-F35: the form pins the task on the floor plan (picker per floor, the pin fills an empty room, Remove pin).
// AC-F34: a task has title, assignee, due date, room, photo, note and punch flag; the form adds, edits and deletes
@AcceptanceCriterion("AC-F34", "AC-F35")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TaskFormViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var tasks: JobTasks

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        tasks = JobTasks(db)
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Garage subpanel", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(taskId: String? = null, manage: Boolean = true, saved: SavedStateHandle = SavedStateHandle(), jobId: String = "j1") = TaskFormViewModel(
        TaskFormDeps(
            jobId, taskId, tasks,
            people = { listOf(PersonOption("m1", "Nate Kurtz"), PersonOption("m3", "Marcus Bell", onJob = true), PersonOption("m2", "Dana Ortiz", onJob = true)) },
            rooms = { listOf(RoomOption("r1", "Kitchen")) },
            photos = { listOf(PhotoOption("p1", "/media/p1.jpg")) },
            plan = { plan },
            canManage = flowOf(manage),
        ),
        saved,
    )

    // Kitchen (r1) on the main floor is 0..144 x 0..120 inches; the basement has no rooms and is not offered.
    private var plan = listOf(
        PinFloor("basement", "Basement", emptyList()),
        PinFloor("main", "Main floor", listOf(PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 144, 120))),
        PinFloor("upper", "Upstairs", listOf(PlanRoom.rectangle("r9", "Bedroom", "BEDROOM", 0, 0, 144, 144))),
    )

    private fun TaskFormViewModel.current(until: (TaskFormUiState) -> Boolean = { true }): TaskFormUiState = runBlocking {
        withTimeout(5_000) { state.first { !it.loading && until(it) } }
    }

    @Test fun newTaskSavesEveryField() {
        val vm = vm()
        val s = vm.current()
        // the job's crew first, then the rest, by name
        assertThat(s.people.map { it.name }).containsExactly("Dana Ortiz", "Marcus Bell", "Nate Kurtz").inOrder()
        assertThat(s.isNew).isTrue()
        vm.onEvent(TaskFormEvent.Title("Patch drywall by the panel"))
        vm.onEvent(TaskFormEvent.OpenSheet(TaskFormSheet.ASSIGNEE))
        assertThat(vm.current().sheet).isEqualTo(TaskFormSheet.ASSIGNEE)
        vm.onEvent(TaskFormEvent.Assignee("m2"))
        assertThat(vm.current().sheet).isNull()
        vm.onEvent(TaskFormEvent.Due("2026-11-02"))
        vm.onEvent(TaskFormEvent.Room("r1"))
        vm.onEvent(TaskFormEvent.Photo("p1"))
        vm.onEvent(TaskFormEvent.Note("5/8 board "))
        vm.onEvent(TaskFormEvent.Punch(true))
        val typed = vm.current()
        assertThat(typed.assigneeName).isEqualTo("Dana Ortiz")
        assertThat(typed.roomName).isEqualTo("Kitchen")
        assertThat(typed.dirty).isTrue()
        vm.onEvent(TaskFormEvent.Save)
        vm.current { it.done }
        val saved = runBlocking { tasks.observeForJob("j1").first() }.single()
        assertThat(saved.title).isEqualTo("Patch drywall by the panel")
        assertThat(saved.assigneeId).isEqualTo("m2")
        assertThat(saved.due).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(saved.roomId).isEqualTo("r1")
        assertThat(saved.photoId).isEqualTo("p1")
        assertThat(saved.note).isEqualTo("5/8 board")
        assertThat(saved.punch).isTrue()
    }

    @Test fun blankTitleDoesNotSaveAndShowsTheError() {
        val vm = vm()
        vm.current()
        vm.onEvent(TaskFormEvent.Title("x"))
        vm.onEvent(TaskFormEvent.Title("   "))
        assertThat(vm.current().titleError).isTrue()
        vm.onEvent(TaskFormEvent.Save)
        assertThat(vm.current().done).isFalse()
        assertThat(runBlocking { tasks.observeForJob("j1").first() }).isEmpty()
    }

    @Test fun editLoadsTheTaskAndSavesOverIt() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Cover plate", assigneeId = "m3", punch = true)) })
        val vm = vm(id)
        val s = vm.current()
        assertThat(s.isNew).isFalse()
        assertThat(s.draft.title).isEqualTo("Cover plate")
        assertThat(s.assigneeName).isEqualTo("Marcus Bell")
        assertThat(s.dirty).isFalse()
        vm.onEvent(TaskFormEvent.Title("Replace cracked cover plate"))
        vm.onEvent(TaskFormEvent.Assignee(null))
        vm.onEvent(TaskFormEvent.Save)
        vm.current { it.done }
        val t = checkNotNull(runBlocking { tasks.get(id) })
        assertThat(t.title).isEqualTo("Replace cracked cover plate")
        assertThat(t.assigneeId).isNull()
        assertThat(t.punch).isTrue()
    }

    @Test fun deleteAsksThenRemoves() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Cover plate")) })
        val vm = vm(id)
        vm.current()
        vm.onEvent(TaskFormEvent.AskDelete)
        assertThat(vm.current().confirmDelete).isTrue()
        vm.onEvent(TaskFormEvent.Delete)
        vm.current { it.done }
        assertThat(runBlocking { tasks.get(id) }).isNull()
    }

    @Test fun typingSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.current()
        first.onEvent(TaskFormEvent.Title("Label the new circuits"))
        first.onEvent(TaskFormEvent.Punch(true))
        val again = vm(saved = saved).current()
        assertThat(again.draft.title).isEqualTo("Label the new circuits")
        assertThat(again.draft.punch).isTrue()
    }

    @Test fun crewCannotOpenTheForm() {
        assertThat(vm(manage = false).current().failed).isTrue()
    }

    @Test fun aTaskFromAnotherJobDoesNotOpen() {
        val id = checkNotNull(runBlocking { tasks.add("j2", TaskDraft("Mount the subpanel")) })
        assertThat(vm(id).current().failed).isTrue()
    }

    @Test fun pinOnPlanDropsThePinAndFillsTheRoom() {
        val vm = vm()
        val s = vm.current()
        assertThat(s.plan.map { it.id }).containsExactly("main", "upper").inOrder()
        vm.onEvent(TaskFormEvent.Title("Move the outlet"))
        vm.onEvent(TaskFormEvent.OpenPin)
        assertThat(vm.current().pinning).isTrue()
        assertThat(vm.current().pinFloor?.id).isEqualTo("main")
        vm.onEvent(TaskFormEvent.PlacePin(60, 40))
        assertThat(vm.current().draft.planPin).isEqualTo(TaskPlanPin("main", 60, 40))
        assertThat(vm.current().draft.roomId).isEqualTo("r1")
        assertThat(vm.current().pinPlace).isEqualTo("Main floor" to "Kitchen")
        vm.onEvent(TaskFormEvent.ClosePin)
        assertThat(vm.current().pinning).isFalse()
        vm.onEvent(TaskFormEvent.Save)
        vm.current { it.done }
        val saved = runBlocking { tasks.observeForJob("j1").first().single() }
        assertThat(saved.planPin).isEqualTo(TaskPlanPin("main", 60, 40))
        assertThat(saved.roomId).isEqualTo("r1")
    }

    @Test fun pinOnAnotherFloorKeepsTheRoomAlreadyChosenAndCanBeRemoved() {
        val id = runBlocking { checkNotNull(tasks.add("j1", TaskDraft(title = "Smoke detector", roomId = "r1", planPin = TaskPlanPin("main", 10, 10)))) }
        val vm = vm(taskId = id)
        assertThat(vm.current().pinPlace).isEqualTo("Main floor" to "Kitchen")
        vm.onEvent(TaskFormEvent.OpenPin)
        assertThat(vm.current().pinFloorId).isEqualTo("main")
        vm.onEvent(TaskFormEvent.PinFloor("upper"))
        vm.onEvent(TaskFormEvent.PlacePin(20, 20))
        assertThat(vm.current().draft.planPin).isEqualTo(TaskPlanPin("upper", 20, 20))
        assertThat(vm.current().draft.roomId).isEqualTo("r1")
        assertThat(vm.current().dirty).isTrue()
        vm.onEvent(TaskFormEvent.RemovePin)
        assertThat(vm.current().draft.planPin).isNull()
        assertThat(vm.current().pinPlace).isNull()
    }

    @Test fun noPlanMeansNoPinPicker() {
        plan = emptyList()
        val vm = vm()
        vm.current()
        vm.onEvent(TaskFormEvent.OpenPin)
        assertThat(vm.current().pinning).isFalse()
    }
}
