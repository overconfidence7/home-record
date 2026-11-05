package com.kurtz.homerecord.fieldlog.handoff

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.HandoffSummary
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-F51: the handoff summary lists rooms, walls with photos, overlays, items, documents and change orders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
@AcceptanceCriterion("AC-F51")
class HandoffScreenTest {
    @get:Rule val rule = createComposeRule()

    private val hale = HandoffSummary(rooms = 4, wallsWithPhotos = 3, wallPhotos = 7, overlays = 5, items = 2, documents = 1, changeOrders = 2)

    private fun show(state: HandoffUiState): MutableList<HandoffEvent> {
        val events = mutableListOf<HandoffEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { HandoffScreen(state, { events += it }) } }
        return events
    }

    @Test fun summaryListsEveryKindWithItsCountAndExportSendsTheEvent() { // AC-F51
        val events = show(HandoffUiState(loading = false, jobName = "Hale kitchen", summary = hale))
        listOf("rooms", "walls", "overlays", "items", "documents", "change_orders").forEach {
            rule.onNodeWithTag("fl_handoff_count_$it").performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithText("7 photos").assertIsDisplayed()
        rule.onNodeWithTag("fl_handoff_export").performScrollTo().assertIsEnabled().performClick()
        assertThat(events).contains(HandoffEvent.Export)
    }

    @Test fun anEmptyHouseSaysSoAndExportWaits() { // AC-F51
        show(HandoffUiState(loading = false, jobName = "Garage subpanel", summary = HandoffSummary(0, 0, 0, 0, 0, 0, 0)))
        rule.onNodeWithTag("fl_handoff_empty").assertIsDisplayed()
        rule.onNodeWithTag("fl_handoff_export").performScrollTo().assertIsNotEnabled()
    }

    @Test fun makingAndFailedAreShownUnderExport() { // AC-F51
        show(HandoffUiState(loading = false, jobName = "Hale kitchen", summary = hale, failed = true))
        rule.onNodeWithTag("fl_handoff_failed").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("fl_handoff_export").assertIsEnabled()
    }
}
