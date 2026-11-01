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
        rule.onNodeWithTag("fl_log_changed_since_sent").assertDoesNotExist()
    }

    /** R09d: a log edited or re-assembled after it went out says the client's copy is out of date. */
    @AcceptanceCriterion("AC-F33")
    @Test fun aSentLogChangedLaterSaysSoOnTheSentLine() {
        show(ready.copy(sentAt = day.atTime(16, 10).toInstant(ZoneOffset.UTC).toEpochMilli(), changedSinceSent = true))
        rule.onNodeWithTag("fl_log_changed_since_sent", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Changed since sent. Send again so the client has this version.").assertExists()
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

    /** QA judge [fl-log-send-empty]: the warning says what the client would get and offers Cancel and Send anyway. */
    @AcceptanceCriterion("AC-F33")
    @Test fun theEmptyLogWarningOffersCancelAndSendAnyway() {
        val events = mutableListOf<LogEvent>()
        show(LogUiState(day = day, today = day, loading = false, canEdit = true, exists = true, editor = LogEditor.SendEmpty)) { events += it }
        rule.onNodeWithText("Send an empty log?").assertExists()
        rule.onNodeWithTag("fl_log_send_empty_cancel").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("fl_log_send_empty_confirm").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(events).containsExactly(LogEvent.DismissEditor, LogEvent.SendAnyway).inOrder()
    }

    @Test fun nothingLoggedCountsEverySectionAButTheWeather() {
        val bare = LogUiState(day = day, today = day, loading = false, canEdit = true, exists = true)
        assertThat(bare.nothingLogged).isTrue()
        assertThat(bare.copy(weather = com.kurtz.homerecord.core.model.LogWeather(highF = 58, lowF = 41, conditions = "rain")).nothingLogged).isTrue()
        assertThat(bare.copy(visitors = "  ").nothingLogged).isTrue()
        listOf(
            bare.copy(crew = listOf(LogCrewRow("m1", "Marcus Ortiz", 60, onSiteNow = false))),
            bare.copy(workDone = listOf("Set the base cabinets")),
            bare.copy(photos = listOf(LogPhotoThumb("p1", ""))),
            bare.copy(tasksDone = listOf(LogLine("t1", "Level the sink base"))),
            bare.copy(changeOrders = listOf(LogLine("co1", "Add under-cabinet lights"))),
            bare.copy(materials = listOf(LogLine("ma1", "Home Depot"))),
            bare.copy(delays = listOf(com.kurtz.homerecord.core.model.LogDelay(com.kurtz.homerecord.core.model.DelayReason.MATERIALS, ""))),
            bare.copy(visitors = "Township inspector"),
            bare.copy(safety = "Ladder tied off"),
            bare.copy(clientNotes = "Cabinets are in."),
        ).forEach { assertThat(it.nothingLogged).isFalse() }
    }
}
