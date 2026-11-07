package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffGroups
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.contacts.ContactsEvent
import com.kurtz.homerecord.housemanual.contacts.ContactsScreen
import com.kurtz.homerecord.housemanual.contacts.ContactsUiState
import com.kurtz.homerecord.housemanual.documents.DocumentsEvent
import com.kurtz.homerecord.housemanual.documents.DocumentsScreen
import com.kurtz.homerecord.housemanual.documents.DocumentsUiState
import com.kurtz.homerecord.housemanual.emergency.EmergencyEvent
import com.kurtz.homerecord.housemanual.emergency.EmergencyScreen
import com.kurtz.homerecord.housemanual.emergency.EmergencyUiState
import com.kurtz.homerecord.housemanual.emergency.EmergencyViewModel
import com.kurtz.homerecord.housemanual.panel.PanelsEvent
import com.kurtz.homerecord.housemanual.panel.PanelsScreen
import com.kurtz.homerecord.housemanual.panel.PanelsUiState
import com.kurtz.homerecord.housemanual.panel.PanelsViewModel
import com.kurtz.homerecord.housemanual.reminders.RemindersEvent
import com.kurtz.homerecord.housemanual.reminders.RemindersScreen
import com.kurtz.homerecord.housemanual.reminders.RemindersUiState
import com.kurtz.homerecord.housemanual.home.HomeEvent
import com.kurtz.homerecord.housemanual.home.HomeScreen
import com.kurtz.homerecord.housemanual.home.HomeUiState
import com.kurtz.homerecord.housemanual.home.HomeViewModel
import com.kurtz.homerecord.housemodel.home.HomeSummary
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.ui.FloorsEvent
import com.kurtz.homerecord.housemodel.ui.ItemPlaceEvent
import com.kurtz.homerecord.housemodel.ui.ItemPlaceScreen
import com.kurtz.homerecord.housemodel.ui.ItemPlaceUiState
import com.kurtz.homerecord.housemodel.ui.FloorsScreen
import com.kurtz.homerecord.housemodel.ui.FloorsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
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

