package com.kurtz.homerecord.fieldlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.changes.ChangesEvent
import com.kurtz.homerecord.fieldlog.changes.ChangesScreen
import com.kurtz.homerecord.fieldlog.changes.ChangesUiState
import com.kurtz.homerecord.fieldlog.clients.ClientsDeps
import com.kurtz.homerecord.fieldlog.clients.ClientsEvent
import com.kurtz.homerecord.fieldlog.clients.ClientsScreen
import com.kurtz.homerecord.fieldlog.clients.ClientsUiState
import com.kurtz.homerecord.fieldlog.clients.ClientsViewModel
import com.kurtz.homerecord.fieldlog.sync.SyncLogDeps
import com.kurtz.homerecord.fieldlog.sync.SyncLogEvent
import com.kurtz.homerecord.fieldlog.sync.SyncLogScreen
import com.kurtz.homerecord.fieldlog.sync.SyncLogUiState
import com.kurtz.homerecord.fieldlog.sync.SyncLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-U4: every list has an error state. When a read throws, FL Clients, a job's Changes tab and the Sync log show what
// failed and a Retry that reads again, instead of the ViewModel ending with the exception.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-U4")
class ListLoadFailedTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    /** A read that fails the first time ("database disk image is malformed") and works after that. */
    private fun <T> failsOnce(value: T): Flow<T> {
        var calls = 0
        return flow {
            calls++
            if (calls == 1) throw IllegalStateException("database disk image is malformed")
            emit(value)
        }
    }

    @Test fun clientsShowTheErrorStateAndRetryReadsAgain() {
        val vm = ClientsViewModel(ClientsDeps(failsOnce(emptyList()), flowOf(emptyList()), today = { "2026-11-07" }))
        vm.state.launchIn(vm.viewModelScope)
        assertThat(vm.state.value.loadFailed).isTrue()
        assertThat(vm.state.value.loading).isFalse()
        vm.retry()
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.loading).isFalse()
        vm.viewModelScope.cancel()
    }

    @Test fun syncLogShowsTheErrorStateAndRetryReadsAgain() {
        val vm = SyncLogViewModel(SyncLogDeps(flowOf(com.kurtz.homerecord.core.sync.SyncPhase.Idle), flowOf(2), failsOnce(emptyList()), {}, {}))
        vm.state.launchIn(vm.viewModelScope)
        assertThat(vm.state.value.loadFailed).isTrue()
        vm.retry()
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.pending).isEqualTo(2)
        vm.viewModelScope.cancel()
    }

    private fun show(content: @Composable () -> Unit) = rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { content() } }

    @Test fun clientsRetrySendsTheEvent() {
        val events = mutableListOf<ClientsEvent>()
        show { ClientsScreen(ClientsUiState(loading = false, loadFailed = true), { events += it }, {}) }
        rule.onNodeWithTag("fl_clients_retry").performClick()
        assertThat(events).containsExactly(ClientsEvent.Retry)
    }

    @Test fun changesRetrySendsTheEventAndHidesNewChangeOrder() {
        val events = mutableListOf<ChangesEvent>()
        show { ChangesScreen(ChangesUiState(loading = false, loadFailed = true, canManage = true), { events += it }) }
        rule.onNodeWithTag("fl_changes_retry").performClick()
        rule.onAllNodesWithTag("fl_changes_add").assertCountEquals(0)
        assertThat(events).containsExactly(ChangesEvent.Retry)
    }

    @Test fun syncLogRetrySendsTheEvent() {
        val events = mutableListOf<SyncLogEvent>()
        show { SyncLogScreen(SyncLogUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("fl_sync_retry").performClick()
        assertThat(events).containsExactly(SyncLogEvent.Retry)
    }

    @Test fun scheduleShowsTheErrorStateAndRetryReadsAgain() {
        val vm = com.kurtz.homerecord.fieldlog.schedule.ScheduleViewModel(
            com.kurtz.homerecord.fieldlog.schedule.ScheduleDeps(
                "m1",
                failsOnce(listOf(com.kurtz.homerecord.core.data.MemberEntity("m1", "c1", name = "Dana Ortiz", role = "OWNER", createdAt = 1, updatedAt = 1))),
                flowOf(emptyList()), flowOf(emptyList()),
                flowOf(com.kurtz.homerecord.core.company.RoleGates(com.kurtz.homerecord.core.company.CompanyRole.Owner, selfId = "m1")),
                assign = { _, _, _ -> null }, move = { _, _ -> false }, remove = { false },
                ticks = kotlinx.coroutines.flow.emptyFlow(),
            ),
        )
        assertThat(vm.state.value.loadFailed).isTrue()
        assertThat(vm.state.value.loading).isFalse()
        vm.onEvent(com.kurtz.homerecord.fieldlog.schedule.ScheduleEvent.Retry)
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.rows.map { it.name }).containsExactly("Dana Ortiz")
        vm.viewModelScope.cancel()
    }

    @Test fun scheduleRetrySendsTheEventAndHidesTheWeek() {
        val events = mutableListOf<com.kurtz.homerecord.fieldlog.schedule.ScheduleEvent>()
        show { com.kurtz.homerecord.fieldlog.schedule.ScheduleScreen(com.kurtz.homerecord.fieldlog.schedule.ScheduleUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("fl_schedule_retry").performClick()
        rule.onAllNodesWithTag("fl_schedule_week").assertCountEquals(0)
        assertThat(events).containsExactly(com.kurtz.homerecord.fieldlog.schedule.ScheduleEvent.Retry)
    }

    @Test fun clockRetrySendsTheEventAndHidesClockIn() {
        val events = mutableListOf<com.kurtz.homerecord.fieldlog.clock.ClockEvent>()
        show { com.kurtz.homerecord.fieldlog.clock.ClockScreen(com.kurtz.homerecord.fieldlog.clock.ClockUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("fl_clock_retry").performClick()
        rule.onAllNodesWithTag("fl_clock_in").assertCountEquals(0)
        assertThat(events).containsExactly(com.kurtz.homerecord.fieldlog.clock.ClockEvent.Retry)
    }

    @Test fun tasksRetrySendsTheEvent() {
        val events = mutableListOf<com.kurtz.homerecord.fieldlog.tasks.TasksEvent>()
        show { com.kurtz.homerecord.fieldlog.tasks.TasksScreen(com.kurtz.homerecord.fieldlog.tasks.TasksUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("fl_tasks_retry").performClick()
        rule.onAllNodesWithTag("fl_tasks_empty_add").assertCountEquals(0)
        assertThat(events).containsExactly(com.kurtz.homerecord.fieldlog.tasks.TasksEvent.Retry)
    }
}
