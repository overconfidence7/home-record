package com.kurtz.homerecord.housemanual

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.home.HomeEvent
import com.kurtz.homerecord.housemanual.home.HomeViewModel
import com.kurtz.homerecord.housemodel.home.AttentionKind
import com.kurtz.homerecord.housemodel.home.AttentionRow
import com.kurtz.homerecord.housemodel.home.HomeSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test @AcceptanceCriterion("AC-H2")
    fun stateFollowsTheSummaryAndWallActionShowsItsNote() {
        val summary = MutableStateFlow(HomeSummary(0, emptyList(), emptyList(), 0))
        val vm = HomeViewModel(summary, "Maple St")
        val job = vm.state.onEach { }.launchIn(CoroutineScope(Dispatchers.Main))
        assertThat(vm.state.value.attention).isEmpty()
        assertThat(vm.state.value.homeName).isEqualTo("Maple St")
        summary.value = HomeSummary(2, listOf(AttentionRow(AttentionKind.MISSING_MAKE_MODEL, "i", "i", "Fridge", "")), emptyList(), 1)
        assertThat(vm.state.value.shutoffCount).isEqualTo(2)
        assertThat(vm.state.value.attention).hasSize(1)
        assertThat(vm.state.value.wallNoteShown).isFalse()
        vm.onEvent(HomeEvent.PhotographWall)
        assertThat(vm.state.value.wallNoteShown).isTrue()
        job.cancel()
    }
}
