package com.kurtz.homerecord.fieldlog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.company.CompanyScreen
import com.kurtz.homerecord.fieldlog.company.CompanyUiState
import com.kurtz.homerecord.fieldlog.company.TeamStatus
import com.kurtz.homerecord.fieldlog.jobs.JobEditScreen
import com.kurtz.homerecord.fieldlog.jobs.JobEditUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Job edit and Company keep Close and a disabled Save while loading or failed, never a bare page with no way out (rule 3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FormLoadingTest {
    @get:Rule val rule = createComposeRule()

    private var closed = 0

    @AcceptanceCriterion("AC-U6")
    @Test fun jobEditLoadingHasCloseAndSave() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { JobEditScreen(JobEditUiState(loading = true, isNew = false), {}, { closed++ }) } }
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
        rule.onNodeWithTag("fl_close").performClick()
        assertThat(closed).isEqualTo(1)
    }

    @AcceptanceCriterion("AC-U6")
    @Test fun companyLoadingHasClose() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { CompanyScreen(CompanyUiState(status = TeamStatus.Loading), {}, { closed++ }) } }
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
        rule.onNodeWithTag("fl_close").performClick()
        assertThat(closed).isEqualTo(1)
    }

    @AcceptanceCriterion("AC-U6")
    @Test fun companyFailedHasClose() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompanyScreen(CompanyUiState(status = TeamStatus.Failed, failure = ApiFailure.Offline), {}, { closed++ })
            }
        }
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
        rule.onNodeWithTag("fl_close").assertIsDisplayed().performClick()
        assertThat(closed).isEqualTo(1)
    }
}
