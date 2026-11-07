package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.core.units.UnitsStore
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.more.AboutScreen
import com.kurtz.homerecord.housemanual.more.AboutUiState
import com.kurtz.homerecord.housemanual.more.MoreDialog
import com.kurtz.homerecord.housemanual.more.MoreEvent
import com.kurtz.homerecord.housemanual.more.MoreScreen
import com.kurtz.homerecord.housemanual.more.MoreUiState
import com.kurtz.homerecord.housemanual.more.MoreViewModel
import com.kurtz.homerecord.housemodel.reminders.ClimateZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

// T077 (More shell, AC-U3, AC-C4, AC-H12): HM-More lists every SPEC row as a ListItem with an icon; Units and Climate zone pick from the list.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MoreScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun show(state: MoreUiState, events: MutableList<MoreEvent> = mutableListOf()) =
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { MoreScreen(state, { events += it }) } }

    @Test
    @AcceptanceCriterion("AC-U3")
    fun everySpecRowIsThereInOrder() {
        show(MoreUiState(zone = ClimateZone.MIXED))
        val tags = listOf("emergency", "panels", "documents", "contacts", "report", "import", "backup", "account", "share", "units", "zone", "pro", "about")
        var lastTop = Float.NEGATIVE_INFINITY
        tags.forEach { t ->
            val node = rule.onNodeWithTag("hm_more_$t")
            node.assertExists()
            val top = node.fetchSemanticsNode().positionInRoot.y
            assertThat(top).isGreaterThan(lastTop)
            lastTop = top
        }
        rule.onNodeWithText("Feet and inches").assertExists()
        rule.onNodeWithText("Mixed").assertExists()
    }

    @Test
    @AcceptanceCriterion("AC-C4")
    fun unitsRowOpensThePickerAndPicksMetric() {
        val events = mutableListOf<MoreEvent>()
        show(MoreUiState(dialog = MoreDialog.UNITS), events)
        rule.onNodeWithTag("hm_more_units_imperial").assertIsSelected()
        rule.onNodeWithTag("hm_more_units_metric").performClick()
        assertThat(events).containsExactly(MoreEvent.SetUnits(UnitSystem.METRIC))
    }

    @Test
    @AcceptanceCriterion("AC-H12")
    fun zonePickerSendsThePickedZone() {
        val events = mutableListOf<MoreEvent>()
        show(MoreUiState(zone = ClimateZone.MIXED, dialog = MoreDialog.ZONE), events)
        rule.onNodeWithTag("hm_more_zone_mixed").assertIsSelected()
        rule.onNodeWithTag("hm_more_zone_cold").performClick()
        assertThat(events).containsExactly(MoreEvent.PickZone(ClimateZone.COLD))
    }

    @Test
    fun aViewerSeesTheZoneButCannotChangeIt() {
        show(MoreUiState(zone = ClimateZone.COLD, canEdit = false))
        rule.onNodeWithTag("hm_more_zone").performScrollTo().assertHasNoClickAction()
        rule.onNodeWithText("Cold, set by the owner").assertExists()
    }

    @Test
    fun aboutShowsTheVersion() {
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { AboutScreen(AboutUiState("2.0.0", 20000), {}) } }
        rule.onNodeWithText("2.0.0 (build 20000)").assertIsDisplayed()
    }

    @Test
    @AcceptanceCriterion("AC-C4")
    fun viewModelStoresUnitsAndSetsTheZoneOnlyWhenEditable() {
        val units = FakeUnits()
        val zone = MutableStateFlow<ClimateZone?>(null)
        val canEdit = MutableStateFlow(true)
        val picked = mutableListOf<ClimateZone>()
        val vm = MoreViewModel(units, zone, canEdit) { picked += it; zone.value = it }

        vm.onEvent(MoreEvent.OpenUnits)
        assertThat(vm.state.value.dialog).isEqualTo(MoreDialog.UNITS)
        vm.onEvent(MoreEvent.SetUnits(UnitSystem.METRIC))
        assertThat(units.units.value).isEqualTo(UnitSystem.METRIC)
        assertThat(vm.state.value.units).isEqualTo(UnitSystem.METRIC)
        assertThat(vm.state.value.dialog).isNull()

        vm.onEvent(MoreEvent.OpenZone)
        vm.onEvent(MoreEvent.PickZone(ClimateZone.HOT_DRY))
        assertThat(picked).containsExactly(ClimateZone.HOT_DRY)
        assertThat(vm.state.value.zone).isEqualTo(ClimateZone.HOT_DRY)

        canEdit.value = false
        vm.onEvent(MoreEvent.OpenZone)
        assertThat(vm.state.value.dialog).isNull()
        vm.onEvent(MoreEvent.PickZone(ClimateZone.COLD))
        assertThat(picked).containsExactly(ClimateZone.HOT_DRY)
        assertThat(vm.state.value.canEdit).isFalse()
    }

    private class FakeUnits : UnitsStore {
        private val s = MutableStateFlow(UnitSystem.IMPERIAL)
        override val units: StateFlow<UnitSystem> = s
        override fun set(system: UnitSystem) { s.value = system }
    }
}
