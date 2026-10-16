package com.kurtz.homerecord.housemanual.panel

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
@Config(sdk = [34])
class PanelScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun panel(layout: PanelLayout = PanelLayout.NA_NUMBERED, slots: Int = 6) =
        Panel("p", "h", "Main", null, null, slots, layout, 0, 0, Source.HOUSE_MANUAL)

    @Test fun `AC-5 setup shows slot count default and disables save when invalid`() {
        val events = mutableListOf<PanelEvent>()
        rule.setContent { HomeRecordTheme { PanelScreen(PanelUiState(loaded = true), { events += it }) } }
        rule.onNodeWithText("Set up your panel").assertIsDisplayed()
        rule.onNodeWithText("40").assertIsDisplayed()
        rule.onNodeWithTag("panel_save").performClick()
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
}
