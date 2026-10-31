package com.kurtz.homerecord.fieldlog.today

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class TodayViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val created = mutableListOf<TodayViewModel>()

    @After fun tearDown() {
        // A collector left on a live flow would touch Dispatchers.Main while the next test class sets it.
        kotlinx.coroutines.runBlocking { created.forEach { it.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    // AC-F16
    @Test fun opensByRefreshingTheTeamList() {
        var refreshed = 0
        val deps = TodayDeps(
            memberId = "me", base = TodayUiState("Kurtz Electric", CompanyRole.Owner),
            punches = flowOf(emptyList()), members = flowOf(emptyList()), jobs = flowOf(emptyList()),
            assignments = flowOf(emptyList()), refreshMembers = { refreshed++ }, ticks = flowOf(Unit),
        )
        TodayViewModel(deps)
        assertThat(refreshed).isEqualTo(1)
    }

    // AC-F16: pull to refresh asks for a sync and reloads the team, and the indicator stays until the team list is back.
    @Test fun pullToRefreshSyncsAndReloadsTheTeam() {
        var refreshed = 0
        var syncs = 0
        val gate = CompletableDeferred<Unit>()
        val deps = TodayDeps(
            memberId = "me", base = TodayUiState("Kurtz Electric", CompanyRole.Owner),
            punches = flowOf(emptyList()), members = flowOf(emptyList()), jobs = flowOf(emptyList()),
            assignments = flowOf(emptyList()), refreshMembers = { refreshed++; if (refreshed > 1) gate.await() },
            requestSync = { syncs++ }, ticks = flowOf(Unit),
        )
        val vm = TodayViewModel(deps)
        vm.refresh()
        assertThat(vm.state.value.refreshing).isTrue()
        assertThat(syncs).isEqualTo(1)
        assertThat(refreshed).isEqualTo(2)
        vm.refresh() // a second pull while one runs does nothing
        assertThat(syncs).isEqualTo(1)
        gate.complete(Unit)
        assertThat(vm.state.value.refreshing).isFalse()
    }

    // AC-F16: offline, the team refresh fails; the indicator still goes away and the cached list stays.
    @Test fun pullToRefreshEndsWhenTheTeamRefreshFails() {
        var calls = 0
        val deps = TodayDeps(
            memberId = "me", base = TodayUiState("Kurtz Electric", CompanyRole.Owner),
            punches = flowOf(emptyList()), members = flowOf(emptyList()), jobs = flowOf(emptyList()),
            assignments = flowOf(emptyList()), refreshMembers = { if (++calls > 1) throw java.io.IOException("offline") }, ticks = flowOf(Unit),
        )
        val vm = TodayViewModel(deps)
        vm.refresh()
        assertThat(vm.state.value.refreshing).isFalse()
    }

    // AC-F34: ticking a task on Today marks it done and offers Undo; Undo opens it again; the row leaves the list.
    @Test fun completingATaskOffersUndoAndUndoReopensIt() {
        val tasks = kotlinx.coroutines.flow.MutableStateFlow(
            listOf(com.kurtz.homerecord.core.model.JobTask("t1", "j1", "Label the panel", assigneeId = "me", due = java.time.LocalDate.of(2026, 10, 25))),
        )
        val calls = mutableListOf<Pair<String, Boolean>>()
        val deps = TodayDeps(
            memberId = "me", base = TodayUiState("Kurtz Electric", CompanyRole.Crew),
            punches = flowOf(emptyList()), members = flowOf(emptyList()),
            jobs = flowOf(listOf(com.kurtz.homerecord.core.data.JobEntity(id = "j1", companyId = "c", name = "Kitchen Remodel", createdAt = 0, updatedAt = 0))),
            assignments = flowOf(emptyList()), ticks = flowOf(Unit), tasks = tasks,
            setTaskDone = { id, done ->
                calls += id to done
                tasks.value = tasks.value.map { if (it.id == id) it.copy(done = done) else it }
                true
            },
            zone = java.time.ZoneOffset.UTC, now = { java.time.Instant.parse("2026-10-25T15:00:00Z").toEpochMilli() },
        )
        val vm = TodayViewModel(deps).also { created += it }
        assertThat(vm.state.value.tasks.map { it.title }).containsExactly("Label the panel")
        vm.completeTask("t1")
        assertThat(calls).containsExactly("t1" to true)
        assertThat(vm.state.value.tasks).isEmpty()
        assertThat(vm.state.value.taskUndo).isEqualTo(com.kurtz.homerecord.fieldlog.tasks.TaskUndo("t1", "Label the panel"))
        vm.undoTask()
        assertThat(calls).containsExactly("t1" to true, "t1" to false).inOrder()
        assertThat(vm.state.value.taskUndo).isNull()
        assertThat(vm.state.value.tasks.map { it.id }).containsExactly("t1")
        vm.completeTask("t1")
        vm.dismissTaskUndo()
        assertThat(vm.state.value.taskUndo).isNull()
        assertThat(calls).hasSize(3)
    }
}
