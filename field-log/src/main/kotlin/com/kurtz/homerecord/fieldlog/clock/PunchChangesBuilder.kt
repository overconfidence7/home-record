package com.kurtz.homerecord.fieldlog.clock

import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.punch.PunchEdit
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One change a manager made to one of my punches, as "My timesheet" shows it (AC-F14): which punch, the time and
 * job before and after, who changed it, when and why. Times are already in the phone's zone so the screen only formats.
 * [beforeJob]/[afterJob] are set only when the job changed; null inside means "No job".
 */
data class PunchChange(
    val punchId: String,
    val kind: String,
    val day: LocalDate,
    val before: LocalTime,
    val after: LocalTime,
    val byName: String,
    val changedAt: LocalDateTime,
    val reason: String,
    val jobChanged: Boolean = false,
    val beforeJob: String? = null,
    val afterJob: String? = null,
) {
    val timeChanged: Boolean get() = before != after
}

/** The audit trail of this week's punches, newest change first (AC-F14). Pure. */
object PunchChangesBuilder {
    private val json = Json { ignoreUnknownKeys = true }

    fun build(punches: List<PunchEntity>, jobNames: Map<String, String>, monday: LocalDate, zone: ZoneId): List<PunchChange> {
        val from = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)
        return punches.filter { it.deletedAt == null && it.editsJson != "[]" }
            .flatMap { p ->
                val edits = runCatching { json.decodeFromString<List<PunchEdit>>(p.editsJson) }.getOrDefault(emptyList())
                edits.filter { e -> e.afterAt in from until to || e.beforeAt in from until to }.map { e -> p to e }
            }
            .sortedByDescending { (_, e) -> e.at }
            .map { (p, e) ->
                val jobChanged = e.beforeJobId != e.afterJobId
                PunchChange(
                    punchId = p.id, kind = p.kind, day = local(e.afterAt).toLocalDate(),
                    before = local(e.beforeAt).toLocalTime().withSecond(0).withNano(0),
                    after = local(e.afterAt).toLocalTime().withSecond(0).withNano(0),
                    byName = e.byName, changedAt = local(e.at).toLocalDateTime().withSecond(0).withNano(0), reason = e.reason,
                    jobChanged = jobChanged,
                    beforeJob = if (jobChanged) e.beforeJobId?.let(jobNames::get) else null,
                    afterJob = if (jobChanged) e.afterJobId?.let(jobNames::get) else null,
                )
            }
    }
}
