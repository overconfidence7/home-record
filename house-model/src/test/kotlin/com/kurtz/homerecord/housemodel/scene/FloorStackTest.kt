package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.FloorSummary
import org.junit.Test

// AC-C21: one floor shows with the floors below it ghosted; All floors draws every floor, pulled apart.
class FloorStackTest {
    // Stored top-down on purpose: the stack goes by elevation, not list order.
    private val floors = listOf(
        FloorSummary("up", "Upper", 96, false, 5, elevationIn = 108),
        FloorSummary("main", "Main", 108, false, 7, elevationIn = 0),
        FloorSummary("base", "Basement", 90, false, 4, elevationIn = -102),
    )

    @Test
    @AcceptanceCriterion("AC-C21")
    fun oneFloorGhostsTheFloorsBelowAndLeavesOutTheOnesAbove() {
        val main = FloorStack.layout(floors, "main", allFloors = false)
        assertThat(main).containsExactly(
            StackedFloor("base", 90, -102, ghost = true),
            StackedFloor("main", 108, 0, ghost = false),
        ).inOrder()

        val upper = FloorStack.layout(floors, "up", allFloors = false)
        assertThat(upper.map { it.floorId to it.ghost }).containsExactly("base" to true, "main" to true, "up" to false).inOrder()

        val basement = FloorStack.layout(floors, "base", allFloors = false)
        assertThat(basement).containsExactly(StackedFloor("base", 90, -102, ghost = false))
    }

    @Test
    @AcceptanceCriterion("AC-C21")
    fun allFloorsAreSolidAndPulledApartBottomFirst() {
        val all = FloorStack.layout(floors, "main", allFloors = true)
        assertThat(all.map { it.floorId }).containsExactly("base", "main", "up").inOrder()
        assertThat(all.none { it.ghost }).isTrue()
        assertThat(all[0].baseYIn).isEqualTo(-102)
        // Each floor starts at least a gap above the top of the one under it.
        for (i in 1 until all.size) {
            assertThat(all[i].baseYIn).isAtLeast(all[i - 1].baseYIn + all[i - 1].ceilingHeightIn + FloorStack.EXPLODE_GAP_IN)
        }
    }

    @Test
    @AcceptanceCriterion("AC-C21")
    fun floorsWithoutElevationsStillStackApartAndAnUnknownFloorShowsNothing() {
        val flat = listOf(FloorSummary("a", "Main", 96, false, 3), FloorSummary("b", "Upper", 96, false, 2))
        val all = FloorStack.layout(flat, null, allFloors = true)
        assertThat(all.map { it.baseYIn }).containsExactly(0, 96 + FloorStack.EXPLODE_GAP_IN).inOrder()
        // Same elevation is not "below": nothing to ghost.
        assertThat(FloorStack.layout(flat, "b", allFloors = false)).containsExactly(StackedFloor("b", 96, 0, ghost = false))
        assertThat(FloorStack.layout(flat, "gone", allFloors = false)).isEmpty()
    }
}
