package com.kurtz.homerecord.fieldlog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.kurtz.homerecord.fieldlog.today.TodayEvent
import com.kurtz.homerecord.fieldlog.today.TodayScreen
import com.kurtz.homerecord.fieldlog.today.TodayUiState
import androidx.compose.ui.test.onNodeWithTag
import com.kurtz.homerecord.fieldlog.files.FileRow
import com.kurtz.homerecord.fieldlog.files.FilesScreen
import com.kurtz.homerecord.fieldlog.files.FilesUiState
import com.kurtz.homerecord.fieldlog.jobs.JobOverviewScreen
import com.kurtz.homerecord.fieldlog.jobs.JobOverviewUiState
import com.kurtz.homerecord.fieldlog.jobs.JobsScreen
import com.kurtz.homerecord.fieldlog.jobs.JobsUiState
import com.kurtz.homerecord.fieldlog.photos.PhotoDetailEvent
import com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen
import com.kurtz.homerecord.fieldlog.photos.PhotoDetailUiState
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
        val events = mutableListOf<TodayEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Owner), { events += it }) }
        }
        rule.onNodeWithText("No jobs yet at Kurtz Electric").assertIsDisplayed()
        rule.onNodeWithText("Invite crew").performClick()
        assertThat(events).containsExactly(TodayEvent.InviteCrew)
    }

    // AC-F1
    @Test fun todayEmptyStateForOwnerOffersAddJob() {
        val events = mutableListOf<TodayEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Owner), { events += it }) }
        }
        rule.onNodeWithText("Add a job").performClick()
        assertThat(events).containsExactly(TodayEvent.AddJob)
    }

    @Test fun todayEmptyStateForCrewHasNoInviteButton() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Crew), {}) }
        }
        rule.onNodeWithText("Invite crew").assertDoesNotExist()
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { content() } }

    // AC-F3 (cr:fl-role-gates): Crew and Lead get no New job and no Edit job
    @Test fun jobsHideNewJobWithoutTheRight() {
        show { JobsScreen(JobsUiState(loading = false, canAddJob = false), {}) }
        rule.onNodeWithTag("fl_jobs_new").assertDoesNotExist()
    }

    @Test fun jobsShowNewJobForOwner() {
        show { JobsScreen(JobsUiState(loading = false, canAddJob = true), {}) }
        rule.onNodeWithTag("fl_jobs_new").assertIsDisplayed()
    }

    @Test fun jobOverviewHidesEditWithoutTheRight() {
        show { JobOverviewScreen(JobOverviewUiState(loading = false, name = "Garage subpanel", canEdit = false), {}) }
        rule.onNodeWithTag("fl_job_overview").assertIsDisplayed()
        rule.onNodeWithTag("fl_job_edit").assertDoesNotExist()
    }

    // AC-F3: Crew view files and keep them offline; no add, folder, rename or delete
    @Test fun filesAreViewOnlyWithoutTheRight() {
        val row = FileRow("f1", "Panel schedule.pdf", "", "application/pdf", 640_000, offline = false)
        show { FilesScreen(FilesUiState(loading = false, files = listOf(row), canManage = false), {}) }
        rule.onNodeWithTag("fl_files_row_f1").assertIsDisplayed()
        rule.onNodeWithTag("fl_files_offline_f1").assertIsDisplayed()
        rule.onNodeWithTag("fl_files_fab").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_add").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_new_folder").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_rename_f1").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_delete_f1").assertDoesNotExist()
    }

    @Test fun photoHasNoDeleteWithoutTheRight() {
        show { PhotoDetailScreen(PhotoDetailUiState(loading = false, imagePath = "/none/a.jpg", canDelete = false), {}) }
        rule.onNodeWithTag("fl_photo_share").assertIsDisplayed()
        rule.onNodeWithTag("fl_photo_delete").assertDoesNotExist()
    }

    // AC-F25 (cr:fl-screen-signature): share, mark up and back reach the route as events
    @Test fun photoActionsAreEvents() {
        val events = mutableListOf<PhotoDetailEvent>()
        show { PhotoDetailScreen(PhotoDetailUiState(loading = false, imagePath = "/none/a.jpg", canDelete = false), { events += it }) }
        rule.onNodeWithTag("fl_photo_share").performClick()
        rule.onNodeWithTag("fl_photo_annotate").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        assertThat(events).containsExactly(PhotoDetailEvent.Share, PhotoDetailEvent.Annotate, PhotoDetailEvent.Back).inOrder()
    }
}
