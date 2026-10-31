package com.kurtz.homerecord.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// AC-F32: delays typed on the daily logs appear in a job timeline in FL-Reports
class DelayTimelineTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private val sun = mon.plusDays(6)
    private val names = mapOf("hale" to "Hale kitchen", "ortiz" to "Ortiz bath", "garage" to "Garage subpanel")

    private fun day(job: String, offset: Long, vararg delays: LogDelay) = DelayDay(job, mon.plusDays(offset), delays.toList())

    @Test fun groupsByJobOldestFirstInTypedOrder() {
        val out = DelayTimeline.build(
            listOf(
                day("hale", 3, LogDelay(DelayReason.INSPECTION, "Township inspector late")),
                day("hale", 1, LogDelay(DelayReason.WEATHER, "Rain held up the siding"), LogDelay(DelayReason.MATERIALS, "")),
            ),
            names, mon, sun,
        )
        assertEquals(1, out.size)
        val hale = out.single()
        assertEquals("Hale kitchen", hale.jobName)
        assertEquals(2, hale.days)
        assertEquals(
            listOf(
                DelayEntry(mon.plusDays(1), 0, DelayReason.WEATHER, "Rain held up the siding"),
                DelayEntry(mon.plusDays(1), 1, DelayReason.MATERIALS, ""),
                DelayEntry(mon.plusDays(3), 0, DelayReason.INSPECTION, "Township inspector late"),
            ),
            hale.entries,
        )
    }

    @Test fun keepsOnlyTheRangeInclusive() {
        val out = DelayTimeline.build(
            listOf(
                day("hale", -1, LogDelay(DelayReason.CLIENT)),
                day("hale", 0, LogDelay(DelayReason.CLIENT, "Owner away")),
                day("hale", 6, LogDelay(DelayReason.OTHER, "Power out")),
                day("hale", 7, LogDelay(DelayReason.WEATHER)),
            ),
            names, mon, sun,
        )
        assertEquals(listOf(mon, sun), out.single().entries.map { it.day })
    }

    @Test fun mostDelayedJobFirstThenByName() {
        val out = DelayTimeline.build(
            listOf(
                day("ortiz", 0, LogDelay(DelayReason.MATERIALS)),
                day("garage", 2, LogDelay(DelayReason.INSPECTION)),
                day("hale", 1, LogDelay(DelayReason.WEATHER)),
                day("hale", 2, LogDelay(DelayReason.WEATHER)),
            ),
            names, mon, sun,
        )
        assertEquals(listOf("Hale kitchen", "Garage subpanel", "Ortiz bath"), out.map { it.jobName })
    }

    @Test fun skipsDaysWithoutDelaysAndUnknownJobsAndTrimsNotes() {
        val out = DelayTimeline.build(
            listOf(
                day("hale", 1),
                day("deleted-job", 1, LogDelay(DelayReason.WEATHER)),
                day("ortiz", 2, LogDelay(DelayReason.OTHER, "  Waiting on the tile  ")),
            ),
            names, mon, sun,
        )
        assertEquals(listOf("ortiz"), out.map { it.jobId })
        assertEquals("Waiting on the tile", out.single().entries.single().note)
    }

    @Test fun noDelaysIsEmpty() {
        assertTrue(DelayTimeline.build(emptyList(), names, mon, sun).isEmpty())
    }
}
