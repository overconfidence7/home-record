package com.kurtz.homerecord.fieldlog.timesheets

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.core.model.TimesheetRules
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// AC-F14 (a week per person with flags), AC-F13 (totals match the clock's own sheet), AC-F12 (flags)
class TimesheetsBuilderTest {
    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 10, 19)
    private fun at(day: Int, h: Int, m: Int = 0) = monday.plusDays(day.toLong()).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun member(id: String, name: String) = MemberEntity(id = id, companyId = "c1", name = name, createdAt = 1, updatedAt = 1)
    private fun punch(id: String, who: String, kind: String, at: Long, far: Boolean = false, edits: String = "[]") =
        PunchEntity(id = id, companyId = "c1", memberId = who, jobId = "j1", kind = kind, at = at, farFromJob = far, editsJson = edits, createdAt = at, updatedAt = at)

    private fun build(punches: List<PunchEntity>, approved: Set<Pair<String, String>> = emptySet(), start: LocalDate = monday) =
        TimesheetsBuilder.build(
            listOf(member("m2", "Sam Rivera"), member("m1", "Dana Whitfield"), member("m3", "Idle Ian")), punches, mapOf("j1" to "Garage subpanel"),
            approved, start, TimesheetRules.from(com.kurtz.homerecord.core.company.OvertimeRule.None), true, zone, at(6, 20),
        )

    @Test fun oneRowPerPersonWithPunchesSortedByName() {
        val rows = build(
            listOf(
                punch("a", "m2", PunchKind.IN, at(0, 8)), punch("b", "m2", PunchKind.OUT, at(0, 16)),
                punch("c", "m1", PunchKind.IN, at(1, 9)), punch("d", "m1", PunchKind.OUT, at(1, 12)),
            ),
        )
        assertThat(rows.map { it.name }).containsExactly("Dana Whitfield", "Sam Rivera").inOrder()
        assertThat(rows[1].sheet.totalMillis).isEqualTo(8 * 3_600_000L)
        assertThat(rows[1].punches.map { it.jobName }.distinct()).containsExactly("Garage subpanel")
    }

    @Test fun flagsShowOnThePersonsWeek() {
        val rows = build(
            listOf(
                punch("a", "m1", PunchKind.IN, at(2, 7), far = true), punch("b", "m1", PunchKind.OUT, at(2, 15)),
            ),
        )
        assertThat(rows.single().flags).containsAtLeast(ShiftFlag.FarFromJob, ShiftFlag.NoBreak)
    }

    @Test fun punchesOutsideTheWeekAreLeftOut() {
        val rows = build(listOf(punch("a", "m1", PunchKind.IN, at(-1, 8)), punch("b", "m1", PunchKind.OUT, at(-1, 12))))
        assertThat(rows).isEmpty()
    }

    @Test fun approvedWeekIsMarkedAndKeptEvenWithoutPunches() {
        val rows = build(emptyList(), approved = setOf("m1" to "2026-10-19"))
        assertThat(rows.single().approved).isTrue()
        assertThat(build(emptyList(), approved = setOf("m1" to "2026-10-12"))).isEmpty()
    }

    @Test fun editedPunchCarriesItsAuditEntries() {
        val edits = """[{"by":"m9","byName":"Nate","at":5,"reason":"Forgot to clock out","beforeAt":1,"afterAt":2}]"""
        val row = build(listOf(punch("a", "m1", PunchKind.IN, at(0, 8), edits = edits), punch("b", "m1", PunchKind.OUT, at(0, 12)))).single()
        val p = row.punches.first { it.id == "a" }
        assertThat(p.edited).isTrue()
        assertThat(p.edits.single().reason).isEqualTo("Forgot to clock out")
        assertThat(ShiftFlag.Edited in row.flags).isTrue()
    }
}
