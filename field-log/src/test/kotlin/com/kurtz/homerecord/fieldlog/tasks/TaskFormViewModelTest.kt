package com.kurtz.homerecord.fieldlog.tasks

import com.kurtz.homerecord.fieldlog.ViewModelScopes
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.TaskEntity
import com.kurtz.homerecord.core.model.TaskFilePin
import com.kurtz.homerecord.core.model.TaskPlanPin
import com.kurtz.homerecord.core.task.JobTasks
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.core.task.TaskDraft
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

// AC-F35: the form pins the task on the floor plan (picker per floor, the pin fills an empty room, Remove pin), and
// a task started from a file's "New task here" saves pinned on that page; Remove takes it off the file.
// AC-F34: a task has title, assignee, due date, room, photo, note and punch flag; the form adds, edits and deletes
@AcceptanceCriterion("AC-F34", "AC-F35")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TaskFormViewModelTest {
    private val scopes = ViewModelScopes()
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
        scopes.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(
        taskId: String? = null, manage: Boolean = true, saved: SavedStateHandle = SavedStateHandle(), jobId: String = "j1",
        startFilePin: TaskFilePin? = null, me: String? = null,
    ) = scopes.track(TaskFormViewModel(
        TaskFormDeps(
            jobId, taskId, tasks,
            people = { listOf(PersonOption("m1", "Nate Kurtz"), PersonOption("m3", "Marcus Bell", onJob = true), PersonOption("m2", "Dana Ortiz", onJob = true)) },
            rooms = { listOf(RoomOption("r1", "Kitchen")) },
            photos = { listOf(PhotoOption("p1", "/media/p1.jpg")) },
            plan = { plan },
            canManage = flowOf(manage),
            startFilePin = startFilePin,
            fileName = { if (it == "plan1") "Kitchen plan.pdf" else null },
            me = me,
        ),
        saved,
    ))

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

    // AC-F36: a task-assigned push opens the form before sync brings the task down; it loads once the task arrives (R09f).
    @Test fun aTaskArrivingBySyncLoadsTheFormThatWasOnTheError() {
        val vm = vm("t-pushed")
        assertThat(vm.current().failed).isTrue()
        runBlocking { db.taskDao().upsert(TaskEntity(id = "t-pushed", jobId = "j1", title = "Label the panel", createdAt = 1, updatedAt = 1)) }
        val s = vm.current { !it.failed }
        assertThat(s.draft.title).isEqualTo("Label the panel")
        assertThat(s.isNew).isFalse()
    }

    @Test fun theFormLoadsOnceTheRightToEditArrives() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Cover plate")) })
        val manage = MutableStateFlow(false)
        val vm = scopes.track(TaskFormViewModel(TaskFormDeps("j1", id, tasks, canManage = manage)))
        assertThat(vm.current().failed).isTrue()
        manage.value = true
        assertThat(vm.current { !it.failed }.draft.title).isEqualTo("Cover plate")
    }

    @Test fun aTaskArrivingOnAnotherJobKeepsTheError() {
        val vm = vm("t-other")
        assertThat(vm.current().failed).isTrue()
        runBlocking { db.taskDao().upsert(TaskEntity(id = "t-other", jobId = "j2", title = "Mount the subpanel", createdAt = 1, updatedAt = 1)) }
        assertThat(vm.current().failed).isTrue()
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

    @Test fun newTaskHereSavesPinnedOnThePage() {
        val pin = TaskFilePin("plan1", 1, 0.3f, 0.6f)
        val vm = vm(startFilePin = pin)
        val s = vm.current()
        assertThat(s.draft.filePin?.toPin()).isEqualTo(pin)
        assertThat(s.fileName).isEqualTo("Kitchen plan.pdf")
        // The spot counts as typed: Close asks before dropping it.
        assertThat(s.dirty).isTrue()
        vm.onEvent(TaskFormEvent.Title("Move the vanity light"))
        vm.onEvent(TaskFormEvent.Save)
        vm.current { it.done }
        val saved = runBlocking { tasks.observeForJob("j1").first().single() }
        assertThat(saved.filePin).isEqualTo(pin)
    }

    @Test fun removeFilePinTakesTheTaskOffTheFile() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Move the vanity light", filePin = TaskFilePin("plan1", 0, 0.5f, 0.5f))) })
        val vm = vm(taskId = id)
        assertThat(vm.current().draft.filePin).isNotNull()
        vm.onEvent(TaskFormEvent.RemoveFilePin)
        assertThat(vm.current().draft.filePin).isNull()
        vm.onEvent(TaskFormEvent.Save)
        vm.current { it.done }
        assertThat(runBlocking { tasks.get(id) }?.filePin).isNull()
    }

    @Test fun savingTheFormKeepsAFilePinDroppedWhileItWasOpen() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Move the vanity light")) })
        val vm = vm(taskId = id)
        vm.current()
        val pin = TaskFilePin("plan1", 2, 0.1f, 0.2f)
        runBlocking { tasks.pinOnFile(id, pin, "j1") }
        vm.onEvent(TaskFormEvent.Note("Box is 6 inches left of center"))
        vm.onEvent(TaskFormEvent.Save)
        vm.current { it.done }
        assertThat(runBlocking { tasks.get(id) }?.filePin).isEqualTo(pin)
    }

    // AC-F35 (R09g): a Crew member's pin tap opens their own task read only, with the names it points at
    @Test fun crewOpenTheirOwnTaskReadOnly() {
        val pin = TaskPlanPin("main", 40, 40)
        val id = checkNotNull(runBlocking {
            tasks.add("j1", TaskDraft("Set the kitchen outlets", assigneeId = "m3", roomId = "r1", planPin = pin, filePin = TaskFilePin("plan1", 1, 0.2f, 0.3f)))
        })
        val vm = vm(id, manage = false, me = "m3")
        val s = vm.current()
        assertThat(s.failed).isFalse()
        assertThat(s.readOnly).isTrue()
        assertThat(s.isDone).isFalse()
        assertThat(s.draft.title).isEqualTo("Set the kitchen outlets")
        assertThat(s.assigneeName).isEqualTo("Marcus Bell")
        assertThat(s.roomName).isEqualTo("Kitchen")
        assertThat(s.pinPlace).isEqualTo("Main floor" to "Kitchen")
        assertThat(s.fileName).isEqualTo("Kitchen plan.pdf")
    }

    @Test fun readOnlyChangesNothing() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Set the kitchen outlets", assigneeId = "m3")) })
        runBlocking { tasks.setDone(id, true) }
        val vm = vm(id, manage = false, me = "m3")
        assertThat(vm.current().isDone).isTrue()
        vm.onEvent(TaskFormEvent.Title("Something else"))
        vm.onEvent(TaskFormEvent.Punch(true))
        vm.onEvent(TaskFormEvent.OpenSheet(TaskFormSheet.ASSIGNEE))
        vm.onEvent(TaskFormEvent.OpenPin)
        vm.onEvent(TaskFormEvent.Save)
        vm.onEvent(TaskFormEvent.AskDelete)
        vm.onEvent(TaskFormEvent.Delete)
        val s = vm.current()
        assertThat(s.draft.title).isEqualTo("Set the kitchen outlets")
        assertThat(s.dirty).isFalse()
        assertThat(s.sheet).isNull()
        assertThat(s.pinning).isFalse()
        assertThat(s.confirmDelete).isFalse()
        assertThat(s.done).isFalse()
        val stored = runBlocking { tasks.get(id) }
        assertThat(stored?.title).isEqualTo("Set the kitchen outlets")
        assertThat(stored?.punch).isFalse()
    }

    @Test fun crewCannotOpenSomeoneElsesTask() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Pull the dryer circuit", assigneeId = "m2")) })
        assertThat(vm(id, manage = false, me = "m3").current().failed).isTrue()
        assertThat(vm(id, manage = false, me = null).current().failed).isTrue()
    }

    // AC-F36: a Crew member's task-assigned push opens before the task syncs down; it loads read only once it arrives
    @Test fun crewTaskArrivingBySyncLoadsReadOnly() {
        val vm = vm("t-crew", manage = false, me = "m3")
        assertThat(vm.current().failed).isTrue()
        runBlocking { db.taskDao().upsert(TaskEntity(id = "t-crew", jobId = "j1", title = "Label the panel", assigneeId = "m3", createdAt = 1, updatedAt = 1)) }
        val s = vm.current { !it.failed }
        assertThat(s.readOnly).isTrue()
        assertThat(s.draft.title).isEqualTo("Label the panel")
    }

    @Test fun managersStillGetTheFormForTheirOwnTask() {
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Set the kitchen outlets", assigneeId = "m3")) })
        assertThat(vm(id, manage = true, me = "m3").current().readOnly).isFalse()
    }
}
