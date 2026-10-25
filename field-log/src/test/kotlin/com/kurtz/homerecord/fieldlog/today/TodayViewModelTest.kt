package com.kurtz.homerecord.fieldlog.today

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
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
}
