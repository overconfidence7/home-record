package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.core.model.JobSummary
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FL-Jobs rows follow rule 7 ([judge:fl-jobs-rows-no-icon]): a job with no client or address still has a second line. */
@AcceptanceCriterion("AC-F6")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobsRowsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aJobWithNoClientOrAddressSaysSoUnderItsName() {
        val rows = listOf(
            JobRow(JobSummary("j1", "Hale kitchen", JobStatus.ACTIVE), "Dana Hale", "418 Linden Ave", "2026-11-06"),
            JobRow(JobSummary("j2", "Garage subpanel", JobStatus.ACTIVE), null, null, null),
        )
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                JobsScreen(JobsUiState(loading = false, groups = JobRowGroups(rows, emptyList(), emptyList(), emptyList()), today = "2026-11-05"), {})
            }
        }
        rule.onNodeWithText("Dana Hale, 418 Linden Ave").assertIsDisplayed()
        rule.onNodeWithText("No client or address yet").assertIsDisplayed()
    }
}
