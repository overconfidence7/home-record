package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.documents.DocumentsEvent
import com.kurtz.homerecord.housemanual.documents.DocumentsScreen
import com.kurtz.homerecord.housemanual.documents.DocumentsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Documents has one create action (rule 13, judge:hm-documents-three-adds): a FAB menu with Add a file and Scan a document. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DocumentsAddMenuTest {
    @get:Rule val rule = createComposeRule()

    @AcceptanceCriterion("AC-U3", "AC-C37")
    @Test fun emptyStateAndFabOpenOneMenuWithUploadAndScan() {
        val events = mutableListOf<DocumentsEvent>()
        var state by mutableStateOf(DocumentsUiState(loading = false))
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                DocumentsScreen(state, {
                    events += it
                    if (it is DocumentsEvent.SetAddMenu) state = state.copy(addMenuOpen = it.open)
                })
            }
        }
        // closed: no scan button on the page, only the FAB and the empty state's action
        rule.onNodeWithTag("hm_documents_scan").assertIsNotDisplayed()
        rule.onNodeWithTag("hm_documents_upload").assertIsNotDisplayed()
        rule.onNodeWithTag("hm_documents_add").performClick()
        assertThat(events).containsExactly(DocumentsEvent.SetAddMenu(true))
        rule.onNodeWithTag("hm_documents_scan").performClick()
        rule.onNodeWithTag("hm_documents_upload").performClick()
        rule.onNodeWithTag("hm_documents_fab").performClick()
        assertThat(events).containsExactly(
            DocumentsEvent.SetAddMenu(true),
            DocumentsEvent.Scan,
            DocumentsEvent.Upload,
            DocumentsEvent.SetAddMenu(false),
        ).inOrder()
        rule.onNodeWithTag("hm_documents_scan").assertIsNotDisplayed()
    }
}
