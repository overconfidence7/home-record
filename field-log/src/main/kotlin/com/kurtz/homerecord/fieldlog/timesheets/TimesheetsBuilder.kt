package com.kurtz.homerecord.fieldlog.timesheets

import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.ShiftBuilder
import com.kurtz.homerecord.core.model.ShiftPunch
import com.kurtz.homerecord.core.model.TimesheetRules
import com.kurtz.homerecord.core.punch.PunchEdit
import com.kurtz.homerecord.fieldlog.clock.WeekSheetBuilder
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** "7:30", "07:30", "2:30 pm" or "2pm" to a time of day; null when it is not one (AC-F14 edit sheet). Pure. */
object TimeText {
    private val pattern = Regex("""^\s*(\d{1,2})(?::(\d{2}))?\s*([ap])?\.?m?\.?\s*$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): LocalTime? {
        val m = pattern.matchEntire(text) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val meridiem = m.groupValues[3].lowercase()
        if (minute > 59) return null
        if (meridiem.isNotEmpty()) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (meridiem == "p") 12 else 0
        } else if (hour > 23) return null
        return LocalTime.of(hour, minute)
    }
}

/** Builds the Timesheets week: one [PersonWeek] per member with punches or an approval in the week (AC-F13, AC-F14). Pure. */
object TimesheetsBuilder {
    private val json = Json { ignoreUnknownKeys = true }

    fun build(
        members: List<MemberEntity>,
        punches: List<PunchEntity>,
        jobNames: Map<String, String>,
        approved: Set<Pair<String, String>>,
        weekStart: LocalDate,
        rules: TimesheetRules,
        breakRule: Boolean,
        zone: ZoneId,
        now: Long,
    ): List<PersonWeek> {
        val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val key = weekStart.toString()
        val byMember = punches.groupBy { it.memberId }
        return members.mapNotNull { m ->
            val mine = byMember[m.id].orEmpty().sortedBy { it.at }
            val inWeek = mine.filter { it.at in from until to }
            val isApproved = (m.id to key) in approved
            if (inWeek.isEmpty() && !isApproved) return@mapNotNull null
            val shifts = ShiftBuilder.build(mine.map { ShiftPunch(it.memberId, it.jobId, it.kind, it.at, it.farFromJob, it.editsJson != "[]") }, now)
            val sheet = WeekSheetBuilder.build(shifts, rules, breakRule, zone, now, weekStart)
            PersonWeek(
                memberId = m.id, name = m.name, sheet = sheet, flags = sheet.days.flatMap { it.flags }.toSet(), approved = isApproved,
                punches = inWeek.map { p ->
                    PunchRow(
                        p.id, p.kind, p.at, p.jobId, p.jobId?.let(jobNames::get), p.farFromJob,
                        runCatching { json.decodeFromString<List<PunchEdit>>(p.editsJson) }.getOrDefault(emptyList()),
                    )
                },
            )
        }.sortedBy { it.name.lowercase() }
    }
}
