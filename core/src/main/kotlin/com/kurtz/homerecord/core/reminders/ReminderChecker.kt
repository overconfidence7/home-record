package com.kurtz.homerecord.core.reminders

import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.MaintenanceRepository
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** Side effect behind an interface (AC-83): posts the "tasks due" notification. */
fun interface ReminderNotifier {
    fun notifyDue(tasks: List<MaintenanceTask>)
}

/** The daily WorkManager body: notifies when any task is due today or overdue. Returns how many were due. */
class ReminderChecker(
    private val homes: HomeRepository,
    private val tasks: MaintenanceRepository,
    private val notifier: ReminderNotifier,
) {
    suspend fun run(): Int {
        val home = homes.getHome() ?: return 0
        val due = tasks.getDue(home.id)
        if (due.isNotEmpty()) notifier.notifyDue(due)
        return due.size
    }
}

/** AC-83: the periodic check is aligned to the morning ([MORNING_HOUR]:00 local). */
object ReminderSchedule {
    const val MORNING_HOUR = 8

    fun initialDelay(nowMillis: Long, zone: ZoneId): Duration {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        var next = now.toLocalDate().atTime(MORNING_HOUR, 0).atZone(zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(MORNING_HOUR, 0).atZone(zone)
        return Duration.between(now, next)
    }
}