// AC-U4: every list has an error state. When a list's read throws, Emergency, Breaker panels, Contacts, Documents
// Reminders, Home and Floors show what failed and a Retry that reads again, with no Add button over a list that never loaded.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-U4")
class ListLoadFailedTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    /** A read that fails the first time ("database disk image is malformed") and works after that. */
    private fun <T> failsOnce(value: T): kotlinx.coroutines.flow.Flow<T> {
        var calls = 0
        return flow {
            calls++
            if (calls == 1) throw IllegalStateException("database disk image is malformed")
            emit(value)
        }
    }

    @Test fun emergencyShowsTheErrorStateAndRetryReadsAgain() {
        val sections = ShutoffGroups.sections(listOf(Shutoff("w", "Water main shutoff", "water_main", "Utility room", "Quarter turn.", null, 1L)))
        val vm = EmergencyViewModel(failsOnce(sections))
        vm.state.launchIn(vm.viewModelScope)
        assertThat(vm.state.value.loadFailed).isTrue()
        assertThat(vm.state.value.loading).isFalse()
        vm.retry()
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.count).isEqualTo(1)
        vm.viewModelScope.cancel()
    }

    @Test fun panelsShowTheErrorStateAndRetryReadsAgain() {
        val panel = ItemRow("p1", "Main panel", null, null, ItemCategory.ELECTRICAL, "breaker_panel", null, "", WarrantyStatus.Unknown, createdAt = 1L)
        val vm = PanelsViewModel(failsOnce(listOf(panel)))
        vm.state.launchIn(vm.viewModelScope)
        assertThat(vm.state.value.loadFailed).isTrue()
        vm.retry()
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.panels.map { it.id }).containsExactly("p1")
        vm.viewModelScope.cancel()
    }

    private fun show(content: @Composable () -> Unit) = rule.setContent { HomeRecordTheme(Brand.HouseManual) { content() } }

    @Test fun emergencyRetrySendsTheEvent() {
        val events = mutableListOf<EmergencyEvent>()
        show { EmergencyScreen(EmergencyUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("hm_emergency_retry").performClick()
        rule.onAllNodesWithTag("hm_emergency_add").assertCountEquals(0)
        assertThat(events).containsExactly(EmergencyEvent.Retry)
    }

    @Test fun panelsRetrySendsTheEvent() {
        val events = mutableListOf<PanelsEvent>()
        show { PanelsScreen(PanelsUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("hm_panels_retry").performClick()
        rule.onAllNodesWithTag("hm_panels_add").assertCountEquals(0)
        assertThat(events).containsExactly(PanelsEvent.Retry)
    }

    @Test fun contactsRetrySendsTheEventAndHidesAdd() {
        val events = mutableListOf<ContactsEvent>()
        show { ContactsScreen(ContactsUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("hm_contacts_retry").performClick()
        rule.onAllNodesWithTag("hm_contacts_fab").assertCountEquals(0)
        assertThat(events).containsExactly(ContactsEvent.Retry)
    }

    @Test fun documentsRetrySendsTheEventAndHidesAdd() {
        val events = mutableListOf<DocumentsEvent>()
        show { DocumentsScreen(DocumentsUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("hm_documents_retry").performClick()
        rule.onAllNodesWithTag("hm_documents_add").assertCountEquals(0)
        rule.onAllNodesWithTag("hm_documents_fab").assertCountEquals(0)
        assertThat(events).containsExactly(DocumentsEvent.Retry)
    }

    @Test fun remindersRetrySendsTheEventAndHidesAdd() {
        val events = mutableListOf<RemindersEvent>()
        show { RemindersScreen(RemindersUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("hm_reminders_retry").performClick()
        rule.onAllNodesWithTag("hm_reminders_fab").assertCountEquals(0)
        assertThat(events).containsExactly(RemindersEvent.Retry)
    }

    @Test fun homeShowsTheErrorStateAndRetryReadsAgain() {
        val vm = HomeViewModel(failsOnce(HomeSummary(1, emptyList(), emptyList(), 3)), "12 Maple Street")
        vm.state.launchIn(vm.viewModelScope)
        assertThat(vm.state.value.loadFailed).isTrue()
        vm.onEvent(HomeEvent.Retry)
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.itemCount).isEqualTo(3)
        vm.viewModelScope.cancel()
    }

    @Test fun homeRetrySendsTheEvent() {
        val events = mutableListOf<HomeEvent>()
        show { HomeScreen(HomeUiState(loading = false, loadFailed = true), { events += it }) }
        rule.onNodeWithTag("hm_home_retry").performClick()
        rule.onAllNodesWithTag("hm_home_search").assertCountEquals(0)
        assertThat(events).containsExactly(HomeEvent.Retry)
    }

    @Test fun floorsRetrySendsTheEventAndHidesAdd() {
        val events = mutableListOf<FloorsEvent>()
        show { FloorsScreen(FloorsUiState(loading = false, loadFailed = true), { events += it }, {}, "Maple St") }
        rule.onNodeWithTag("hm_floors_retry").performClick()
        rule.onAllNodesWithTag("hm_floors_fab").assertCountEquals(0)
        assertThat(events).containsExactly(FloorsEvent.Retry)
    }

    @Test fun itemPlaceRetrySendsTheEventAndHidesDone() {
        val events = mutableListOf<ItemPlaceEvent>()
        show { ItemPlaceScreen(ItemPlaceUiState(loading = false, loadFailed = true, itemId = "i3"), { events += it }) }
        rule.onNodeWithTag("hm_item_place_retry").performClick()
        rule.onAllNodesWithTag("hm_item_place_done").assertCountEquals(0)
        rule.onAllNodesWithTag("hm_item_place_plan").assertCountEquals(0)
        assertThat(events).containsExactly(ItemPlaceEvent.Retry)
    }
}
