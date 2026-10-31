package com.kurtz.homerecord.fieldlog.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Files has one create action (rule 13, judge:fl-files-empty-and-add): a FAB menu with Add file, Scan a document (AC-F22) and New folder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FilesAddMenuTest {
    @get:Rule val rule = createComposeRule()

    @AcceptanceCriterion("AC-U3", "AC-U4", "AC-F22", "AC-F23")
    @Test fun emptyStateAndFabOpenOneMenuWithAddFileScanAndNewFolder() {
        val events = mutableListOf<FilesEvent>()
        var state by mutableStateOf(FilesUiState(loading = false))
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                FilesScreen(state, {
                    events += it
                    if (it is FilesEvent.SetAddMenu) state = state.copy(addMenuOpen = it.open)
                })
            }
        }
        // rule 10: icon, headline, one sentence, one action; no search over an empty job
        rule.onNodeWithText("No files yet").assertIsDisplayed()
        rule.onNodeWithTag("fl_files_search").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_new_folder").assertIsNotDisplayed()
        rule.onNodeWithTag("fl_files_empty_add").performClick()
        assertThat(events).containsExactly(FilesEvent.SetAddMenu(true))
        rule.onNodeWithTag("fl_files_new_folder").performClick()
        rule.onNodeWithTag("fl_files_add").performClick()
        rule.onNodeWithTag("fl_files_scan").performClick()
        rule.onNodeWithTag("fl_files_fab").performClick()
        assertThat(events).containsExactly(
            FilesEvent.SetAddMenu(true),
            FilesEvent.NewFolder,
            FilesEvent.AddFile,
            FilesEvent.ScanDocument,
            FilesEvent.SetAddMenu(false),
        ).inOrder()
    }

    // AC-F3: someone who only views gets the empty state without an action or FAB
    @Test fun crewEmptyStateHasNoAction() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { FilesScreen(FilesUiState(loading = false, canManage = false), {}) }
        }
        rule.onNodeWithTag("fl_files_empty").assertIsDisplayed()
        rule.onNodeWithTag("fl_files_empty_add").assertDoesNotExist()
        rule.onNodeWithTag("fl_files_fab").assertDoesNotExist()
    }
}
