package com.kurtz.homerecord.fieldlog.reports

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.DelayEntry
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.HoursLine
import com.kurtz.homerecord.core.model.JobDelays
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FL-Reports: the delays of the range as a job timeline; a delay opens that job's log on its day (AC-F32). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReportsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val monday = LocalDate.of(2026, 10, 26)

    private val state = ReportsUiState(
        loading = false, from = monday, to = monday.plusDays(6),
        byPerson = listOf(HoursLine("m1", "Dana Whitfield", 8 * 3_600_000L)),
        byJob = listOf(HoursLine("j1", "Hale kitchen", 8 * 3_600_000L)),
        totalMillis = 8 * 3_600_000L, regularMillis = 8 * 3_600_000L,
        delays = listOf(
            JobDelays(
                "j1", "Hale kitchen", 2,
                listOf(
                    DelayEntry(monday.plusDays(1), 0, DelayReason.WEATHER, "Rain held up the siding"),
                    DelayEntry(monday.plusDays(3), 0, DelayReason.INSPECTION, ""),
                ),
            ),
        ),
    )

    private fun show(s: ReportsUiState, onOpenLog: (String, LocalDate) -> Unit = { _, _ -> }) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ReportsScreen(s, {}, {}, onOpenLog) }
    }

    @AcceptanceCriterion("AC-F32")
    @Test fun delaysShowPerJobAndOpenTheDaysLog() {
        val opened = mutableListOf<Pair<String, LocalDate>>()
        show(state) { job, day -> opened += job to day }
        rule.onNodeWithTag("fl_reports_list").performScrollToNode(hasTestTag("fl_reports_delays_j1"))
        rule.onNodeWithText("Hale kitchen · 2 days delayed").assertExists()
        rule.onNodeWithTag("fl_reports_list").performScrollToNode(hasTestTag("fl_reports_delay_j1_${monday.plusDays(1)}_0"))
        rule.onNodeWithText("Tue, Oct 27 · Rain held up the siding").assertExists()
        rule.onNodeWithTag("fl_reports_delay_j1_${monday.plusDays(1)}_0").performClick()
        assertThat(opened).containsExactly("j1" to monday.plusDays(1))
    }

    @AcceptanceCriterion("AC-F32")
    @Test fun noDelaysSaysSo() {
        show(state.copy(delays = emptyList()))
        rule.onNodeWithTag("fl_reports_list").performScrollToNode(hasTestTag("fl_reports_delays_none"))
        rule.onNodeWithText("No delays logged in these dates.").assertExists()
    }
}
