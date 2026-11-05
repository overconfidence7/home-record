package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AC-C40: SlotOccupancy — two-pole breakers fill two slots and draw as one tall cell; a tandem holds two single-pole breakers
class SlotOccupancyTest {
    private val na = PanelLayout.NORTH_AMERICAN
    private val din = PanelLayout.DIN

    private fun b(id: String, slots: List<Int>, poles: Int = slots.size, at: Long = 0) =
        Breaker(id, "p", slots, poles, 20, BreakerType.STANDARD, id, LinkConfidence.UNKNOWN, at)

    @Test fun northAmericanPutsOddSlotsLeftAndEvenRightAndTwoPoleGoesDownTheColumn() {
        assertThat(SlotOccupancy.position(na, 1)).isEqualTo(0 to 0)
        assertThat(SlotOccupancy.position(na, 2)).isEqualTo(0 to 1)
        assertThat(SlotOccupancy.position(na, 14)).isEqualTo(6 to 1)
        assertThat(SlotOccupancy.rows(na, 40)).isEqualTo(20)
        assertThat(SlotOccupancy.slotsFor(na, 40, 13, 2)).containsExactly(13, 15).inOrder()
        assertThat(SlotOccupancy.slotsFor(na, 40, 39, 2)).isNull()
        assertThat(SlotOccupancy.slotsFor(din, 12, 3, 2)).containsExactly(3, 4).inOrder()
        assertThat(SlotOccupancy.rows(din, 12)).isEqualTo(12)
    }

    @Test fun placeRefusesTakenSlotsAndOffPanel() {
        val others = listOf(b("range", listOf(1, 3)), b("lights", listOf(2)))
        assertThat(SlotOccupancy.place(na, 40, others, 3, 1)).isEqualTo(SlotPlacement.Taken(listOf(3)))
        assertThat(SlotOccupancy.place(na, 40, others, 2, 2)).isEqualTo(SlotPlacement.Taken(listOf(2)))
        assertThat(SlotOccupancy.place(na, 40, others, 4, 2)).isEqualTo(SlotPlacement.Ok(listOf(4, 6)))
        assertThat(SlotOccupancy.place(na, 40, others, 41, 1)).isEqualTo(SlotPlacement.OffPanel)
        assertThat(SlotOccupancy.place(na, 40, others, 0, 1)).isEqualTo(SlotPlacement.OffPanel)
    }

    @Test fun tandemSharesWithOneSinglePoleOnly() {
        val lights = b("lights", listOf(2))
        assertThat(SlotOccupancy.place(na, 40, listOf(lights), 2, 1, tandem = true)).isEqualTo(SlotPlacement.Ok(listOf(2)))
        assertThat(SlotOccupancy.place(na, 40, listOf(lights), 2, 2, tandem = true)).isEqualTo(SlotPlacement.TandemNeedsOnePole)
        val full = listOf(lights, b("bath", listOf(2)))
        assertThat(SlotOccupancy.place(na, 40, full, 2, 1, tandem = true)).isEqualTo(SlotPlacement.Taken(listOf(2)))
        assertThat(SlotOccupancy.place(na, 40, listOf(b("range", listOf(1, 3))), 1, 1, tandem = true)).isEqualTo(SlotPlacement.Taken(listOf(1)))
    }

    @Test fun gridDrawsTwoPoleAsOneTallCellAndTandemAsTwoIds() {
        val grid = SlotOccupancy.grid(na, 8, listOf(b("range", listOf(1, 3), at = 1), b("a", listOf(4), at = 2), b("b", listOf(4), at = 3)))
        assertThat(grid.columns).isEqualTo(2)
        assertThat(grid.rows).isEqualTo(4)
        assertThat(grid.cells.map { it.slot }).containsExactly(1, 2, 4, 5, 6, 7, 8).inOrder()
        val range = grid.cells.first { it.slot == 1 }
        assertThat(range.rowSpan).isEqualTo(2)
        assertThat(range.breakerIds).containsExactly("range")
        val tandem = grid.cells.first { it.slot == 4 }
        assertThat(tandem.isTandem).isTrue()
        assertThat(tandem.breakerIds).containsExactly("a", "b").inOrder()
        assertThat(grid.cells.first { it.slot == 2 }.isEmpty).isTrue()
        assertThat(grid.unplaced).isEmpty()
    }

    @Test fun clashingOrMisshapenBreakersAreUnplacedNotDropped() {
        val grid = SlotOccupancy.grid(na, 8, listOf(b("first", listOf(1), at = 1), b("clash", listOf(1, 3), at = 2), b("bad", listOf(5, 6), poles = 2, at = 3)))
        assertThat(grid.unplaced).containsExactly("clash", "bad")
        assertThat(grid.cells.first { it.slot == 1 }.breakerIds).containsExactly("first")
        assertThat(SlotOccupancy.highestUsed(listOf(b("x", listOf(13, 15))))).isEqualTo(15)
    }
}
