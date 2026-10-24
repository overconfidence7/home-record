package com.kurtz.homerecord.core.model

/** A punch with everything the shift rules read. */
data class ShiftPunch(
    val memberId: String,
    val jobId: String?,
    val kind: String,
    val at: Long,
    val farFromJob: Boolean = false,
    val edited: Boolean = false,
)

data class JobSegment(val jobId: String?, val startAt: Long, val endAt: Long)

/**
 * A derived shift: IN to OUT for one person. [endAt] is null while open.
 * [autoClosed] is set when the OUT was missing and the shift was closed at the limit.
 */
data class Shift(
    val memberId: String,
    val startAt: Long,
    val endAt: Long?,
    val breaks: List<LongRange>,
    val segments: List<JobSegment>,
    val autoClosed: Boolean,
    val farFromJob: Boolean,
    val edited: Boolean,
) {
    val jobId: String? get() = segments.firstOrNull()?.jobId
    fun breakMillis(): Long = breaks.sumOf { it.last - it.first }
    fun elapsedMillis(now: Long): Long = (endAt ?: now) - startAt
    fun workedMillis(now: Long): Long = elapsedMillis(now) - breakMillis()
}

/** Builds [Shift]s from a person's punches (AC-F10, AC-F12). */
object ShiftBuilder {
    const val MAX_SHIFT_MILLIS = 14 * 3_600_000L

    fun build(punches: List<ShiftPunch>, now: Long): List<Shift> =
        punches.groupBy { it.memberId }.flatMap { (_, mine) -> buildFor(mine.sortedBy { it.at }, now) }
            .sortedBy { it.startAt }

    private fun buildFor(sorted: List<ShiftPunch>, now: Long): List<Shift> {
        val out = mutableListOf<Shift>()
        var open: Builder? = null
        for (p in sorted) {
            when (p.kind) {
                PunchKind.IN -> {
                    // an IN while a shift is open means the OUT was never punched
                    open?.let { out += it.close(minOf(p.at, it.start + MAX_SHIFT_MILLIS), auto = true) }
                    open = Builder(p)
                }
                PunchKind.BREAK_START -> open?.startBreak(p)
                PunchKind.BREAK_END -> open?.endBreak(p)
                PunchKind.SWITCH -> open?.switchTo(p)
                PunchKind.OUT -> open?.let { out += it.close(p.at, auto = false, outPunch = p); open = null }
            }
        }
        open?.let {
            val limit = it.start + MAX_SHIFT_MILLIS
            out += if (now > limit) it.close(limit, auto = true) else it.running()
        }
        return out
    }

    private class Builder(first: ShiftPunch) {
        val member = first.memberId
        val start = first.at
        var far = first.farFromJob
        var edited = first.edited
        val breaks = mutableListOf<LongRange>()
        val segments = mutableListOf<JobSegment>()
        var segJob = first.jobId
        var segStart = first.at
        var breakStart: Long? = null

        fun startBreak(p: ShiftPunch) { absorb(p); if (breakStart == null) breakStart = p.at }
        fun endBreak(p: ShiftPunch) { absorb(p); breakStart?.let { breaks += it..p.at }; breakStart = null }
        fun switchTo(p: ShiftPunch) {
            absorb(p)
            segments += JobSegment(segJob, segStart, p.at)
            segJob = p.jobId; segStart = p.at
        }
        private fun absorb(p: ShiftPunch) { far = far || p.farFromJob; edited = edited || p.edited }

        fun close(end: Long, auto: Boolean, outPunch: ShiftPunch? = null): Shift {
            outPunch?.let { absorb(it) }
            breakStart?.let { breaks += it..end }
            segments += JobSegment(segJob, segStart, end)
            return Shift(member, start, end, breaks.toList(), segments.toList(), auto, far, edited)
        }

        fun running(): Shift = Shift(member, start, null, breaks.toList() + listOfNotNull(breakStart?.let { it..it }),
            segments.toList() + JobSegment(segJob, segStart, segStart), false, far, edited)
    }
}

enum class ShiftFlag { MissingClockOut, FarFromJob, NoBreak, Overlap, Edited }

/** Flags a shift for the timesheet (AC-F12). */
object ShiftFlags {
    const val NO_BREAK_AFTER_MILLIS = 6 * 3_600_000L

    /** Flags per shift, same order as [shifts]. [breakRule] is the company's "break required" setting. */
    fun flags(shifts: List<Shift>, breakRule: Boolean, now: Long): List<Set<ShiftFlag>> =
        shifts.mapIndexed { i, s ->
            buildSet {
                if (s.autoClosed) add(ShiftFlag.MissingClockOut)
                if (s.farFromJob) add(ShiftFlag.FarFromJob)
                if (s.edited) add(ShiftFlag.Edited)
                if (breakRule && s.elapsedMillis(now) > NO_BREAK_AFTER_MILLIS && s.breaks.isEmpty()) add(ShiftFlag.NoBreak)
                val end = s.endAt ?: now
                val overlaps = shifts.withIndex().any { (j, o) ->
                    j != i && o.memberId == s.memberId && o.startAt < end && (o.endAt ?: now) > s.startAt
                }
                if (overlaps) add(ShiftFlag.Overlap)
            }
        }
}
