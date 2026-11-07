package com.kurtz.homerecord.housemanual.reminders

import com.kurtz.homerecord.housemodel.reminders.ClimateZone
import com.kurtz.homerecord.housemodel.reminders.ReminderBucket
import com.kurtz.homerecord.housemodel.reminders.ReminderDraft
import com.kurtz.homerecord.housemodel.reminders.ReminderRow
import com.kurtz.homerecord.housemodel.reminders.SnoozeLength
import java.time.LocalDate

/** HM-Reminders (AC-H12): Due now / Upcoming / Done, the climate zone chips and the Add reminder form. */
data class RemindersUiState(
    val loading: Boolean = true,
    /** The reminders could not be read; the tab shows what failed and Retry (AC-U4). */
    val loadFailed: Boolean = false,
    val rows: List<ReminderRow> = emptyList(),
    val zone: ClimateZone? = null,
    val today: LocalDate = LocalDate.now(),
    val form: ReminderDraft? = null,
    val showFormErrors: Boolean = false,
    /** The row whose Snooze menu is open. */
    val snoozeMenuFor: String? = null,
    /** A viewer on a shared home: the list reads only; no Add, climate zone, Done or Snooze (AC-H16). */
    val viewOnly: Boolean = false,
) {
    val due: List<ReminderRow> get() = rows.filter { it.bucket == ReminderBucket.DUE }
    val upcoming: List<ReminderRow> get() = rows.filter { it.bucket == ReminderBucket.UPCOMING }
    val done: List<ReminderRow> get() = rows.filter { it.bucket == ReminderBucket.DONE }
    val formDirty: Boolean get() = form != null && form != ReminderDraft()
}

sealed interface RemindersEvent {
    data class Done(val id: String) : RemindersEvent
    data class OpenSnooze(val id: String) : RemindersEvent
    data object CloseSnooze : RemindersEvent
    data class Snooze(val id: String, val length: SnoozeLength) : RemindersEvent
    data class PickZone(val zone: ClimateZone) : RemindersEvent
    /** Opens the reminder's item page. */
    data class OpenItem(val itemId: String) : RemindersEvent
    data object OpenForm : RemindersEvent
    data class ChangeForm(val draft: ReminderDraft) : RemindersEvent
    data object SaveForm : RemindersEvent
    data object CloseForm : RemindersEvent
    data object Retry : RemindersEvent
}
