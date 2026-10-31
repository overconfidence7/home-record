package com.kurtz.homerecord.fieldlog.tasks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.TaskFilter
import com.kurtz.homerecord.core.task.JobTasks
import com.kurtz.homerecord.core.task.TaskDraft
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

// AC-F34: ticking a task completes it with Undo; the punch list is a filter; Crew tick only their own (AC-F3)
@AcceptanceCriterion("AC-F34")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TasksViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var tasks: JobTasks
    private val manage = MutableStateFlow(true)
    private val vms = mutableListOf<TasksViewModel>()
    private lateinit var mine: String
    private lateinit var theirs: String

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        tasks = JobTasks(db)
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            mine = checkNotNull(tasks.add("j1", TaskDraft("Set the outlets", assigneeId = "m2", due = LocalDate.of(2026, 10, 31))))
            theirs = checkNotNull(tasks.add("j1", TaskDraft("Cover plate", assigneeId = "m1", punch = true)))
        }
    }

    @After fun tearDown() {
        vms.forEach { runBlocking { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(me: String? = "m9", saved: SavedStateHandle = SavedStateHandle()) =
        TasksViewModel(TasksDeps("j1", tasks, canManage = manage, me = me, today = { LocalDate.of(2026, 10, 31) }), saved).also { vms += it }

    private fun TasksViewModel.current(until: (TasksUiState) -> Boolean = { true }): TasksUiState = runBlocking {
        withTimeout(5_000) { state.first { !it.loading && until(it) } }
    }

    @Test fun tickMovesTaskToDoneAndUndoBringsItBack() {
        val vm = vm()
        assertThat(vm.current { it.open.size == 2 }.open.map { it.title }).containsExactly("Set the outlets", "Cover plate").inOrder()
        vm.onEvent(TasksEvent.SetDone(mine, true))
        val done = vm.current { it.done.isNotEmpty() && it.undo != null }
        assertThat(done.done.map { it.id }).containsExactly(mine)
        assertThat(done.undo).isEqualTo(TaskUndo(mine, "Set the outlets"))
        vm.onEvent(TasksEvent.Undo)
        val back = vm.current { it.done.isEmpty() && it.undo == null }
        assertThat(back.open.map { it.id }).containsExactly(mine, theirs).inOrder()
        assertThat(runBlocking { tasks.get(mine) }?.done).isFalse()
    }

    @Test fun dismissingTheSnackbarKeepsItDone() {
        val vm = vm()
        vm.current { it.open.size == 2 }
        vm.onEvent(TasksEvent.SetDone(mine, true))
        vm.current { it.undo != null }
        vm.onEvent(TasksEvent.DismissUndo)
        val s = vm.current { it.undo == null }
        assertThat(s.done.map { it.id }).containsExactly(mine)
    }

    @Test fun punchFilterIsKeptAcrossProcessDeath() {
        val saved = SavedStateHandle()
        vm(saved = saved).onEvent(TasksEvent.SetFilter(TaskFilter.PUNCH))
        val again = vm(saved = saved).current { it.filter == TaskFilter.PUNCH && it.total > 0 }
        assertThat(again.open.map { it.id }).containsExactly(theirs)
    }

    @Test fun crewSeeTheirOwnAndCannotTickSomeoneElses() {
        manage.value = false
        val vm = vm(me = "m2")
        val s = vm.current { it.total == 1 }
        assertThat(s.open.map { it.id }).containsExactly(mine)
        assertThat(s.canManage).isFalse()
        vm.onEvent(TasksEvent.SetDone(theirs, true))
        vm.onEvent(TasksEvent.SetDone(mine, true))
        vm.current { it.done.isNotEmpty() }
        assertThat(runBlocking { tasks.get(theirs) }?.done).isFalse()
        assertThat(runBlocking { tasks.get(mine) }?.done).isTrue()
    }
}
