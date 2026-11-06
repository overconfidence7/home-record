package com.kurtz.homerecord.housemodel.reminders

import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** One morning's summary notification (AC-C50): how many reminders are due and the first few, oldest due first. */
data class ReminderDigest(val count: Int, val titles: List<String>) {
    /** Due reminders the notification does not name. */
    val more: Int get() = count - titles.size
}

/** The pure rules of the daily reminder check (AC-C50). */
object DailyReminderCheck {
    /** Local time the check runs. */
    val AT: LocalTime = LocalTime.of(8, 0)

    /** How many reminders the notification names before "and N more". */
    const val NAMED = 3

    /**
     * Time from [now] to the next 08:00 in [now]'s zone: today's when it is still ahead, otherwise tomorrow's. Works in
     * zoned time so the day a clock change happens still lands on 08:00 local.
     */
    fun delayUntilNext(now: ZonedDateTime): Duration {
        val today = now.toLocalDate().atTime(AT).atZone(now.zone)
        val next = if (now.isBefore(today)) today else now.toLocalDate().plusDays(1).atTime(AT).atZone(now.zone)
        return Duration.between(now, next)
    }

    /** The summary of [due] (already filtered to due-now reminders), or null when nothing is due and nothing should show. */
    fun digest(due: List<MaintenanceTaskEntity>): ReminderDigest? {
        if (due.isEmpty()) return null
        val named = due.sortedWith(compareBy<MaintenanceTaskEntity> { maxOf(it.dueOn, it.snoozedUntil ?: "") }.thenBy { it.title.lowercase() })
            .take(NAMED)
            .map { it.title }
        return ReminderDigest(due.size, named)
    }

    /**
     * Whether to ask for the notification permission now: Android 13+, not granted, not asked on this install before,
     * and at least one reminder exists (AC-C50: never before there is something to be reminded of).
     */
    fun shouldAskPermission(sdkInt: Int, granted: Boolean, askedBefore: Boolean, reminderCount: Int): Boolean =
        sdkInt >= TIRAMISU && !granted && !askedBefore && reminderCount > 0

    private const val TIRAMISU = 33
}

/** Shows or clears the one reminder notification; the Android version posts it, tests use a fake. */
interface ReminderNotices {
    fun show(digest: ReminderDigest)
    fun clear()
}

/**
 * One run of the daily check: gathers the due reminders of every home on the phone into one digest and shows it, or
 * clears yesterday's notification when nothing is due any more. Returns what it showed.
 */
class ReminderCheck(
    private val homeIds: suspend () -> List<String>,
    private val repo: ReminderRepository,
    private val notices: ReminderNotices,
) {
    suspend fun run(): ReminderDigest? {
        val due = homeIds().flatMap { repo.dueNow(it) }
        val digest = DailyReminderCheck.digest(due)
        if (digest == null) notices.clear() else notices.show(digest)
        return digest
    }
}
