package com.kurtz.homerecord.core.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeasonalTaskSeederTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun everyZoneSeedsTasksSortedByDueDateOnOrAfterToday() {
        for (zone in ClimateZone.entries) {
            val tasks = SeasonalTaskSeeder.templates(zone, today)
            assertTrue(zone.name, tasks.size >= 8)
            assertEquals(tasks.map { it.nextDue }.sorted(), tasks.map { it.nextDue })
            assertTrue(tasks.all { LocalDate.parse(it.nextDue) >= today })
        }
    }

    @Test
    fun zonesDiffer() {
        val cold = SeasonalTaskSeeder.templates(ClimateZone.COLD, today).map { it.title }.toSet()
        val dry = SeasonalTaskSeeder.templates(ClimateZone.HOT_DRY, today).map { it.title }.toSet()
        assertNotEquals(cold, dry)
        assertTrue("Service furnace" in cold)
        assertTrue("Replace swamp-cooler pads" in dry)
    }

    @Test
    fun zoneNameLookup() {
        assertEquals(ClimateZone.HOT_HUMID, ClimateZone.fromName("HOT_HUMID"))
        assertNull(ClimateZone.fromName("Tropical"))
        assertNull(ClimateZone.fromName(null))
    }
}
