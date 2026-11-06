package com.kurtz.homerecord.housemanual.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.reminders.ReminderBucket
import com.kurtz.homerecord.housemodel.reminders.ReminderRow
import com.kurtz.homerecord.housemodel.reminders.SnoozeLength
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// AC-C49 / SPEC HM-Item: the item page lists the item's reminders with Done and Snooze.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemDetailRemindersTest {
    @get:Rule val rule = createComposeRule()

    private val row = ReminderRow(
        id = "r1", title = "Replace furnace filter", itemId = "f", itemName = "Furnace", bucket = ReminderBucket.DUE,
        dueOn = LocalDate.of(2026, 10, 28), intervalMonths = 3, lastDoneOn = null, snoozedUntil = null, seasonal = false,
    )

    private fun state(rows: List<ReminderRow>, snooze: String? = null) = ItemDetailUiState(
        loading = false,
        detail = ItemDetail(
            ItemEntity(id = "f", homeId = "h1", category = ItemCategory.HEATING_COOLING.name, subtype = "furnace", name = "Furnace", createdAt = 1, updatedAt = 1),
            ItemCategory.HEATING_COOLING, "Furnace", "Utility room", null, WarrantyStatus.Unknown, emptyList(),
        ),
        reminders = rows, reminderSnoozeFor = snooze, today = LocalDate.of(2026, 11, 6),
    )

    @Test
    @AcceptanceCriterion("AC-C49")
    fun remindersShowWithDoneAndSnooze() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ItemDetailScreen(state(listOf(row), snooze = "r1"), onEvent = { events += it }) }
        rule.onNodeWithText("Replace furnace filter").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("hm_item_detail_reminder_done_0").performScrollTo().performClick()
        rule.onNodeWithTag("hm_item_detail_reminder_snooze_week").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.ReminderDone("r1"), ItemDetailEvent.ReminderSnooze("r1", SnoozeLength.WEEK)).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C49")
    fun withNoRemindersTheSectionSaysSoAndOffersAddReminder() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ItemDetailScreen(state(emptyList()), onEvent = { events += it }) }
        rule.onNodeWithText("Nothing to do for this item yet.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("hm_item_detail_reminder_add").performScrollTo().performClick()
        assertThat(events).containsExactly(ItemDetailEvent.OpenReminderForm)
    }
}
