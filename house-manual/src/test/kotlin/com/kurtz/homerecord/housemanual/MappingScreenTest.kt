package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.mapping.MappingPhase
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.LinkConfidence
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.SlotOccupancy
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.mapping.MappingBreaker
import com.kurtz.homerecord.housemanual.mapping.MappingEvent
import com.kurtz.homerecord.housemanual.mapping.MappingScreen
import com.kurtz.homerecord.housemanual.mapping.MappingUiState
import com.kurtz.homerecord.housemanual.panel.PanelEvent
import com.kurtz.homerecord.housemanual.panel.PanelScreen
import com.kurtz.homerecord.housemanual.panel.PanelUiState
import com.kurtz.homerecord.housemodel.panel.FeedOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C42: HM-Mapping screens: Start, the alarm's Silence, and the pick that confirms a breaker for a room or item.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-C42")
class MappingScreenTest {
    @get:Rule val rule = createComposeRule()

    private val events = mutableListOf<MappingEvent>()
    private fun state(phase: MappingPhase) = MappingUiState(
        loading = false, panelName = "Main panel", phase = phase, plugged = true,
        breakers = listOf(MappingBreaker("b12", 12, "Kitchen outlets", 20), MappingBreaker("b14", 14, "", 15)),
        rooms = listOf(FeedOption("kitchen", "Kitchen")), items = listOf(FeedOption("fridge", "Refrigerator")),
    )

    private fun show(state: MappingUiState) =
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { MappingScreen(state) { events += it } } }

    @Test fun startIsOfferedWhenThePanelHasBreakers() {
        show(state(MappingPhase.IDLE))
        rule.onNodeWithText("The phone is charging.").assertExists()
        rule.onNodeWithTag("hm_mapping_start").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        assertThat(events).containsExactly(MappingEvent.Start)
    }

    @Test fun theAlarmHasOneLargeSilenceButton() {
        show(state(MappingPhase.ALARM))
        rule.onNodeWithText("Power lost").assertExists()
        rule.onNodeWithTag("hm_mapping_silence").assertHeightIsAtLeast(72.dp).performClick()
        assertThat(events).containsExactly(MappingEvent.Silence)
    }

    @Test fun pickingNeedsABreakerAndARoomOrItemBeforeSave() {
        show(state(MappingPhase.PICKING).copy(breakerId = "b12"))
        rule.onNodeWithText("Breaker 12 · Kitchen outlets").assertExists()
        rule.onNodeWithText("Breaker 14").assertExists()
        rule.onNodeWithTag("hm_mapping_save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Kitchen").performScrollTo().performClick()
        rule.onNodeWithTag("hm_mapping_skip").performScrollTo().performClick()
        assertThat(events).containsExactly(MappingEvent.PickRoom("kitchen"), MappingEvent.Skip).inOrder()
    }

    @Test fun saveIsOnOnceTheLinkIsComplete() {
        show(state(MappingPhase.PICKING).copy(breakerId = "b12", itemId = "fridge"))
        rule.onNodeWithTag("hm_mapping_save").performScrollTo().assertIsEnabled().performClick()
        assertThat(events).containsExactly(MappingEvent.Save)
    }

    @Test fun thePanelOffersMapBreakersOnceItHasBreakers() {
        val panel = Panel("p", "pi", 12, PanelLayout.NORTH_AMERICAN, 200, "Square D QO", null)
        val breakers = listOf(Breaker("b", "p", listOf(1), 1, 20, BreakerType.STANDARD, "Range", LinkConfidence.UNKNOWN, 1))
        val got = mutableListOf<PanelEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                PanelScreen(PanelUiState(loading = false, name = "Main panel", panel = panel, breakers = breakers, grid = SlotOccupancy.grid(panel.layout, panel.slotCount, breakers))) { got += it }
            }
        }
        rule.onNodeWithTag("hm_panel_map").performClick()
        assertThat(got).containsExactly(PanelEvent.Map)
    }
}
