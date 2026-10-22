package com.kurtz.homerecord.housemodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultFloorTest {
    private fun floor(name: String, elevationIn: Int, rooms: Int = 0) =
        FloorSummary(name, name, 96, false, rooms, elevationIn)

    // AC-H2: a fresh two-story with basement opens on Main, not the cellar
    @Test
    fun emptyHomeOpensOnEntranceLevel() {
        val floors = listOf(floor("Basement", -96), floor("Main", 0), floor("Upper", 108))
        assertEquals("Main", floors.defaultFloor()?.name)
    }

    @Test
    fun floorWithRoomsWins() {
        val floors = listOf(floor("Basement", -96), floor("Main", 0), floor("Upper", 108, rooms = 2))
        assertEquals("Upper", floors.defaultFloor()?.name)
    }

    @Test
    fun splitLevelOpensOnLowestAtOrAboveGrade() {
        val floors = listOf(floor("Lower", -48), floor("Upper", 48))
        assertEquals("Upper", floors.defaultFloor()?.name)
    }

    @Test
    fun allBelowGradeFallsBackToFirstAndEmptyIsNull() {
        assertEquals("A", listOf(floor("A", -96), floor("B", -48)).defaultFloor()?.name)
        assertNull(emptyList<FloorSummary>().defaultFloor())
    }
}
