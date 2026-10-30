package com.kurtz.homerecord.fieldlog.timesheets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// AC-F14, ui-standards rules 3, 15 and 22 (judge:fl-edit-punch-dialog): Edit punch is a full-screen form with
// Close and Save, not a dialog; the time comes from the time picker; Close asks before dropping a change.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PunchEditScreenTest {
    @get:Rule val rule = createComposeRule()

    private val sheet = PunchEditSheet(
        "p3", "Marcus Ortiz", "IN", "Tue Oct 20", LocalTime.of(7, 30), "j2",
        listOf(JobChoice("j1", "Garage subpanel"), JobChoice("j2", "Ortiz kitchen remodel")),
    )

    private fun screen(edit: PunchEditSheet, onEvent: (TimesheetsEvent) -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                TimesheetsScreen(
                    TimesheetsUiState(loading = false, weekStart = LocalDate.of(2026, 10, 19), edit = edit),
                    onEvent, onBack = {}, zone = ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test fun editIsAFullScreenFormWithCloseAndSave() {
        val events = mutableListOf<TimesheetsEvent>()
        screen(sheet) { events += it }
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onNodeWithTag("fl_timesheets_edit").assertExists()
        rule.onNodeWithTag("fl_timesheets_list").assertDoesNotExist()
        rule.onNodeWithText("Edit punch").assertExists()
        rule.onNodeWithTag("fl_save").performClick()
        rule.onNodeWithTag("fl_close").performClick()
        assertThat(events).containsExactly(TimesheetsEvent.SaveEdit, TimesheetsEvent.CancelEdit).inOrder()
    }

    @Test fun timeComesFromTheTimePicker() {
        val events = mutableListOf<TimesheetsEvent>()
        screen(sheet) { events += it }
        rule.onNodeWithText(LocalTime.of(7, 30).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))).assertExists()
        rule.onNodeWithTag("fl_pick_time").performClick()
        rule.onNode(isDialog()).assertExists()
        rule.onNodeWithTag("fl_pick_time_ok").performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
        assertThat(events).containsExactly(TimesheetsEvent.EditTime(LocalTime.of(7, 30)))
    }

    @Test fun closeAfterAChangeAsksFirst() {
        val events = mutableListOf<TimesheetsEvent>()
        screen(sheet.copy(reason = "Forgot to clock in")) { events += it }
        rule.onNodeWithTag("fl_close").performClick()
        rule.onNodeWithText("Discard changes?").assertExists()
        rule.onNodeWithTag("fl_keep_editing").performClick()
        assertThat(events).isEmpty()
        rule.onNodeWithTag("fl_close").performClick()
        rule.onNodeWithTag("fl_discard").performClick()
        assertThat(events).containsExactly(TimesheetsEvent.CancelEdit)
    }

    @Test fun missingReasonShowsItsError() {
        screen(sheet.copy(error = EditError.ReasonRequired)) {}
        rule.onNodeWithText("Add a reason. It stays on the punch.").assertExists()
    }
}
