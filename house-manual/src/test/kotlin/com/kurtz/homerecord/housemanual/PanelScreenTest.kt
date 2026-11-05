package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.LinkConfidence
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.SlotOccupancy
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.items.ItemDetailEvent
import com.kurtz.homerecord.housemanual.items.PanelSection
import com.kurtz.homerecord.housemanual.panel.PanelEvent
import com.kurtz.homerecord.housemanual.panel.PanelScreen
import com.kurtz.homerecord.housemanual.panel.PanelUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C40: the panel grid draws a two-pole breaker as one tall cell over both slots, a tandem as two halves, open slots as Open.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PanelScreenTest {
    @get:Rule val rule = createComposeRule()

    private val panel = Panel("p", "pi", 12, PanelLayout.NORTH_AMERICAN, 200, "Square D QO", null)
    private fun b(id: String, slots: List<Int>, label: String, type: BreakerType = BreakerType.STANDARD, at: Long = 1) =
        Breaker(id, "p", slots, slots.size, 20, type, label, LinkConfidence.UNKNOWN, at)

    private fun state(breakers: List<Breaker>, p: Panel = panel) =
        PanelUiState(loading = false, name = "Main panel", panel = p, breakers = breakers, grid = SlotOccupancy.grid(p.layout, p.slotCount, breakers))

    private fun show(state: PanelUiState, onEvent: (PanelEvent) -> Unit = {}) =
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { PanelScreen(state, onEvent) } }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aTwoPoleBreakerIsOneCellAsTallAsTwoSlots() {
        show(state(listOf(b("r", listOf(1, 3), "Range"), b("k", listOf(2), "Kitchen counter", BreakerType.GFCI))))
        val tall = rule.onNodeWithTag("hm_panel_slot_1").getBoundsInRoot()
        val single = rule.onNodeWithTag("hm_panel_slot_2").getBoundsInRoot()
        assertThat((tall.bottom - tall.top).value).isGreaterThan((single.bottom - single.top).value * 1.9f)
        rule.onNodeWithTag("hm_panel_slot_3").assertDoesNotExist()
        rule.onNodeWithContentDescription("Slots 1 and 3, two-pole, Range, 20 A").assertExists()
        rule.onNodeWithContentDescription("Slot 2, Kitchen counter, 20 A · GFCI").assertExists()
        rule.onNodeWithText("200 A main").assertExists()
        rule.onNodeWithText("12 slots, two columns").assertExists()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aTandemShowsBothBreakersInOneSlotAndOpenSlotsSayOpen() {
        show(state(listOf(b("a", listOf(5), "Porch lights"), b("g", listOf(5), "Garage door opener", at = 2))))
        rule.onNodeWithText("5A").assertExists()
        rule.onNodeWithText("5B").assertExists()
        rule.onNodeWithContentDescription("Slot 5, tandem: Porch lights, 20 A; Garage door opener, 20 A").assertExists()
        rule.onNodeWithContentDescription("Slot 4, open").assertExists()
        rule.onNodeWithTag("hm_panel_slot_4").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aDinPanelIsOneColumnInSlotOrder() {
        val din = panel.copy(layout = PanelLayout.DIN, slotCount = 6)
        show(state(listOf(b("d", listOf(1, 2), "Dryer")), din))
        val dryer = rule.onNodeWithTag("hm_panel_slot_1").getBoundsInRoot()
        val next = rule.onNodeWithTag("hm_panel_slot_3").getBoundsInRoot()
        assertThat(next.top.value).isGreaterThan(dryer.bottom.value - 1f)
        assertThat(next.left).isEqualTo(dryer.left)
        rule.onNodeWithText("6 slots, one row (DIN)").assertExists()
    }

    @Test
    fun aBreakerThatClashesIsListedUnderTheGridAndAnEmptyPanelSaysSo() {
        show(state(listOf(b("x", listOf(4), "Refrigerator"), b("y", listOf(4), "Garage outlets", BreakerType.STANDARD, at = 5).copy(poles = 1, slots = listOf(4, 6)))))
        rule.onNodeWithTag("hm_panel_unplaced").performScrollTo().assertExists()
        rule.onNodeWithText("Says slots 4, 6, which another breaker holds").assertExists()
    }

    @Test
    fun noBreakersYetSaysWhatTheGridHolds() {
        show(state(emptyList()))
        rule.onNodeWithTag("hm_panel_empty").assertExists()
        rule.onNodeWithText("0 breakers labeled").assertExists()
    }

    @Test
    fun backLeavesAndAMissingPanelSaysItIsGone() {
        val events = mutableListOf<PanelEvent>()
        show(PanelUiState(loading = false, missing = true)) { events += it }
        rule.onNodeWithTag("hm_panel_missing").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        assertThat(events).containsExactly(PanelEvent.Back)
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aPanelItemPageOpensItsSlots() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { PanelSection { events += it } } }
        rule.onNodeWithText("Slots and breakers").assertExists()
        rule.onNodeWithTag("hm_item_detail_panel").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.OpenPanel)
    }
}
