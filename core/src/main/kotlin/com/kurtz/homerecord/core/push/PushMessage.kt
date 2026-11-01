package com.kurtz.homerecord.core.push

import java.time.LocalDate

/** Android notification channels; the ids are stable, the names come from strings.xml. */
enum class PushChannel(val id: String) {
    Tasks("tasks"),
    Schedule("schedule"),
    ChangeOrders("change_orders"),
    Timesheets("timesheets"),
}

/**
 * A data-only push from the Worker (backend/src/push/events.ts, AC-B30), read from the FCM data map. It carries ids and
 * the names to show; the phone writes the words. Null from [parse] for a type this version does not know or a message
 * without the ids it needs, which is then dropped.
 */
sealed interface PushMessage {
    val companyId: String

    data class TaskAssigned(override val companyId: String, val jobId: String, val jobName: String, val taskId: String, val title: String) : PushMessage

    data class ScheduleChanged(override val companyId: String, val date: LocalDate, val jobId: String, val jobName: String) : PushMessage

    data class ChangeOrderSigned(
        override val companyId: String,
        val jobId: String,
        val jobName: String,
        val changeOrderId: String,
        val title: String,
        val signedBy: String,
    ) : PushMessage

    data class TimesheetApproved(override val companyId: String, val weekStart: LocalDate) : PushMessage

    val channel: PushChannel
        get() = when (this) {
            is TaskAssigned -> PushChannel.Tasks
            is ScheduleChanged -> PushChannel.Schedule
            is ChangeOrderSigned -> PushChannel.ChangeOrders
            is TimesheetApproved -> PushChannel.Timesheets
        }

    /** One notification per thing: a second push about the same task, day, change order or week replaces the first. */
    val key: String
        get() = when (this) {
            is TaskAssigned -> "task:$taskId"
            is ScheduleChanged -> "schedule:$date"
            is ChangeOrderSigned -> "change_order:$changeOrderId"
            is TimesheetApproved -> "timesheet:$weekStart"
        }

    companion object {
        fun parse(data: Map<String, String>): PushMessage? {
            fun text(name: String) = data[name]?.trim().orEmpty()
            fun id(name: String) = text(name).ifEmpty { null }
            fun day(name: String) = id(name)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val company = id("companyId") ?: return null
            return when (data["type"]) {
                "task_assigned" -> TaskAssigned(company, text("jobId"), text("jobName"), id("taskId") ?: return null, text("title"))
                "schedule_changed" -> ScheduleChanged(company, day("date") ?: return null, text("jobId"), text("jobName"))
                "change_order_signed" -> ChangeOrderSigned(
                    company, text("jobId"), text("jobName"), id("changeOrderId") ?: return null, text("title"), text("signedBy"),
                )
                "timesheet_approved" -> TimesheetApproved(company, day("weekStart") ?: return null)
                else -> null
            }
        }
    }
}

/** How a schedule notification names its day. */
enum class PushDay { Today, Tomorrow, Other }

fun pushDay(date: LocalDate, today: LocalDate): PushDay = when (date) {
    today -> PushDay.Today
    today.plusDays(1) -> PushDay.Tomorrow
    else -> PushDay.Other
}
