package com.kurtz.homerecord.fieldlog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.more.MoreEvent
import com.kurtz.homerecord.fieldlog.more.MoreScreen
import com.kurtz.homerecord.fieldlog.more.MoreUiState
import com.kurtz.homerecord.fieldlog.today.TodayScreen
import com.kurtz.homerecord.fieldlog.today.TodayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-F3: admin items are absent for Crew and Lead, not greyed out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoleGatesTest {
    @get:Rule val rule = createComposeRule()

    private fun more(role: CompanyRole, onEvent: (MoreEvent) -> Unit = {}) =
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { MoreScreen(MoreUiState("Kurtz Electric", role), onEvent) } }

    @Test fun ownerSeesTeamAndCompany() {
        val events = mutableListOf<MoreEvent>()
        more(CompanyRole.Owner) { events += it }
        rule.onNodeWithText("Team").assertIsDisplayed().performClick()
        rule.onNodeWithText("Company").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(MoreEvent.OpenTeam, MoreEvent.OpenCompany).inOrder()
    }

    @Test fun officeSeesTeamAndCompany() {
        more(CompanyRole.Office)
        rule.onNodeWithText("Team").assertIsDisplayed()
        rule.onNodeWithText("Company").assertIsDisplayed()
    }

    @Test fun crewDoesNotSeeTeamOrCompany() {
        more(CompanyRole.Crew)
        rule.onNodeWithText("Team").assertDoesNotExist()
        rule.onNodeWithText("Company").assertDoesNotExist()
        rule.onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test fun leadDoesNotSeeTeamOrCompany() {
        more(CompanyRole.Lead)
        rule.onNodeWithText("Team").assertDoesNotExist()
        rule.onNodeWithText("Company").assertDoesNotExist()
    }

    @Test fun signOutAsksFirst() {
        val events = mutableListOf<MoreEvent>()
        more(CompanyRole.Crew) { events += it }
        rule.onNodeWithText("Sign out").performClick()
        assertThat(events).isEmpty()
        rule.onNodeWithText("Sign out?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        assertThat(events).isEmpty()
        rule.onNodeWithText("Sign out").performClick()
        rule.onAllNodesWithText("Sign out")[1].performClick()
        assertThat(events).containsExactly(MoreEvent.SignOut)
    }

    @Test fun todayEmptyStateForOwnerOffersInvite() {
        var invited = false
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Owner), { invited = true }) }
        }
        rule.onNodeWithText("No jobs yet at Kurtz Electric").assertIsDisplayed()
        rule.onNodeWithText("Invite crew").performClick()
        assertThat(invited).isTrue()
    }

    // AC-F1
    @Test fun todayEmptyStateForOwnerOffersAddJob() {
        var added = false
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Owner), {}, onAddJob = { added = true }) }
        }
        rule.onNodeWithText("Add a job").performClick()
        assertThat(added).isTrue()
    }

    @Test fun todayEmptyStateForCrewHasNoInviteButton() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Crew), {}) }
        }
        rule.onNodeWithText("Invite crew").assertDoesNotExist()
    }
}
