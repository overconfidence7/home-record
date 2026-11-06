package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffGroups
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.emergency.EmergencyEvent
import com.kurtz.homerecord.housemanual.emergency.EmergencyScreen
import com.kurtz.homerecord.housemanual.emergency.EmergencyUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C44: HM-Emergency groups shutoffs with mains first, instructions at 24 sp, "Manage shutoffs" at the bottom.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-C44")
class EmergencyScreenTest {
    @get:Rule val rule = createComposeRule()

    private val events = mutableListOf<EmergencyEvent>()
    private val sections = ShutoffGroups.sections(
        listOf(
            Shutoff("k", "Kitchen sink cold", "fixture_shutoff", "Kitchen", "Under the sink, left valve.", null, 1L),
            Shutoff("w", "Water main shutoff", "water_main", "Utility room", "Quarter turn on the red handle.", null, 2L),
            Shutoff("g", "Gas main shutoff", "gas_main", "Exterior", "", null, 3L),
        ),
    )

    private fun show(state: EmergencyUiState) = rule.setContent {
        HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { EmergencyScreen(state, { events += it }) }
    }

    @Test
    fun groupsWithTheMainFirstAndLargeInstructions() {
        show(EmergencyUiState(loading = false, sections = sections))
        rule.onNodeWithTag("hm_emergency_group_water").assertIsDisplayed()
        // Water main before the kitchen valve inside the Water group
        val rows = rule.onAllNodes(tagPrefix("hm_emergency_shutoff_"), useUnmergedTree = true).fetchSemanticsNodes()
        val tops = rows.map { it.boundsInRoot.top }
        assertThat(tops).isInOrder()
        assertThat(rule.onNodeWithText("Water main shutoff").fetchSemanticsNode().boundsInRoot.top)
            .isLessThan(rule.onNodeWithText("Kitchen sink cold").fetchSemanticsNode().boundsInRoot.top)
        // 24 sp instructions
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onAllNodesWithTag("hm_emergency_instructions").onFirst().fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertThat(layouts.single().layoutInput.style.fontSize.value).isEqualTo(24f)
        // A shutoff with no notes says where to add them
        val list = rule.onNodeWithTag("hm_emergency_list")
        list.performScrollToNode(hasText("No instructions yet. Open the shutoff and add them in Notes."))
        rule.onNodeWithText("No instructions yet. Open the shutoff and add them in Notes.").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("hm_emergency_manage"))
        rule.onNodeWithTag("hm_emergency_manage").assertIsDisplayed()
        rule.onNodeWithTag("hm_emergency_manage_add").performClick()
        assertThat(events.last()).isEqualTo(EmergencyEvent.Add)
    }

    @Test
    fun tappingAShutoffOpensItsItem() {
        show(EmergencyUiState(loading = false, sections = sections))
        rule.onNodeWithTag("hm_emergency_shutoff_water_1").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(events.last()).isEqualTo(EmergencyEvent.Open("w"))
    }

    @Test
    fun emptyHomeOffersToAddTheFirstShutoff() {
        show(EmergencyUiState(loading = false))
        rule.onNodeWithText("No shutoffs yet").assertIsDisplayed()
        rule.onNodeWithTag("hm_emergency_add").performClick()
        assertThat(events.last()).isEqualTo(EmergencyEvent.Add)
    }
}

/** Every node whose test tag starts with [prefix] (one tag per shutoff row). */
private fun tagPrefix(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("testTag starts with $prefix") {
    androidx.compose.ui.semantics.SemanticsProperties.TestTag in it.config && it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith(prefix)
}
