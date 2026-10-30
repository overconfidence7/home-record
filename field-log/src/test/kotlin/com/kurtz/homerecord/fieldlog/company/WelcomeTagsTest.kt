package com.kurtz.homerecord.fieldlog.company

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F1, AC-F2, CLAUDE.md QA rule 3 (cr:qa-text-selectors): _owner-company.yaml and F02 pick Create or Join by id.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WelcomeTagsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun createAndJoinSegmentsHaveIds() {
        val events = mutableListOf<WelcomeEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { WelcomeScreen(WelcomeUiState(), { events += it }) }
        }
        rule.onNodeWithTag("fl_welcome_mode_create").assertIsDisplayed()
        rule.onNodeWithTag("fl_welcome_mode_join").performClick()
        rule.onNodeWithTag("fl_welcome_submit").assertExists()
        assertThat(events).contains(WelcomeEvent.ModeChanged(WelcomeMode.Join))
    }
}
