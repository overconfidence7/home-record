package com.kurtz.homerecord.fieldlog.company

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.TeamMember
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F2, AC-F3, CLAUDE.md QA rule 3: F02 opens the crew member's row, picks Make Lead and Remove from company by id.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TeamTagsTest {
    @get:Rule val rule = createComposeRule()

    private val owner = TeamMember("u-owner", "Dana Kurtz", "dana@example.test", CompanyRole.Owner, null)
    private val crew = TeamMember("u-crew", null, "marco@example.test", CompanyRole.Crew, 3200)

    @Test fun memberRowsHaveIdsAndOpenTheirActions() {
        val events = mutableListOf<TeamEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "fl") { TeamScreen(TeamUiState(status = TeamStatus.Ready, members = listOf(owner, crew), inviteCode = "K7M2QX9R"), { events += it }, { _, _ -> }, {}) }
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("fl_team_row_u-owner"))
        rule.onNodeWithTag("fl_team_row_u-owner").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("fl_team_row_u-crew"))
        rule.onNodeWithTag("fl_team_row_u-crew").performClick()
        assertThat(events).contains(TeamEvent.OpenActions(crew))
    }

    @Test fun actionSheetOptionsHaveIds() {
        val events = mutableListOf<TeamEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "fl") { TeamScreen(TeamUiState(status = TeamStatus.Ready, members = listOf(owner, crew), actionsFor = crew), { events += it }, { _, _ -> }, {}) }
            }
        }
        rule.onNodeWithTag("fl_sheet_role:lead").performClick()
        rule.onNodeWithTag("fl_sheet_remove").assertExists()
        assertThat(events).contains(TeamEvent.SetRole(crew, CompanyRole.Lead))
    }
}
