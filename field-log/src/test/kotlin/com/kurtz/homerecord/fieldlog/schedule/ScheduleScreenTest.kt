package com.kurtz.homerecord.fieldlog.schedule

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ScheduleEntry
import com.kurtz.homerecord.core.model.ScheduleWeek
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** FL-Schedule (AC-F41, AC-F43): crew rows for the day, Add opens the picker, a chip opens move/remove, conflicts badged. */
@AcceptanceCriterion("AC-F41", "AC-F43")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ScheduleScreenTest {
    @get:Rule val rule = createComposeRule()
    private val wed = LocalDate.of(2026, 11, 4)
    private val monday = ScheduleWeek.weekStart(wed)
    private val names = mapOf("j1" to "Smith deck", "j2" to "Hale kitchen")
    private val rows = ScheduleWeek.build(
        monday, listOf("m1" to "Dana Ortiz", "m2" to "Luis Romero"),
        listOf(ScheduleEntry("a1", "j1", "m2", wed), ScheduleEntry("a2", "j2", "m2", wed)), names,
    )

    private fun show(state: ScheduleUiState, events: MutableList<ScheduleEvent> = mutableListOf()) =
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.kurtz.homerecord.coreui.shell.LocalTagPrefix provides "fl") {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ScheduleScreen(state, { events += it }) }
            }
        }

    @Test fun teamDayShowsChipsConflictAndAssign() {
        val events = mutableListOf<ScheduleEvent>()
        show(ScheduleUiState(false, wed, monday, wed, rows, jobs = listOf(ScheduleJobOption("j1", "Smith deck"), ScheduleJobOption("j2", "Hale kitchen"))), events)
        rule.onNodeWithText("Week of Nov 2").assertExists()
        rule.onNodeWithTag("fl_schedule_conflict_m2", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("fl_schedule_conflict_m1", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Not scheduled").assertExists()
        rule.onNodeWithTag("fl_schedule_assign_m1").performClick()
        rule.onNodeWithTag("fl_schedule_chip_m2_j1").performClick()
        rule.onNodeWithTag("fl_schedule_day_2026-11-05").performClick()
        rule.onNodeWithTag("fl_schedule_next").performClick()
        assertThat(events).containsExactly(
            ScheduleEvent.Assign("m1"), ScheduleEvent.OpenDay("m2", "a1"), ScheduleEvent.SelectDay(wed.plusDays(1)), ScheduleEvent.NextWeek,
        ).inOrder()
    }

    @Test fun crewWeekHasNoAssignButtons() {
        val own = ScheduleWeek.build(monday, listOf("m2" to "Luis Romero"), listOf(ScheduleEntry("a1", "j1", "m2", wed)), names)
        show(ScheduleUiState(false, wed, monday, wed, own, ownOnly = true))
        rule.onNodeWithTag("fl_schedule_own_2026-11-04").assertExists()
        rule.onNodeWithText("Smith deck").assertExists()
        rule.onNodeWithTag("fl_schedule_assign_m2").assertDoesNotExist()
    }

    @Test fun crewWithNothingThisWeekGetTheEmptyState() {
        val own = ScheduleWeek.build(monday, listOf("m2" to "Luis Romero"), emptyList(), names)
        show(ScheduleUiState(false, wed, monday, wed, own, ownOnly = true))
        rule.onNodeWithTag("fl_schedule_empty").assertExists()
        rule.onNodeWithText("Nothing scheduled this week").assertExists()
    }

    @Test fun pickerSheetListsTheJobs() {
        val events = mutableListOf<ScheduleEvent>()
        show(
            ScheduleUiState(false, wed, monday, wed, rows, jobs = listOf(ScheduleJobOption("j1", "Smith deck")), sheet = ScheduleSheet.PickJob("m1", "Dana Ortiz", wed)),
            events,
        )
        rule.onNodeWithText("Which job for Dana Ortiz on Wed, Nov 4?").assertExists()
        rule.onNodeWithTag("fl_sheet_job_j1", useUnmergedTree = true).performClick()
        assertThat(events).contains(ScheduleEvent.PickJob("j1"))
    }
}
