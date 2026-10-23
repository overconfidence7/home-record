package com.kurtz.homerecord.housemanual

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.items.ItemsEvent
import com.kurtz.homerecord.housemanual.items.ItemsViewModel
import com.kurtz.homerecord.housemodel.items.ItemGrouping
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.items.WarrantyFilter
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
class ItemsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val rows = listOf(
        ItemRow("1", "Furnace", "Carrier", null, "Heating and cooling", 1, "furnace", "r1", "Basement", WarrantyStatus.Expired("2024-01-01")),
        ItemRow("2", "Dishwasher", "Bosch", null, "Appliances", 0, "dishwasher", "r2", "Kitchen", WarrantyStatus.Unknown),
    )

    @Test @AcceptanceCriterion("AC-H6")
    fun filtersAndGroupingApplyAndSurviveRecreation() {
        val saved = SavedStateHandle()
        val vm = ItemsViewModel(MutableStateFlow(rows), saved)
        val job = vm.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        assertThat(vm.state.value.groups.map { it.title }).containsExactly("Appliances", "Heating and cooling").inOrder()
        vm.onEvent(ItemsEvent.SetGrouping(ItemGrouping.ROOM))
        vm.onEvent(ItemsEvent.SetWarranty(WarrantyFilter.EXPIRED))
        assertThat(vm.state.value.groups.map { it.title }).containsExactly("Basement")
        assertThat(vm.state.value.filtered).isTrue()
        val again = ItemsViewModel(MutableStateFlow(rows), saved)
        val job2 = again.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        assertThat(again.state.value.grouping).isEqualTo(ItemGrouping.ROOM)
        assertThat(again.state.value.filter.warranty).isEqualTo(WarrantyFilter.EXPIRED)
        again.onEvent(ItemsEvent.ClearFilters)
        assertThat(again.state.value.groups).hasSize(2)
        job.cancel(); job2.cancel()
    }
}
