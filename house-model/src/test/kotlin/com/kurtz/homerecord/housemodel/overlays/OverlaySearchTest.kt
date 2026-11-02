package com.kurtz.homerecord.housemodel.overlays

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Test

// AC-C28: overlays are searchable by label and kind.
class OverlaySearchTest {
    private val names = mapOf(
        OverlayKind.WATER_COLD to "Cold water", OverlayKind.WATER_HOT to "Hot water", OverlayKind.GAS to "Gas",
        OverlayKind.OUTLET to "Outlet", OverlayKind.LV_DATA to "Data cable", OverlayKind.DRAIN to "Drain",
    )
    private val search = OverlaySearch { names[it] ?: it.wire }

    private val sinkCold = overlay("1", OverlayKind.WATER_COLD, "Sink supply", at = 1)
    private val sinkHot = overlay("2", OverlayKind.WATER_HOT, "Sink supply", at = 2)
    private val range = overlay("3", OverlayKind.GAS, "Range line", at = 3)
    private val island = overlay("4", OverlayKind.OUTLET, "Island GFCI", at = 4)
    private val office = overlay("5", OverlayKind.LV_DATA, "Office drop", at = 5)
    private val gasDryer = overlay("6", OverlayKind.OUTLET, "Gas dryer outlet", at = 6)
    private val all = listOf(sinkCold, sinkHot, range, island, office, gasDryer)

    @Test
    @AcceptanceCriterion("AC-C28")
    fun findsByLabelWordPrefix() {
        assertThat(search.search(all, "isl")).containsExactly(island)
        assertThat(search.search(all, "sink sup")).containsExactly(sinkHot, sinkCold).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun findsByKindNameAndKindTerms() {
        assertThat(search.search(all, "cold water")).containsExactly(sinkCold)
        assertThat(search.search(all, "ethernet")).containsExactly(office)
        assertThat(search.search(all, "pipe")).containsExactly(sinkCold, sinkHot, range)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun labelMatchesRankAboveKindMatches() {
        // "gas" is the range line's kind and the start of the dryer outlet's label.
        assertThat(search.search(all, "gas")).containsExactly(gasDryer, range).inOrder()
    }

    @Test
    fun everyWordMustMatchAndBlankListsNewestFirst() {
        assertThat(search.search(all, "sink gas")).isEmpty()
        assertThat(search.search(all, "  ")).containsExactly(gasDryer, office, island, range, sinkHot, sinkCold).inOrder()
    }

    private fun overlay(id: String, kind: OverlayKind, label: String, at: Long) = Overlay(
        id = id, homeId = "h", roomId = "kitchen", wallId = "w", kind = kind, label = label, note = null,
        depth = OverlayDepth.BEHIND_DRYWALL, points = listOf(OverlayPoint(0.5f, 0.5f)), stage = WallStage.ROUGH_IN,
        photoPath = null, itemId = null, updatedAt = at,
    )
}
