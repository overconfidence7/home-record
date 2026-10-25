package com.kurtz.homerecord.core.model

import com.kurtz.homerecord.core.data.PunchEntity
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class OnSiteBuilderTest {
    private val hour = 3_600_000L
    private val t0 = 1_700_000_000_000L
    private val names = mapOf("m1" to "Maria Soto", "m2" to "Dev Patel")
    private val jobs = mapOf("j1" to "Kitchen Remodel", "j2" to "Deck")

    private fun punch(member: String, kind: String, at: Long, job: String? = "j1", lat: Double? = null, lng: Double? = null) =
        PunchEntity(id = "$member-$kind-$at", companyId = "c", memberId = member, jobId = job, kind = kind, at = at, lat = lat, lng = lng, createdAt = at, updatedAt = at)

    // AC-F16
    @Test fun listsOnlyOpenShiftsWithJobSinceAndPin() {
        val punches = listOf(
            punch("m1", PunchKind.IN, t0, lat = 40.0, lng = -75.0),
            punch("m2", PunchKind.IN, t0, job = "j2"),
            punch("m2", PunchKind.OUT, t0 + 2 * hour, job = "j2"),
        )
        val rows = OnSiteBuilder.build(punches, names, jobs, t0 + 3 * hour)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("Maria Soto", row.name)
        assertEquals("Kitchen Remodel", row.jobName)
        assertEquals(t0, row.since)
        assertTrue(row.hasPin)
        assertFalse(row.onBreak)
    }

    // AC-F16
    @Test fun noFixMeansNoPinAndSwitchShowsTheCurrentJob() {
        val punches = listOf(
            punch("m1", PunchKind.IN, t0),
            punch("m1", PunchKind.SWITCH, t0 + hour, job = "j2"),
        )
        val row = OnSiteBuilder.build(punches, names, jobs, t0 + 2 * hour).single()
        assertFalse(row.hasPin)
        assertEquals("Deck", row.jobName)
        assertEquals(t0, row.since)
    }

    // AC-F16
    @Test fun breakShowsAndLongestOnSiteComesFirst() {
        val punches = listOf(
            punch("m2", PunchKind.IN, t0 + hour, job = "j2"),
            punch("m1", PunchKind.IN, t0),
            punch("m1", PunchKind.BREAK_START, t0 + hour),
        )
        val rows = OnSiteBuilder.build(punches, names, jobs, t0 + 2 * hour)
        assertEquals(listOf("m1", "m2"), rows.map { it.memberId })
        assertTrue(rows[0].onBreak)
        assertNull(OnSiteBuilder.build(emptyList(), names, jobs, t0).firstOrNull())
    }
}
