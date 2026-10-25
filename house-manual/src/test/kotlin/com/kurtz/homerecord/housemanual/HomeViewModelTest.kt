package com.kurtz.homerecord.housemanual

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.SearchDocument
import com.kurtz.homerecord.core.model.SearchKind
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
    fun stateFollowsTheSummary() {
        val summary = MutableStateFlow(HomeSummary(0, emptyList(), emptyList(), 0))
        val vm = HomeViewModel(summary, "Maple St")
        val job = vm.state.onEach { }.launchIn(CoroutineScope(Dispatchers.Main))
        assertThat(vm.state.value.attention).isEmpty()
        assertThat(vm.state.value.homeName).isEqualTo("Maple St")
        summary.value = HomeSummary(2, listOf(AttentionRow(AttentionKind.MISSING_MAKE_MODEL, "i", "i", "Fridge", "")), emptyList(), 1)
        assertThat(vm.state.value.shutoffCount).isEqualTo(2)
        assertThat(vm.state.value.attention).hasSize(1)
        job.cancel()
    }

    @Test @AcceptanceCriterion("AC-H3")
    fun queryFiltersTheIndexAndClearingItEmptiesResults() {
        val docs = listOf(
            SearchDocument(SearchKind.ITEM, "i1", "Furnace", "Carrier", listOf("Furnace", "59TP6A")),
            SearchDocument(SearchKind.ROOM, "r1", "Garage"),
        )
        val vm = HomeViewModel(MutableStateFlow(HomeSummary(0, emptyList(), emptyList(), 0)), "Maple St", MutableStateFlow(docs))
        val job = vm.state.onEach { }.launchIn(CoroutineScope(Dispatchers.Main))
        assertThat(vm.state.value.results).isEmpty()
        vm.onEvent(HomeEvent.QueryChanged("59tp6a"))
        assertThat(vm.state.value.searching).isTrue()
        assertThat(vm.state.value.results.map { it.id }).containsExactly("i1")
        vm.onEvent(HomeEvent.QueryChanged("garage"))
        assertThat(vm.state.value.results.map { it.kind }).containsExactly(SearchKind.ROOM)
        vm.onEvent(HomeEvent.QueryChanged(""))
        assertThat(vm.state.value.searching).isFalse()
        assertThat(vm.state.value.results).isEmpty()
        job.cancel()
    }
}
