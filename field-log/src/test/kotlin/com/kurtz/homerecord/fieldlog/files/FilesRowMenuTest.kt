package com.kurtz.homerecord.fieldlog.files

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * judge:fl-files-row-actions: a file row has one trailing control (rule 7); offline, rename and delete sit in its
 * menu, which a long-press on the row opens too (rule 18), so no bin sits on every row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FilesRowMenuTest {
    @get:Rule val rule = createComposeRule()

    private val row = FileRow("f1", "Ortiz first floor plan.pdf", "", "application/pdf", 18_400_000, offline = true)

    private fun show(events: MutableList<FilesEvent>) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
            FilesScreen(FilesUiState(loading = false, files = listOf(row), canManage = true), { events += it })
        }
    }

    @AcceptanceCriterion("AC-F19", "AC-F23")
    @Test fun actionsLiveInTheRowMenu() {
        val events = mutableListOf<FilesEvent>()
        show(events)
        rule.onNodeWithTag("fl_files_rename_f1").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_delete_f1").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_more_f1").performClick()
        rule.onNodeWithTag("fl_files_offline_f1").assertIsDisplayed()
        rule.onNodeWithTag("fl_files_rename_f1").assertIsDisplayed()
        rule.onNodeWithTag("fl_files_delete_f1").assertIsDisplayed().performClick()
        rule.onNodeWithTag("fl_files_delete_f1").assertDoesNotExist()
        assertThat(events).containsExactly(FilesEvent.AskDelete("f1"))
    }

    @AcceptanceCriterion("AC-F23")
    @Test fun longPressOpensTheSameMenu() {
        val events = mutableListOf<FilesEvent>()
        show(events)
        rule.onNodeWithTag("fl_files_row_f1").performTouchInput { longClick() }
        rule.onNodeWithTag("fl_files_offline_f1").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(FilesEvent.ToggleOffline("f1", false))
    }
}
