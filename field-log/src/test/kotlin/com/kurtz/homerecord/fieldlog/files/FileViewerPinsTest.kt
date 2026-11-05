package com.kurtz.homerecord.fieldlog.files

import com.kurtz.homerecord.fieldlog.ViewModelScopes
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.TaskFilePin
import com.kurtz.homerecord.core.photo.JobFiles
import com.kurtz.homerecord.core.task.JobTasks
import com.kurtz.homerecord.core.task.TaskDraft
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.pages.PageSource
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
import java.io.File

// AC-F35: pin a task on a plan file page from the viewer; the pins drawn on the page open the task
@AcceptanceCriterion("AC-F35")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FileViewerPinsTest {
    private val scopes = ViewModelScopes()
    private lateinit var db: HomeRecordDatabase
    private lateinit var files: JobFiles
    private lateinit var tasks: JobTasks
    private val manage = MutableStateFlow(true)

    private val pages = object : PageSource {
        override val pageCount = 3
        override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = null
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        files = JobFiles(db)
        tasks = JobTasks(db)
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() {
        scopes.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun plan(): String {
        val f = File.createTempFile("viewer", "plan.pdf").apply { deleteOnExit() }
        return runBlocking { files.add("j1", "", "Kitchen plan.pdf", f.absolutePath, "application/pdf", 10, "m1") }.id
    }

    private fun viewer(fileId: String, me: String? = "m1") = scopes.track(FileViewerViewModel(
        FileViewerDeps(
            fileId, db.jobFileDao(), { _, _ -> pages },
            tasks = tasks::observeForJob, canManageTasks = { manage }, me = me, pinOnFile = tasks::pinOnFile,
        ),
    ))

    private fun FileViewerViewModel.until(test: (FileViewerUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(test) } }

    @Test fun tapInPinModeThenChooseATaskPinsItWhereTapped() {
        val file = plan()
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Patch the soffit")) })
        val vm = viewer(file)
        val ready = vm.until { !it.loading && it.choices.isNotEmpty() }
        assertThat(ready.canStartPinning).isTrue()
        assertThat(ready.choices.single().pinnedHere).isFalse()
        // A tap outside pin mode does nothing.
        vm.onPinEvent(FileViewerEvent.TapPage(0, 0.5f, 0.5f))
        assertThat(vm.state.value.spot).isNull()
        vm.onPinEvent(FileViewerEvent.StartPinning)
        vm.onPinEvent(FileViewerEvent.TapPage(1, 0.25f, 0.75f))
        assertThat(vm.state.value.spot).isEqualTo(PageSpot(1, 0.25f, 0.75f))
        vm.onPinEvent(FileViewerEvent.ChooseTask(id))
        val pinned = vm.until { it.pins.isNotEmpty() }
        assertThat(pinned.pins.single()).isEqualTo(PagePin(id, 1, "Patch the soffit", 1, 0.25f, 0.75f))
        assertThat(pinned.pinMode).isFalse()
        assertThat(pinned.choices.single().pinnedHere).isTrue()
        assertThat(runBlocking { tasks.get(id) }?.filePin).isEqualTo(TaskFilePin(file, 1, 0.25f, 0.75f))
    }

    @Test fun newTaskHereHandsTheSpotToTheFormAndLeavesPinMode() {
        val vm = viewer(plan())
        vm.until { !it.loading && it.canPinTasks }
        vm.onPinEvent(FileViewerEvent.StartPinning)
        vm.onPinEvent(FileViewerEvent.TapPage(0, 1.4f, -0.2f))
        val pin = checkNotNull(vm.spotPin())
        assertThat(pin.page to (pin.x to pin.y)).isEqualTo(0 to (1f to 0f))
        vm.onNewTaskOpened()
        assertThat(vm.state.value.pinMode).isFalse()
        assertThat(vm.state.value.spot).isNull()
    }

    @Test fun crewSeeOnlyTheirPinsAndCannotPin() {
        val file = plan()
        runBlocking {
            tasks.add("j1", TaskDraft("Mine", assigneeId = "m1", filePin = TaskFilePin(file, 0, 0.2f, 0.2f)))
            tasks.add("j1", TaskDraft("Theirs", assigneeId = "m2", filePin = TaskFilePin(file, 0, 0.6f, 0.6f)))
        }
        manage.value = false
        val vm = viewer(file, me = "m1")
        val s = vm.until { !it.loading && it.pins.isNotEmpty() }
        assertThat(s.pins.map { it.title }).containsExactly("Mine")
        assertThat(s.canStartPinning).isFalse()
        assertThat(s.choices).isEmpty()
        vm.onPinEvent(FileViewerEvent.StartPinning)
        assertThat(vm.state.value.pinMode).isFalse()
    }

    @Test fun pinsOnAPageTheFileNoLongerHasAreLeftOffAndDoneTasksDropOut() {
        val file = plan()
        val done = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Done one", filePin = TaskFilePin(file, 0, 0.5f, 0.5f))) })
        runBlocking {
            tasks.add("j1", TaskDraft("Old page 5", filePin = TaskFilePin(file, 4, 0.5f, 0.5f)))
            tasks.setDone(done, true)
            tasks.add("j1", TaskDraft("Kept", filePin = TaskFilePin(file, 2, 0.1f, 0.9f)))
        }
        val s = viewer(file).until { !it.loading && it.pins.isNotEmpty() }
        assertThat(s.pins.map { it.title }).containsExactly("Kept")
    }

    @Test fun choosingADeletedTaskSaysSo() {
        val file = plan()
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Gone soon")) })
        val vm = viewer(file)
        vm.until { !it.loading && it.choices.isNotEmpty() }
        vm.onPinEvent(FileViewerEvent.StartPinning)
        vm.onPinEvent(FileViewerEvent.TapPage(0, 0.5f, 0.5f))
        runBlocking { tasks.delete(id) }
        vm.onPinEvent(FileViewerEvent.ChooseTask(id))
        assertThat(vm.until { it.pinFailed }.pinFailed).isTrue()
        vm.onPinEvent(FileViewerEvent.DismissPinFailed)
        assertThat(vm.state.value.pinFailed).isFalse()
    }

    // R09g: losing the right to manage tasks while the chooser is open closes it, and choosing re-checks the role before writing
    @Test fun losingTheRoleWhileChoosingPinsNothing() {
        val file = plan()
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Patch the soffit")) })
        val vm = viewer(file)
        vm.until { !it.loading && it.choices.isNotEmpty() }
        vm.onPinEvent(FileViewerEvent.StartPinning)
        vm.onPinEvent(FileViewerEvent.TapPage(0, 0.5f, 0.5f))
        manage.value = false
        val s = vm.until { !it.canPinTasks }
        assertThat(s.spot).isNull()
        assertThat(s.pinMode).isFalse()
        vm.onPinEvent(FileViewerEvent.ChooseTask(id))
        assertThat(runBlocking { tasks.get(id) }?.filePin).isNull()
    }

    @Test fun chooseAsksForTheRoleAgainBeforeWriting() {
        val file = plan()
        val id = checkNotNull(runBlocking { tasks.add("j1", TaskDraft("Patch the soffit")) })
        // The screen's own watch still says Lead; the role on the phone has just changed (the gate is asked again on Choose).
        var asks = 0
        val vm = scopes.track(FileViewerViewModel(
            FileViewerDeps(
                file, db.jobFileDao(), { _, _ -> pages },
                tasks = tasks::observeForJob, canManageTasks = { flowOf(asks++ == 0) }, me = "m1", pinOnFile = tasks::pinOnFile,
            ),
        ))
        vm.until { !it.loading && it.choices.isNotEmpty() }
        vm.onPinEvent(FileViewerEvent.StartPinning)
        vm.onPinEvent(FileViewerEvent.TapPage(0, 0.5f, 0.5f))
        vm.onPinEvent(FileViewerEvent.ChooseTask(id))
        runBlocking { kotlinx.coroutines.delay(50) }
        assertThat(asks).isAtLeast(2)
        assertThat(runBlocking { tasks.get(id) }?.filePin).isNull()
        assertThat(vm.state.value.pinFailed).isFalse()
    }
}
