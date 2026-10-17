package com.kurtz.homerecord.housemanual.panel

import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// Tall window: the panel header (photo, profile, slot count, buttons) now precedes the lazily composed grid.
@Config(sdk = [34], qualifiers = "w360dp-h1400dp")
class PanelScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun panel(layout: PanelLayout = PanelLayout.NA_NUMBERED, slots: Int = 6) =
        Panel("p", "h", "Main", null, null, slots, layout, 0, 0, Source.HOUSE_MANUAL)

    @Test fun `AC-5 setup shows slot count default and disables save when invalid`() {
        val events = mutableListOf<PanelEvent>()
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true), { events += it }) } }
        rule.onNodeWithText("Set up your panel").assertIsDisplayed()
        rule.onNodeWithText("40").assertIsDisplayed()
        rule.onNodeWithTag("panel_save").performScrollTo().performClick()
        assertEquals(listOf<PanelEvent>(PanelEvent.SavePanel), events)
    }

    @Test fun `setup save is disabled for an invalid count`() {
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, slotCountInput = "1"), {}) } }
        rule.onNodeWithTag("panel_save").assertIsNotEnabled()
    }

    @Test fun `grid shows slots, breaker label and amps, and reports taps`() {
        val events = mutableListOf<PanelEvent>()
        val breaker = Breaker("b", "p", listOf(2), 1, 20, label = "Kitchen outlets", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)
        rule.setContent {
            HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel(), breakers = listOf(breaker)), { events += it }) }
        }
        rule.onNodeWithText("Kitchen outlets · 20A").assertIsDisplayed()
        rule.onNodeWithTag("slot_1").performClick()
        assertEquals(listOf<PanelEvent>(PanelEvent.OpenBreaker(1, null)), events)
    }

    @Test fun `AC-9 two-pole breaker is one cell with both slot numbers and a marker`() {
        val events = mutableListOf<PanelEvent>()
        val range = Breaker("r", "p", listOf(2, 4), 2, 50, label = "Range", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)
        rule.setContent {
            HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel(), breakers = listOf(range)), { events += it }) }
        }
        rule.onAllNodesWithText("Range · 50A").assertCountEquals(1)
        rule.onNodeWithText("2 · 4").assertIsDisplayed()
        rule.onNodeWithText("2-pole · 240 V").assertIsDisplayed()
        rule.onAllNodesWithTag("slot_4").assertCountEquals(0)
        rule.onNodeWithTag("slot_2").performClick()
        assertEquals(listOf<PanelEvent>(PanelEvent.OpenBreaker(2, "r")), events)
    }

    @Test fun `take photo button reports event`() {
        val events = mutableListOf<PanelEvent>()
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel(PanelLayout.DIN)), { events += it }) } }
        rule.onNodeWithText("Take panel photo").performClick()
        assertEquals(listOf<PanelEvent>(PanelEvent.TakePhoto), events)
    }

    @Test fun `AC-18 searched slot is scrolled into view and highlighted for two seconds`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel(slots = 40)), {}, highlightSlot = 39) }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("slot_39").assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        rule.mainClock.advanceTimeBy(HIGHLIGHT_MILLIS - 100)
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("slot_39").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test fun `AC-22 legend button emits ShareLegend`() {
        val events = mutableListOf<PanelEvent>()
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel()), { events += it }) } }
        rule.onNodeWithTag("panel_legend_pdf").performClick()
        assertEquals(listOf<PanelEvent>(PanelEvent.ShareLegend), events)
    }

    @Test fun `AC-101 setup form offers kind, rating, brand, tandem and main breaker choices`() {
        val events = mutableListOf<PanelEvent>()
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true), { events += it }) } }
        rule.onNodeWithTag("kind_SUB").performScrollTo().performClick()
        rule.onNodeWithTag("rating_200").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("brand_Square D QO").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("tandem_NOT_ALLOWED").performScrollTo().performClick()
        rule.onNodeWithTag("has_main_no").performScrollTo().performClick()
        val changes = events.filterIsInstance<PanelEvent.ProfileChanged>().map { it.input }
        assertEquals(com.kurtz.homerecord.core.model.PanelKind.SUB, changes[0].kind)
        assertEquals(com.kurtz.homerecord.core.model.TandemPolicy.NOT_ALLOWED, changes[1].tandemPolicy)
        assertEquals(false, changes[2].hasMainBreaker)
    }

    @Test fun `AC-103 main breaker is shown above the grid`() {
        val main = Breaker("m", "p", listOf(1, 3), 2, 200, label = "House main", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL, isMain = true)
        val events = mutableListOf<PanelEvent>()
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel(), breakers = listOf(main)), { events += it }) } }
        rule.onNodeWithTag("main_breaker_banner").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf<PanelEvent>(PanelEvent.OpenBreaker(1, "m")), events)
    }

    @Test fun `AC-102 grid cell shows the feeds note`() {
        val b = Breaker("b", "p", listOf(2), 1, 20, label = "Garage", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel(), breakers = listOf(b), feedNotes = mapOf("b" to "Shed sub")), {}) } }
        rule.onNodeWithText("Garage · 20A → feeds Shed sub").assertIsDisplayed()
    }

    @Test fun `AC-106 insights content renders capacity, cards, and the disclaimer`() {
        val result = com.kurtz.homerecord.core.logic.PanelInsightsResult(
            com.kurtz.homerecord.core.logic.PanelCapacity(8, 12, 7, 1, 40, 20),
            listOf(
                com.kurtz.homerecord.core.logic.Insight.Hazard(listOf(com.kurtz.homerecord.core.logic.HazardReason.FPE)),
                com.kurtz.homerecord.core.logic.Insight.Dedicated(com.kurtz.homerecord.core.logic.DedicatedRow.MICROWAVE, false),
            ),
        )
        rule.setContent { HomeRecordTheme { PanelInsightsContent(result, onClose = {}) } }
        rule.onNodeWithText("8 slots used, 12 free").assertIsDisplayed()
        rule.onNodeWithText("Home inspectors and many insurers flag this panel type; have a licensed electrician evaluate it.").assertIsDisplayed()
        rule.onNodeWithText("Microwave: not labeled yet — label it, or it may not exist.").assertIsDisplayed()
        rule.onNodeWithTag("insights_disclaimer").performScrollTo().assertIsDisplayed()
    }

    @Test fun `insights button asks to open the sheet`() {
        val events = mutableListOf<PanelEvent>()
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true, panel = panel()), { events += it }) } }
        rule.onNodeWithTag("panel_insights").performScrollTo().performClick()
        assertEquals(listOf<PanelEvent>(PanelEvent.ShowInsights), events)
    }
}
