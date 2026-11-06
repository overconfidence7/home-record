package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.reminders.RemindersEvent
import com.kurtz.homerecord.housemanual.reminders.RemindersScreen
import com.kurtz.homerecord.housemanual.reminders.RemindersUiState
import com.kurtz.homerecord.housemodel.reminders.ClimateZone
import com.kurtz.homerecord.housemodel.reminders.ReminderBucket
import com.kurtz.homerecord.housemodel.reminders.ReminderRow
import com.kurtz.homerecord.housemodel.reminders.SnoozeLength
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H12: HM-Reminders shows Due now / Upcoming / Done with Done and Snooze per row and the climate zone chips.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-H12")
class RemindersScreenTest {
    @get:Rule val rule = createComposeRule()
    private val events = mutableListOf<RemindersEvent>()
    private val today = LocalDate.parse("2026-11-06")

    private fun row(id: String, title: String, bucket: ReminderBucket, due: String, item: String? = null) = ReminderRow(
        id = id, title = title, itemId = item?.let { "i-$id" }, itemName = item, bucket = bucket, dueOn = LocalDate.parse(due),
        intervalMonths = 3, lastDoneOn = if (bucket == ReminderBucket.DONE) today else null, snoozedUntil = null, seasonal = false,
    )

    private fun show(state: RemindersUiState) = rule.setContent {
        HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { RemindersScreen(state, onEvent = { events += it }) }
    }

    @Test fun sectionsRowsAndActions() {
        show(
            RemindersUiState(
                loading = false, today = today, zone = ClimateZone.COLD,
                rows = listOf(
                    row("a", "Replace furnace filter", ReminderBucket.DUE, "2026-10-28", "Basement furnace"),
                    row("b", "Flush water heater", ReminderBucket.UPCOMING, "2027-03-01"),
                    row("c", "Clean dryer vent", ReminderBucket.DONE, "2027-02-06"),
                ),
                snoozeMenuFor = "a",
            ),
        )
        rule.onNodeWithText("Due now (1)").assertIsDisplayed()
        rule.onNodeWithText("Upcoming (1)").assertIsDisplayed()
        rule.onNodeWithText("Done (1)").assertIsDisplayed()
        rule.onNodeWithTag("hm_reminders_done_due_0").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("hm_reminders_snooze_month").performClick()
        rule.onNodeWithTag("hm_reminders_row_due_0").performClick()
        rule.onNodeWithTag("hm_reminders_zone_marine").performClick()
        assertThat(events).containsAtLeast(
            RemindersEvent.Done("a"), RemindersEvent.Snooze("a", SnoozeLength.MONTH), RemindersEvent.OpenItem("i-a"), RemindersEvent.PickZone(ClimateZone.MARINE),
        ).inOrder()
        rule.onNodeWithTag("hm_reminders_done_done_0").assertDoesNotExist() // a done row has no actions
    }

    @Test fun emptyAsksForAZoneAndOffersAdd() {
        show(RemindersUiState(loading = false, today = today))
        rule.onNodeWithTag("hm_reminders_empty").assertIsDisplayed()
        rule.onNodeWithText("Climate zone").assertIsDisplayed()
        rule.onNodeWithTag("hm_reminders_fab").performClick()
        assertThat(events).contains(RemindersEvent.OpenForm)
    }
}
