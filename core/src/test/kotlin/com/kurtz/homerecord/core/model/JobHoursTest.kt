package com.kurtz.homerecord.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

private const val H = 3_600_000L

class JobHoursTest {
    private fun p(m: String, job: String?, kind: String, hour: Long) = PunchPoint(m, job, kind, hour * H)

    // AC-F7
    @Test fun inToOutCounts() {
        val punches = listOf(p("a", "j1", PunchKind.IN, 8), p("a", null, PunchKind.OUT, 12))
        assertEquals(4 * H, JobHours.workedMillis(punches, "j1", 0, 100 * H, 50 * H))
    }

    // AC-F7
    @Test fun breakIsNotCountedAndResumeIs() {
        val punches = listOf(
            p("a", "j1", PunchKind.IN, 8), p("a", "j1", PunchKind.BREAK_START, 12),
            p("a", "j1", PunchKind.BREAK_END, 13), p("a", null, PunchKind.OUT, 16),
        )
        assertEquals(7 * H, JobHours.workedMillis(punches, "j1", 0, 100 * H, 50 * H))
    }

    // AC-F7
    @Test fun switchSplitsHoursBetweenJobs() {
        val punches = listOf(p("a", "j1", PunchKind.IN, 8), p("a", "j2", PunchKind.SWITCH, 10), p("a", null, PunchKind.OUT, 15))
        assertEquals(2 * H, JobHours.workedMillis(punches, "j1", 0, 100 * H, 50 * H))
        assertEquals(5 * H, JobHours.workedMillis(punches, "j2", 0, 100 * H, 50 * H))
    }

    // AC-F7
    @Test fun openSpanRunsToNowAndPeopleAdd() {
        val punches = listOf(p("a", "j1", PunchKind.IN, 8), p("b", "j1", PunchKind.IN, 9))
        assertEquals(3 * H + 2 * H, JobHours.workedMillis(punches, "j1", 0, 100 * H, 11 * H))
    }

    // AC-F7
    @Test fun clipsToTheWindow() {
        val punches = listOf(p("a", "j1", PunchKind.IN, 20), p("a", null, PunchKind.OUT, 30))
        assertEquals(4 * H, JobHours.workedMillis(punches, "j1", 22 * H, 26 * H, 40 * H))
    }
}
