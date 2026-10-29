package com.kurtz.homerecord.fieldlog.acceptance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.clock.ClockEvent
import com.kurtz.homerecord.fieldlog.clock.ClockJobOption
import com.kurtz.homerecord.fieldlog.clock.ClockScreen
import com.kurtz.homerecord.fieldlog.clock.ClockUiState
import com.kurtz.homerecord.fieldlog.clock.CrewNotice
import com.kurtz.homerecord.fieldlog.clock.CrewPick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lead crew clock-in on a device (AC-F11): pick the crew, one tap, and the line saying who was punched. The punches
 * themselves (lead's location, clockedInBy, people already on the clock skipped) are ClockControllerTest and
 * ClockViewModelTest on the JVM; the walkthrough flow F04 has no second member to tick.
 */
@RunWith(AndroidJUnit4::class)
class ClockCrewAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val jobs = listOf(ClockJobOption("j-garage", "Garage subpanel", "40.0, -75.0", 40.0, -75.0))
    private val crew = listOf(CrewPick("m-dale", "Dale Harlan"), CrewPick("m-maria", "Maria Soto"), CrewPick("m-sam", "Sam Ortiz", onClock = true))

    @AcceptanceCriterion("AC-F11")
    @Test fun aLeadTicksTheCrewAndClocksThemAllInWithOneTap() {
        val events = mutableListOf<ClockEvent>()
        var state by mutableStateOf(
            ClockUiState(loading = false, jobs = jobs, selectedJobId = "j-garage", canLeadCrew = true, crew = crew),
        )
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "fl") {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                    ClockScreen(state, onEvent = { e ->
                        events += e
                        if (e is ClockEvent.ToggleCrew) {
                            state = state.copy(crewSelected = if (e.id in state.crewSelected) state.crewSelected - e.id else state.crewSelected + e.id)
                        }
                    })
                }
            }
        }
        rule.onNodeWithTag("fl_clock_list").performScrollToNode(hasTestTag("fl_clock_crew_in"))
        rule.onNodeWithTag("fl_clock_crew_in").assertIsNotEnabled()
        rule.onNodeWithTag("fl_clock_crew_m-sam").assertIsNotEnabled()

        rule.onNodeWithTag("fl_clock_crew_m-dale").performClick()
        rule.onNodeWithTag("fl_clock_crew_m-maria").performClick()
        rule.onNodeWithTag("fl_clock_crew_m-dale").assertIsOn()
        rule.onNodeWithTag("fl_clock_crew_in").assertIsEnabled().performClick()
        assertThat(events).containsAtLeast(ClockEvent.ToggleCrew("m-dale"), ClockEvent.ToggleCrew("m-maria"), ClockEvent.ClockInCrew).inOrder()

        state = state.copy(crewSelected = emptySet(), crewNotice = CrewNotice(punched = 2, skippedNames = emptyList()))
        rule.onNodeWithTag("fl_clock_list").performScrollToNode(hasTestTag("fl_clock_crew_notice"))
        rule.onNodeWithTag("fl_clock_crew_notice").assertIsDisplayed()
    }
}
