package com.kurtz.homerecord.housemanual.reminders

import com.kurtz.homerecord.core.logic.ClimateZone
import com.kurtz.homerecord.core.model.MaintenanceTask

/** One row on HM-Reminders; [overdue] is nextDue before today, [dueToday] is nextDue == today. */
data class ReminderItem(val task: MaintenanceTask, val overdue: Boolean, val dueToday: Boolean)

data class RemindersUiState(
    val loaded: Boolean = false,
    val items: List<ReminderItem> = emptyList(),
    val climateZone: ClimateZone? = null,
    val highlightId: String? = null,
)

sealed interface RemindersEvent {
    data class Done(val taskId: String) : RemindersEvent
    data class Snooze(val taskId: String) : RemindersEvent
    data class PickZone(val zone: ClimateZone) : RemindersEvent
}
