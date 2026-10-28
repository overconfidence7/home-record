package com.kurtz.homerecord.fieldlog.today

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import kotlinx.coroutines.CompletableDeferred
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

    @After fun tearDown() = Dispatchers.resetMain()

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
}
