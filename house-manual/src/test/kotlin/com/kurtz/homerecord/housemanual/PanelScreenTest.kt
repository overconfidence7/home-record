package com.kurtz.homerecord.housemanual

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.housemanual.panel.BreakerEditorContent
import com.kurtz.homerecord.housemanual.panel.BreakerEditorState
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.FeedOption
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
        val tall = rule.onNodeWithTag("hm_panel_slot_1").getUnclippedBoundsInRoot()
        val single = rule.onNodeWithTag("hm_panel_slot_2").getUnclippedBoundsInRoot()
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
        val dryer = rule.onNodeWithTag("hm_panel_slot_1").getUnclippedBoundsInRoot()
        val next = rule.onNodeWithTag("hm_panel_slot_3").getUnclippedBoundsInRoot()
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

    @Test
    @AcceptanceCriterion("AC-C40")
    fun tapASlotToEditItsBreakerAndTouchAndHoldForATandem() {
        val events = mutableListOf<PanelEvent>()
        show(state(listOf(b("r", listOf(1, 3), "Range"), b("k", listOf(2), "Kitchen counter"), b("a", listOf(5), "Porch lights"), b("g", listOf(5), "Garage door opener", at = 2)))) { events += it }
        rule.onNodeWithTag("hm_panel_hint").assertExists()
        rule.onNodeWithTag("hm_panel_slot_4").performScrollTo().performClick()
        rule.onNodeWithTag("hm_panel_slot_1").performScrollTo().performClick()
        rule.onNodeWithTag("hm_panel_slot_2").performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithTag("hm_panel_slot_5_b").performScrollTo().performClick()
        // R13: each tandem half is a 48 dp touch target
        rule.onNodeWithTag("hm_panel_slot_5_a").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("hm_panel_slot_5_b").assertHeightIsAtLeast(48.dp)
        assertThat(events).containsExactly(
            PanelEvent.OpenSlot(4), PanelEvent.OpenBreaker("r"), PanelEvent.AddTandem(2), PanelEvent.OpenBreaker("g"),
        ).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun theBreakerFormTakesLabelAmpsPolesTypeRoomsAndItemsAndAsksBeforeDelete() {
        val events = mutableListOf<PanelEvent>()
        val draft = BreakerDraft(id = "k", startSlot = 2, amps = "20", label = "Kitchen counter")
        val editor = BreakerEditorState(
            draft, placement = SlotPlacement.Ok(listOf(2)),
            rooms = listOf(FeedOption("kit", "Kitchen")), items = listOf(FeedOption("dw", "Dishwasher")),
        )
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { BreakerEditorContent(editor, { events += it }) } }
        rule.onNodeWithText("Slot 2").assertExists()
        rule.onNodeWithTag("hm_breaker_type_gfci").performScrollTo().performClick()
        rule.onNodeWithTag("hm_breaker_poles_2").performScrollTo().performClick()
        rule.onNodeWithText("Kitchen").performScrollTo().performClick()
        rule.onNodeWithText("Dishwasher").performScrollTo().performClick()
        rule.onNodeWithTag("hm_breaker_delete").performScrollTo().performClick()
        assertThat(events).containsExactly(
            PanelEvent.ChangeBreaker(draft.copy(type = BreakerType.GFCI)),
            PanelEvent.ChangeBreaker(draft.copy(poles = 2)),
            PanelEvent.ChangeBreaker(draft.copy(roomIds = setOf("kit"))),
            PanelEvent.ChangeBreaker(draft.copy(itemIds = setOf("dw"))),
            PanelEvent.AskDeleteBreaker,
        ).inOrder()
    }

    // AC-C41: the breaker shows what it feeds, rooms then items, above the pickers; nothing picked says so.
    @Test
    @AcceptanceCriterion("AC-C41")
    fun theBreakerFormSaysWhatItFeeds() {
        val rooms = listOf(FeedOption("kit", "Kitchen"), FeedOption("din", "Dining room"))
        val items = listOf(FeedOption("dw", "Dishwasher"), FeedOption("fr", "Refrigerator"))
        val feeding = BreakerEditorState(BreakerDraft(id = "k", startSlot = 2, roomIds = setOf("kit"), itemIds = setOf("fr", "dw")), rooms = rooms, items = items)
        assertThat(feeding.feeds).containsExactly("Kitchen", "Dishwasher", "Refrigerator").inOrder()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    BreakerEditorContent(feeding, {}, Modifier.weight(1f))
                    BreakerEditorContent(BreakerEditorState(BreakerDraft(startSlot = 4), rooms = rooms, items = items), {}, Modifier.weight(1f))
                }
            }
        }
        rule.onNodeWithText("Feeds Kitchen, Dishwasher, Refrigerator").assertExists()
        rule.onNodeWithText("Feeds nothing yet. Pick the rooms and items below.").assertExists()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun aTwoPoleIntoATakenSlotSaysWhichAndATandemHasNoPoleChoice() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    BreakerEditorContent(BreakerEditorState(BreakerDraft(startSlot = 10, poles = 2), placement = SlotPlacement.Taken(listOf(12))), {})
                    BreakerEditorContent(BreakerEditorState(BreakerDraft(startSlot = 4, tandem = true), placement = SlotPlacement.Ok(listOf(4))), {})
                }
            }
        }
        rule.onNodeWithText("Slot 12 already has a breaker. Pick 1 pole or another slot.").assertExists()
        rule.onNodeWithText("Slot 4, tandem").assertExists()
        assertThat(rule.onAllNodesWithTag("hm_breaker_poles_1").fetchSemanticsNodes()).hasSize(1)
        assertThat(rule.onAllNodesWithTag("hm_breaker_delete").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun thePanelPhotoAndEditOpenTheirFormsAndTheDetailsFormShowsWhyItRefuses() {
        val events = mutableListOf<PanelEvent>()
        show(state(emptyList())) { events += it }
        rule.onNodeWithTag("hm_panel_photo").performClick()
        rule.onNodeWithTag("hm_panel_edit").performClick()
        assertThat(events).containsExactly(PanelEvent.OpenPhotoSheet, PanelEvent.EditFacts).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C40")
    fun panelDetailsSayTheBreakersNeedTheSlotsAndKeepTheLayout() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                com.kurtz.homerecord.housemanual.panel.PanelFactsContent(
                    com.kurtz.homerecord.housemanual.panel.PanelFactsState(
                        com.kurtz.homerecord.housemodel.panel.PanelDraft("20"), showErrors = true, highestUsed = 23, layoutLocked = true,
                    ),
                    {},
                )
            }
        }
        rule.onNodeWithText("Breakers fill slots up to 23, so the panel needs at least 23").assertExists()
        rule.onNodeWithText("The layout can't change once breakers are labeled, since each one would move.").assertExists()
        assertThat(rule.onAllNodesWithTag("hm_panel_facts_layout_din").fetchSemanticsNodes()).isEmpty()
    }

    // T076c2f: a viewer cannot edit (AC-H16): no Map or Edit, the photo and slots take no tap, the hint drops "Tap a slot".
    @Test
    @AcceptanceCriterion("AC-H16")
    fun aViewerSeesTheSlotsButCannotEditThem() {
        show(state(listOf(b("k", listOf(2), "Kitchen counter"))).copy(viewOnly = true))
        rule.onNodeWithTag("hm_panel_map").assertDoesNotExist()
        rule.onNodeWithTag("hm_panel_edit").assertDoesNotExist()
        rule.onNodeWithTag("hm_panel_legend").assertExists()
        rule.onNodeWithTag("hm_panel_photo").assertHasNoClickAction()
        rule.onNodeWithTag("hm_panel_slot_1").assertHasNoClickAction()
        rule.onNodeWithTag("hm_panel_slot_2").assertHasNoClickAction()
        rule.onNodeWithText("Each slot shows what its breaker powers.").assertExists()
    }
}
