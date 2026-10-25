package com.kurtz.homerecord.core.punch

import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.model.ShiftBuilder
import com.kurtz.homerecord.core.model.ShiftExportRow
import com.kurtz.homerecord.core.model.ShiftFlags
import com.kurtz.homerecord.core.model.ShiftPunch
import com.kurtz.homerecord.core.model.TimeExports
import com.kurtz.homerecord.core.model.TimesheetRules
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class TimeExportKind(val fileStem: String) { ShiftCsv("shifts"), QuickBooksCsv("quickbooks-timesheet") }

/** A finished export: a file name and its CSV text. */
data class TimeExportFile(val name: String, val csv: String)

/** Turns stored punches, jobs and people into the shift or QuickBooks CSV for whole Monday weeks (AC-F15). Pure. */
object TimeExportBuilder {
    fun build(
        kind: TimeExportKind,
        members: List<MemberEntity>,
        punches: List<PunchEntity>,
        jobs: List<JobEntity>,
        clients: List<ClientEntity>,
        weekStarts: List<LocalDate>,
        rules: TimesheetRules,
        breakRule: Boolean,
        zone: ZoneId,
        now: Long,
    ): TimeExportFile {
        val weeks = weekStarts.sorted()
        val from = weeks.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val to = weeks.last().plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val live = punches.filter { it.deletedAt == null }
        val shifts = ShiftBuilder.build(
            live.map { ShiftPunch(it.memberId, it.jobId, it.kind, it.at, it.farFromJob, it.editsJson != "[]") }, now,
        ).filter { it.startAt in from until to }
        val names = members.associate { it.id to it.name }
        val csv = when (kind) {
            TimeExportKind.ShiftCsv -> {
                val flags = ShiftFlags.flags(shifts, breakRule, now)
                val ins = live.filter { it.kind == PunchKind.IN }.associateBy { it.memberId to it.at }
                val outs = live.filter { it.kind == PunchKind.OUT }.associateBy { it.memberId to it.at }
                val rows = shifts.mapIndexed { i, s ->
                    ShiftExportRow(
                        s, names[s.memberId] ?: s.memberId, flags[i],
                        gps(ins[s.memberId to s.startAt]), gps(s.endAt?.let { outs[s.memberId to it] }),
                    )
                }
                TimeExports.shiftCsv(rows, jobs.associate { it.id to it.name }, rules, zone, now)
            }
            TimeExportKind.QuickBooksCsv -> {
                val clientNames = clients.associate { it.id to it.name }
                val customerJobs = jobs.associate { j -> j.id to (j.clientId?.let(clientNames::get)?.let { "$it:${j.name}" } ?: j.name) }
                TimeExports.quickBooksCsv(shifts, names, customerJobs, zone, now)
            }
        }
        return TimeExportFile("${kind.fileStem}-${weeks.first()}-to-${weeks.last().plusDays(6)}.csv", csv)
    }

    private fun gps(p: PunchEntity?): String {
        val lat = p?.lat ?: return ""
        val lng = p.lng ?: return ""
        return String.format(Locale.US, "%.5f,%.5f", lat, lng)
    }
}
