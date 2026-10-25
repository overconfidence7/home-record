package com.kurtz.homerecord.core.punch

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.TimesheetApprovalEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Weeks run Monday to Sunday, keyed by the Monday's ISO date (AC-F14). */
object WeekKey {
    fun of(at: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(at).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString()

    fun parse(key: String): LocalDate = LocalDate.parse(key)
}

/**
 * Approve locks one person's week (AC-F14): the crew sees "Approved" and nobody can edit a punch in it.
 * Rows sync through the generic outbox ([onChange]).
 */
class TimesheetApprovals(
    private val db: HomeRecordDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    suspend fun approve(memberId: String, weekStart: String, approverId: String): Boolean {
        if (isApproved(memberId, weekStart)) return false
        val stamp = now()
        val id = Ids.newId()
        db.timesheetApprovalDao().upsert(TimesheetApprovalEntity(id, memberId, weekStart, approverId, stamp, stamp, stamp))
        onChange("timesheet_approval", id)
        return true
    }

    /** Approves every listed member for the week; returns how many were newly approved. */
    suspend fun approveAll(memberIds: Collection<String>, weekStart: String, approverId: String): Int =
        memberIds.count { approve(it, weekStart, approverId) }

    suspend fun isApproved(memberId: String, weekStart: String): Boolean =
        db.timesheetApprovalDao().listFor(memberId).any { it.weekStart == weekStart }

    /** True when the punch time falls in a week that is approved for the member. */
    suspend fun isLocked(memberId: String, at: Long): Boolean = isApproved(memberId, WeekKey.of(at, zone()))

    fun observe(memberId: String) = db.timesheetApprovalDao().observeFor(memberId)
}
