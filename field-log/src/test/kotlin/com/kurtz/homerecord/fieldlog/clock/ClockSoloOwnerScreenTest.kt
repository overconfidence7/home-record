package com.kurtz.homerecord.fieldlog.clock

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.fieldlog.ClockSoloOwnerPreview
import com.kurtz.homerecord.fieldlog.ClockCrewPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// judge:fl-clock-solo-owner-noise: a one-person company sees one "Invite crew" row, not an empty crew card with a
// disabled "Clock in 0 people" button; with a crew the card and its button are back.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ClockSoloOwnerScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun soloOwnerGetsOneInviteRowThatOpensTeam() {
        val events = mutableListOf<ClockEvent>()
        rule.setContent {
            com.kurtz.homerecord.fieldlog.clock.ClockScreen(
                ClockUiState(loading = false, canLeadCrew = true, crew = emptyList()), { events += it },
            )
        }
        rule.onNodeWithTag("fl_clock_list").performScrollToNode(hasTestTag("fl_clock_crew"))
        rule.onNodeWithText("Invite crew").assertExists()
        rule.onNodeWithTag("fl_clock_crew_in").assertDoesNotExist()
        rule.onNodeWithTag("fl_clock_crew").performClick()
        assertThat(events).contains(ClockEvent.InviteCrew)
    }

    @Test fun previewShowsTheRow() {
        rule.setContent { ClockSoloOwnerPreview() }
        rule.onNodeWithTag("fl_clock_list").performScrollToNode(hasTestTag("fl_clock_crew"))
        rule.onNodeWithTag("fl_clock_crew_in").assertDoesNotExist()
    }

    @Test fun withACrewTheCardAndButtonStay() {
        rule.setContent { ClockCrewPreview() }
        rule.onNodeWithTag("fl_clock_list").performScrollToNode(hasTestTag("fl_clock_crew_in"))
        rule.onNodeWithText("Invite crew").assertDoesNotExist()
    }
}
