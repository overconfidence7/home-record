package com.kurtz.homerecord.core.model

/** Punch kinds as stored in `punch.kind`. */
object PunchKind {
    const val IN = "IN"
    const val OUT = "OUT"
    const val BREAK_START = "BREAK_START"
    const val BREAK_END = "BREAK_END"
    const val SWITCH = "SWITCH"
}

/** A punch reduced to what hour math needs. */
data class PunchPoint(val memberId: String, val jobId: String?, val kind: String, val at: Long)

/**
 * Hours a job has been worked inside a window, from punches (AC-F7).
 * Per person, a span starts at IN, SWITCH or BREAK_END and runs to that person's next punch of any kind;
 * a span with no next punch is still running and ends at [now]. Breaks and time after OUT are not counted.
 */
object JobHours {
    fun workedMillis(punches: List<PunchPoint>, jobId: String, windowStart: Long, windowEnd: Long, now: Long): Long {
        var total = 0L
        for ((_, mine) in punches.groupBy { it.memberId }) {
            val sorted = mine.sortedBy { it.at }
            sorted.forEachIndexed { i, p ->
                val starts = p.kind == PunchKind.IN || p.kind == PunchKind.SWITCH || p.kind == PunchKind.BREAK_END
                if (!starts || p.jobId != jobId) return@forEachIndexed
                val end = sorted.getOrNull(i + 1)?.at ?: now
                val from = maxOf(p.at, windowStart)
                val to = minOf(end, windowEnd)
                if (to > from) total += to - from
            }
        }
        return total
    }
}
