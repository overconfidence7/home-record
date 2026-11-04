package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import androidx.compose.runtime.CompositionLocalProvider
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FL-ChangeOrder (AC-F37): the Changes list and the form's lines and total; Crew see the list only (AC-F3). */
@AcceptanceCriterion("AC-F37")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChangesScreenTest {
    @get:Rule val rule = createComposeRule()

    private val rows = listOf(
        ChangeRow("co2", "Pantry light", ChangeOrderStatus.DRAFT, 42_500, "Dana Hale", 2),
        ChangeRow("co1", "Island outlet", ChangeOrderStatus.SIGNED, 33_500, null, 1),
    )

    @Test fun listShowsStatusClientAndTotalAndOpensForManagers() {
        val events = mutableListOf<ChangesEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ChangesScreen(ChangesUiState(false, rows, canManage = true), { events += it }) } }
        rule.onNodeWithText("Draft · Dana Hale · 2 lines").assertExists()
        rule.onNodeWithText("$425.00").assertExists()
        rule.onNodeWithText("Signed · 1 line").assertExists()
        rule.onNodeWithTag("fl_changes_drafts_total").assertExists()
        rule.onNodeWithTag("fl_changes_row_co2").performClick()
        rule.onNodeWithTag("fl_changes_add").performClick()
        assertThat(events).containsExactly(ChangesEvent.Open("co2"), ChangesEvent.Add).inOrder()
    }

    @Test fun crewGetNoNewButton() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ChangesScreen(ChangesUiState(false, emptyList(), canManage = false), {}) } }
        rule.onNodeWithTag("fl_changes_add").assertDoesNotExist()
        rule.onNodeWithTag("fl_changes_empty_add").assertDoesNotExist()
        rule.onNodeWithText("No change orders").assertExists()
    }

    @Test fun formShowsLineAmountsTotalAndSpeak() {
        val draft = ChangeOrderFormDraft(
            title = "Pantry light", clientName = "Dana Hale",
            lines = listOf(FormLine("l1", "Fixture", "1", "185"), FormLine("l2", "Labor", "2.5", "96")),
        )
        val events = mutableListOf<ChangeOrderFormEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                ChangeOrderFormScreen(ChangeOrderFormUiState(isNew = false, loading = false, draft = draft, initial = draft, canDictate = true), { events += it })
            }
        }
        rule.onNodeWithTag("fl_co_form_line_1_amount").assertExists()
        rule.onNodeWithText("Line total $240.00").assertExists()
        rule.onNodeWithText("$425.00").performScrollTo()
        rule.onNodeWithTag("fl_co_form_speak").performScrollTo().performClick()
        rule.onNodeWithTag("fl_co_form_add_line").performScrollTo().performClick()
        rule.onNodeWithTag("fl_co_form_line_0_remove").performScrollTo().performClick()
        assertThat(events).containsExactly(ChangeOrderFormEvent.Speak, ChangeOrderFormEvent.AddLine, ChangeOrderFormEvent.RemoveLine("l1")).inOrder()
    }

    @Test fun aBadQuantityShowsItsErrorAndSaveStaysOff() {
        val draft = ChangeOrderFormDraft(title = "Pantry light", lines = listOf(FormLine("l1", "Fixture", "0", "185")))
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "fl") { ChangeOrderFormScreen(ChangeOrderFormUiState(loading = false, draft = draft, initial = draft), {}) }
            }
        }
        rule.onNodeWithText("Enter a number above 0.").assertExists()
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
    }

    // T056b: photos on the form, Remove, Add job photos, Take photo, and ticking one in the sheet.
    @Test fun photosShowWithRemoveAndTheSheetTicksAPhoto() {
        val events = mutableListOf<ChangeOrderFormEvent>()
        val photos = listOf(CoPhoto("p2", "", 2), CoPhoto("p1", "", 1))
        val draft = ChangeOrderFormDraft(title = "Pantry light", photoIds = listOf("p1"))
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                ChangeOrderFormScreen(ChangeOrderFormUiState(isNew = false, loading = false, draft = draft, initial = draft, jobPhotos = photos), { events += it })
                PhotoSheetContent(ChangeOrderFormUiState(loading = false, draft = draft, jobPhotos = photos), { events += it })
            }
        }
        rule.onNodeWithTag("fl_co_form_photo_p1").assertExists()
        rule.onNodeWithTag("fl_co_form_photo_p2").assertDoesNotExist()
        rule.onNodeWithTag("fl_co_form_photo_p1_remove").performScrollTo().performClick()
        rule.onNodeWithTag("fl_co_form_photos_add").performScrollTo().performClick()
        rule.onNodeWithTag("fl_co_form_photo_take").performScrollTo().performClick()
        rule.onNodeWithTag("fl_co_form_pick_p2").performClick()
        assertThat(events).containsExactly(
            ChangeOrderFormEvent.RemovePhoto("p1"), ChangeOrderFormEvent.OpenPhotos, ChangeOrderFormEvent.TakePhoto, ChangeOrderFormEvent.TogglePhoto("p2"),
        ).inOrder()
    }
}
