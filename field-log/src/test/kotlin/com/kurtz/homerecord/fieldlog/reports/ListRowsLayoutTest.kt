package com.kurtz.homerecord.fieldlog.reports

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.model.HoursLine
import com.kurtz.homerecord.core.model.OnSiteEntry
import com.kurtz.homerecord.core.punch.ClockStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.timesheets.TimesheetsScreen
import com.kurtz.homerecord.fieldlog.timesheets.TimesheetsUiState
import com.kurtz.homerecord.fieldlog.today.TodayClock
import com.kurtz.homerecord.fieldlog.today.TodayJob
import com.kurtz.homerecord.fieldlog.today.TodayScreen
import com.kurtz.homerecord.fieldlog.today.TodayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Today, Timesheets and Reports draw people and jobs as list rows with rule-10 empty states (cr:fl-card-lists). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ListRowsLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val hour = 3_600_000L

    @AcceptanceCriterion("AC-U3", "AC-F15")
    @Test fun reportsRowsShowNameAndHoursAndExportsAreEnabled() {
        val state = ReportsUiState(
            loading = false,
            byPerson = listOf(HoursLine("m1", "Luis Ortiz", 42 * hour, 40 * hour, 2 * hour)),
            byJob = listOf(HoursLine("j1", "Kitchen remodel", 42 * hour)),
            totalMillis = 42 * hour, regularMillis = 40 * hour, overtimeMillis = 2 * hour,
        )
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ReportsScreen(state, {}, {}) } }
        rule.onNodeWithTag("fl_reports_person_m1").assertIsDisplayed()
        rule.onNodeWithText("Luis Ortiz").assertIsDisplayed()
        rule.onNodeWithTag("fl_reports_list").performScrollToNode(hasText("Kitchen remodel"))
        rule.onNodeWithTag("fl_reports_job_j1").assertIsDisplayed()
        rule.onNodeWithTag("fl_reports_list").performScrollToNode(hasText("Export shifts (CSV)"))
        rule.onNodeWithTag("fl_reports_export_shifts").assertIsEnabled()
    }

    @AcceptanceCriterion("AC-U4", "AC-F15")
    @Test fun emptyReportsRangeHasHeadlineAndDisablesExports() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ReportsScreen(ReportsUiState(loading = false), {}, {}) } }
        rule.onNodeWithText("No hours in these dates").assertIsDisplayed()
        rule.onNodeWithTag("fl_reports_list").performScrollToNode(hasText("Export shifts (CSV)"))
        rule.onNodeWithTag("fl_reports_export_shifts").assertIsNotEnabled()
        rule.onNodeWithTag("fl_reports_export_quickbooks").assertIsNotEnabled()
    }

    @AcceptanceCriterion("AC-U4", "AC-F14")
    @Test fun emptyTimesheetWeekHasHeadline() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TimesheetsScreen(TimesheetsUiState(loading = false), {}, {}) } }
        rule.onNodeWithTag("fl_timesheets_empty").assertIsDisplayed()
        rule.onNodeWithText("No punches this week").assertIsDisplayed()
    }

    @AcceptanceCriterion("AC-U3", "AC-F16")
    @Test fun todayJobsAndOnSiteAreRowsAndAJobRowOpensTheJob() {
        val opened = mutableListOf<String>()
        val state = TodayUiState(
            companyName = "QA Electric", role = CompanyRole.Owner, hasJobs = true,
            clock = TodayClock(ClockStatus.Out, null, now = 1_800_000_000_000L),
            jobs = listOf(TodayJob("j1", "Kitchen remodel", "14 Maple St")),
            onSite = listOf(OnSiteEntry("m1", "Luis Ortiz", "j1", "Kitchen remodel", 1_799_990_000_000L, false, 41.1, -81.5)),
        )
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(state, {}, onOpenJob = { opened += it }) } }
        rule.onNodeWithTag("fl_today_job_j1").assert(hasClickAction()).performClick()
        assertThat(opened).containsExactly("j1")
        rule.onNodeWithTag("fl_today_list").performScrollToNode(hasText("Luis Ortiz"))
        rule.onNodeWithTag("fl_today_site_m1").assertIsDisplayed()
        rule.onNodeWithTag("fl_today_pin_m1", useUnmergedTree = true).assertIsDisplayed()
    }
}
