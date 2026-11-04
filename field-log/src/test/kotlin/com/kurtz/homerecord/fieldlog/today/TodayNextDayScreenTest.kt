package com.kurtz.homerecord.fieldlog.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** AC-F41: crew see "Tomorrow" and the job on Today, and the line opens the Schedule tab. */
@AcceptanceCriterion("AC-F41")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TodayNextDayScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tomorrowLineOpensSchedule() {
        val events = mutableListOf<TodayEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                TodayScreen(
                    TodayUiState(
                        "Kurtz Electric", CompanyRole.Crew, hasJobs = true,
                        clock = TodayClock(com.kurtz.homerecord.core.punch.ClockStatus.Out, null, 0L),
                        next = TodayNextDay(LocalDate.of(2026, 11, 5), tomorrow = true, jobNames = listOf("Smith deck")),
                    ),
                    onEvent = { events += it },
                )
            }
        }
        rule.onNodeWithText("Tomorrow").assertExists()
        rule.onNodeWithText("Smith deck").assertExists()
        rule.onNodeWithTag("fl_today_next").performClick()
        assertThat(events).contains(TodayEvent.OpenSchedule)
    }
}
