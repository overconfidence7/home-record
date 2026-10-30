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
import java.time.ZoneId

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
