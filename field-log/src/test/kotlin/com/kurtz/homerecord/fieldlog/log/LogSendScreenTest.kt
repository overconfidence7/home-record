package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC FL-Log Preview PDF and Send (AC-F33): the bar under the log, the Sent line and the Send sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LogSendScreenTest {
    @get:Rule val rule = createComposeRule()
    private val day = LocalDate.of(2026, 10, 30)
    private val ready = LogUiState(day = day, today = day, loading = false, canEdit = true, exists = true, workDone = listOf("Set the base cabinets"))

    private fun show(state: LogUiState, onEvent: (LogEvent) -> Unit = {}) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { LogScreen(state, onEvent, zone = ZoneOffset.UTC) }
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun theLeadPreviewsAndSendsFromTheBarUnderTheLog() {
        val events = mutableListOf<LogEvent>()
        show(ready) { events += it }
        rule.onNodeWithTag("fl_log_preview").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("fl_log_send").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Send").assertExists()
        rule.onNodeWithTag("fl_log_sent").assertDoesNotExist()
        assertThat(events).containsExactly(LogEvent.Preview, LogEvent.OpenSend).inOrder()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun aSentLogSaysWhenAndToWhomAndOffersSendAgain() {
        val sentAt = day.atTime(16, 10).toInstant(ZoneOffset.UTC).toEpochMilli()
        show(ready.copy(sentAt = sentAt, sentTo = listOf("jen.hale@example.com", "office@ortizbuild.com")))
        rule.onNodeWithTag("fl_log_sent").assertExists()
        rule.onNodeWithText("To jen.hale@example.com, office@ortizbuild.com").assertExists()
        rule.onNodeWithText("Send again").assertExists()
    }

    /**
     * QA F09b on c5543600: the Sent line arrived as a new first row while the list sat at its top, and the list kept
     * "Crew on site" as its first row, so the line was above the viewport and the log still looked unsent.
     */
    @AcceptanceCriterion("AC-F33")
    @Test fun theSentLineScrollsIntoViewWhenTheLogIsMarkedSentWhileOpen() {
        val state = mutableStateOf(ready)
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { LogScreen(state.value, {}, zone = ZoneOffset.UTC) }
        }
        rule.onNodeWithTag("fl_log_sent").assertDoesNotExist()
        state.value = ready.copy(sentAt = day.atTime(16, 10).toInstant(ZoneOffset.UTC).toEpochMilli())
        rule.waitForIdle()
        rule.onNodeWithTag("fl_log_sent").assertIsDisplayed()
        rule.onNodeWithText("Through the share sheet").assertIsDisplayed()
        rule.onNodeWithText("Send again").assertIsDisplayed()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun aSharedLogSaysItWentThroughTheShareSheetAndCrewOnlyPreview() {
        show(ready.copy(canEdit = false, sentAt = 1_793_400_000_000))
        rule.onNodeWithText("Through the share sheet").assertExists()
        rule.onNodeWithTag("fl_log_preview").assertExists()
        rule.onNodeWithTag("fl_log_send").assertDoesNotExist()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun theSheetListsWhoGetsItAndOffersEmailOrShare() {
        val events = mutableListOf<LogEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                LogSendSheetContent(LogEditor.Send(listOf("jen.hale@example.com", "office@ortizbuild.com")), sending = false, failed = false) { events += it }
            }
        }
        rule.onNodeWithText("jen.hale@example.com").assertExists()
        rule.onNodeWithTag("fl_log_send_to_1").assertExists()
        rule.onNodeWithTag("fl_log_send_email").performClick()
        rule.onNodeWithTag("fl_log_send_share").performClick()
        assertThat(events).containsExactly(LogEvent.SendEmail, LogEvent.SendShare).inOrder()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun withNoEmailsShareIsTheOneActionAndWhileMakingTheButtonsWait() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { LogSendSheetContent(LogEditor.Send(emptyList()), sending = true, failed = true) {} }
        }
        rule.onNodeWithTag("fl_log_send_email").assertDoesNotExist()
        rule.onNodeWithTag("fl_log_send_share").assertIsNotEnabled()
        rule.onNodeWithTag("fl_log_send_making").assertExists()
        rule.onNodeWithTag("fl_log_send_failed").assertExists()
    }
}
