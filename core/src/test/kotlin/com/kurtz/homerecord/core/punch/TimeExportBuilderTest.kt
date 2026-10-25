package com.kurtz.homerecord.core.punch

import com.kurtz.homerecord.core.company.OvertimeRule
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.model.TimesheetRules
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val H = 3_600_000L
private val UTC = ZoneOffset.UTC
private val MONDAY = LocalDate.of(2026, 10, 19)

private fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atStartOfDay(UTC).toInstant().toEpochMilli() + hour * H

private fun punch(id: String, kind: String, at: Long, lat: Double? = null, lng: Double? = null) = PunchEntity(
    id = id, companyId = "c", memberId = "m1", jobId = "j1", kind = kind, at = at, lat = lat, lng = lng, createdAt = at, updatedAt = at,
)

class TimeExportBuilderTest {
    private val members = listOf(MemberEntity(id = "m1", companyId = "c", name = "Ana Ruiz", createdAt = 0, updatedAt = 0))
    private val clients = listOf(ClientEntity(id = "k1", companyId = "c", name = "Rivera", createdAt = 0, updatedAt = 0))
    private val jobs = listOf(JobEntity(id = "j1", companyId = "c", clientId = "k1", name = "Kitchen", createdAt = 0, updatedAt = 0))
    private val punches = listOf(
        punch("p1", PunchKind.IN, at(20, 8), 40.12345678, -75.5),
        punch("p2", PunchKind.OUT, at(20, 16), 40.1, -75.6),
        punch("p3", PunchKind.IN, at(28, 8)),
        punch("p4", PunchKind.OUT, at(28, 12)),
    )
    private val rules = TimesheetRules.from(OvertimeRule.Weekly40)

    private fun export(kind: TimeExportKind) =
        TimeExportBuilder.build(kind, members, punches, jobs, clients, listOf(MONDAY), rules, false, UTC, at(30, 0))

    // AC-F15
    @Test fun shiftCsvHasGpsFromPunchesAndOnlyTheChosenWeek() {
        val file = export(TimeExportKind.ShiftCsv)
        val lines = file.csv.trim().split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("Ana Ruiz,Kitchen,2026-10-20 08:00,2026-10-20 16:00"))
        assertTrue(lines[1].endsWith("\"40.12346,-75.50000\",\"40.10000,-75.60000\""))
        assertEquals("shifts-2026-10-19-to-2026-10-25.csv", file.name)
    }

    // AC-F15
    @Test fun quickBooksCsvNamesCustomerColonJob() {
        val lines = export(TimeExportKind.QuickBooksCsv).csv.trim().split("\r\n")
        assertEquals("Rivera:Kitchen,Labor,8.00,2026-10-20,Ana Ruiz", lines[1])
    }
}
