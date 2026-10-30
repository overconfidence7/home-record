package com.kurtz.homerecord.housemanual

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
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
        ItemRow("1", "Furnace", "Carrier", null, ItemCategory.HEATING_COOLING, "furnace", "r1", "Basement", WarrantyStatus.Expired("2024-01-01")),
        ItemRow("2", "Dishwasher", "Bosch", null, ItemCategory.APPLIANCES, "dishwasher", "r2", "Kitchen", WarrantyStatus.Unknown),
    )

    private fun vm(saved: SavedStateHandle) = ItemsViewModel(MutableStateFlow(rows), saved) {
        if (it == ItemCategory.HEATING_COOLING) "Heating and cooling" else it.name
    }

    // AC-H6, cr:label-strings: the category filter keys on the enum (saved by id, not by English text) and a search
    // matches the category name the screen shows
    @Test @AcceptanceCriterion("AC-H6")
    fun categoryFilterKeysOnTheEnumAndSearchMatchesItsName() {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        val job = vm.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        assertThat(vm.state.value.categoryOptions).containsExactly(ItemCategory.APPLIANCES, ItemCategory.HEATING_COOLING).inOrder()
        vm.onEvent(ItemsEvent.SetCategory(ItemCategory.HEATING_COOLING))
        assertThat(saved.get<String>("category")).isEqualTo("HEATING_COOLING")
        assertThat(vm.state.value.groups.flatMap { it.items }.map { it.id }).containsExactly("1")
        val again = vm(saved)
        val job2 = again.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        assertThat(again.state.value.filter.category).isEqualTo(ItemCategory.HEATING_COOLING)
        again.onEvent(ItemsEvent.ClearFilters)
        again.onEvent(ItemsEvent.SetQuery("heating"))
        assertThat(again.state.value.groups.flatMap { it.items }.map { it.id }).containsExactly("1")
        job.cancel(); job2.cancel()
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun filtersAndGroupingApplyAndSurviveRecreation() {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        val job = vm.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        assertThat(vm.state.value.groups.map { it.category }).containsExactly(ItemCategory.APPLIANCES, ItemCategory.HEATING_COOLING).inOrder()
        vm.onEvent(ItemsEvent.SetGrouping(ItemGrouping.ROOM))
        vm.onEvent(ItemsEvent.SetWarranty(WarrantyFilter.EXPIRED))
        assertThat(vm.state.value.groups.map { it.title }).containsExactly("Basement")
        assertThat(vm.state.value.filtered).isTrue()
        val again = vm(saved)
        val job2 = again.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        assertThat(again.state.value.grouping).isEqualTo(ItemGrouping.ROOM)
        assertThat(again.state.value.filter.warranty).isEqualTo(WarrantyFilter.EXPIRED)
        again.onEvent(ItemsEvent.ClearFilters)
        assertThat(again.state.value.groups).hasSize(2)
        job.cancel(); job2.cancel()
    }

    // AC-H6, SPEC HM-Items "grouped by category (collapsible)": a folded group keeps its header and count, survives
    // recreation, and opens again while a search is typed so no match hides
    @Test @AcceptanceCriterion("AC-H6")
    fun groupsFoldAndStayFoldedAcrossRecreation() {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        val job = vm.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        vm.onEvent(ItemsEvent.ToggleGroup("APPLIANCES"))
        assertThat(vm.state.value.isCollapsed("APPLIANCES")).isTrue()
        assertThat(vm.state.value.isCollapsed("HEATING_COOLING")).isFalse()
        assertThat(vm.state.value.groups.first { it.key == "APPLIANCES" }.count).isEqualTo(1)
        val again = vm(saved)
        val job2 = again.state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        assertThat(again.state.value.isCollapsed("APPLIANCES")).isTrue()
        again.onEvent(ItemsEvent.SetQuery("dish"))
        assertThat(again.state.value.isCollapsed("APPLIANCES")).isFalse()
        again.onEvent(ItemsEvent.SetQuery(""))
        again.onEvent(ItemsEvent.ToggleGroup("APPLIANCES"))
        assertThat(again.state.value.isCollapsed("APPLIANCES")).isFalse()
        job.cancel(); job2.cancel()
    }
}
