package com.kurtz.homerecord.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A change order reduced to what the job summary reads. */
data class SummaryChangeOrder(val status: ChangeOrderStatus, val totalCents: Long)

/**
 * A job from start to now (AC-F47): hours against the budget, change orders, tasks, days on site and delays.
 * [budgetMillis] is null when the job has no budget; [overBudgetMillis] is how far past it the hours went (0 when not).
 */
data class JobSummaryTotals(
    val workedMillis: Long,
    val budgetMillis: Long?,
    val signedCount: Int,
    val signedCents: Long,
    val draftCount: Int,
    val draftCents: Long,
    val declinedCount: Int,
    val tasksDone: Int,
    val tasksOpen: Int,
    val daysOnSite: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    /** Days whose log has at least one delay. */
    val delayDays: Int,
    /** How many delays of each reason, most first; ties in [DelayReason] order. */
    val delaysByReason: List<Pair<DelayReason, Int>>,
) {
    val overBudgetMillis: Long get() = budgetMillis?.let { (workedMillis - it).coerceAtLeast(0) } ?: 0
    val remainingMillis: Long? get() = budgetMillis?.let { (it - workedMillis).coerceAtLeast(0) }
}

/** Builds a [JobSummaryTotals] for one job from its punches, change orders, tasks and logged delays (AC-F47). */
object JobSummaryReport {
    fun build(
        jobId: String,
        budgetHours: Int?,
        punches: List<PunchPoint>,
        changeOrders: List<SummaryChangeOrder>,
        tasksDone: List<Boolean>,
        delays: List<DelayDay>,
        zone: ZoneId,
        now: Long,
    ): JobSummaryTotals {
        val worked = JobHours.workedMillis(punches, jobId, Long.MIN_VALUE, Long.MAX_VALUE, now)
        // a day on site is a day someone started work on this job: clocked in, switched to it or came back from a break
        val days = punches.filter { it.jobId == jobId && it.kind in STARTS }
            .map { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            .toSortedSet()
        val signed = changeOrders.filter { it.status == ChangeOrderStatus.SIGNED }
        val drafts = changeOrders.filter { it.status == ChangeOrderStatus.DRAFT }
        val mine = delays.filter { it.jobId == jobId && it.delays.isNotEmpty() }
        val counts = mine.flatMap { d -> d.delays.map { it.reason } }.groupingBy { it }.eachCount()
        return JobSummaryTotals(
            workedMillis = worked,
            budgetMillis = budgetHours?.takeIf { it > 0 }?.let { it * HOUR },
            signedCount = signed.size,
            signedCents = signed.sumOf { it.totalCents },
            draftCount = drafts.size,
            draftCents = drafts.sumOf { it.totalCents },
            declinedCount = changeOrders.count { it.status == ChangeOrderStatus.DECLINED },
            tasksDone = tasksDone.count { it },
            tasksOpen = tasksDone.count { !it },
            daysOnSite = days.size,
            firstDay = days.firstOrNull(),
            lastDay = days.lastOrNull(),
            delayDays = mine.map { it.day }.distinct().size,
            delaysByReason = DelayReason.entries.mapNotNull { r -> counts[r]?.let { r to it } }.sortedByDescending { it.second },
        )
    }

    private const val HOUR = 3_600_000L
    private val STARTS = setOf(PunchKind.IN, PunchKind.SWITCH, PunchKind.BREAK_END)
}
