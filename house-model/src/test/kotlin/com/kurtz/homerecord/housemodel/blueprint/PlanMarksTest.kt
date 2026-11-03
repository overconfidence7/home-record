package com.kurtz.homerecord.housemodel.blueprint

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.scene.ItemSpot
import com.kurtz.homerecord.housemodel.scene.MarkerTarget
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Test

// AC-C15: the blueprint's layers draw wall overlays along their wall and items at their plan spot, in their layer.
class PlanMarksTest {
    /** 14' x 12' kitchen, corners clockwise on screen: edge 0 is the north wall from west to east. */
    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    /** The same room with its corners stored the other way round (a corner dragged across). */
    private val kitchenReversed = kitchen.copy(points = kitchen.points.reversed())

    private fun overlay(id: String, kind: OverlayKind, vararg points: Pair<Float, Float>, wallId: String? = "w", roomId: String = "k") =
        Overlay(id, "h", roomId, wallId, kind, "Sink cold supply", null, OverlayDepth.BEHIND_DRYWALL, points.map { OverlayPoint(it.first, it.second) }, WallStage.ROUGH_IN, null, null, 0L)

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `a run across the north wall lies along it, set toward the room`() {
        // Left to right seen from inside facing north is west to east.
        val run = overlay("o", OverlayKind.WATER_COLD, 0.25f to 0.7f, 0.75f to 0.7f)
        val mark = PlanMarks.place(listOf(kitchen), listOf(run), mapOf("w" to WallKey("k", 0))).single()
        assertThat(mark.points).containsExactly(OverlayPoint(42f, 0f), OverlayPoint(126f, 0f)).inOrder()
        assertThat(mark.inward).isEqualTo(OverlayPoint(0f, 1f))
        assertThat(mark.layer).isEqualTo(BlueprintLayer.PLUMBING)
        assertThat(mark.target).isEqualTo(MarkerTarget.OverlayMark("o"))
        assertThat(mark.isRun).isTrue()
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `the same wall stored the other way round puts the run in the same place`() {
        // Reversed corners: edge 1 runs from (168, 0) to (0, 0), the north wall from east to west.
        val north = kitchenReversed.points.indices.first { i ->
            val a = kitchenReversed.points[i]
            val b = kitchenReversed.points[(i + 1) % 4]
            a.y == 0 && b.y == 0
        }
        val run = overlay("o", OverlayKind.WATER_COLD, 0.25f to 0.7f, 0.75f to 0.7f)
        val mark = PlanMarks.place(listOf(kitchenReversed), listOf(run), mapOf("w" to WallKey("k", north))).single()
        assertThat(mark.points).containsExactly(OverlayPoint(42f, 0f), OverlayPoint(126f, 0f)).inOrder()
        assertThat(mark.inward).isEqualTo(OverlayPoint(0f, 1f))
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `a riser straight up the wall and an outlet become spots where they stand`() {
        val riser = overlay("r", OverlayKind.ELECTRICAL_WIRE, 0.5f to 0.1f, 0.5f to 0.6f, 0.51f to 0.8f)
        val outlet = overlay("p", OverlayKind.OUTLET, 0.9f to 0.6f)
        // East wall (edge 1) runs north to south; left to right seen from inside facing east is north to south.
        val marks = PlanMarks.place(listOf(kitchen), listOf(riser, outlet), mapOf("w" to WallKey("k", 1)))
        val r = marks.first { it.target == MarkerTarget.OverlayMark("r") }
        assertThat(r.isRun).isFalse()
        assertThat(r.points.single().x).isEqualTo(168f)
        assertThat(r.points.single().y).isWithin(0.5f).of(72.5f)
        assertThat(r.inward).isEqualTo(OverlayPoint(-1f, 0f))
        val p = marks.first { it.target == MarkerTarget.OverlayMark("p") }
        assertThat(p.points.single().y).isWithin(0.01f).of(129.6f)
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `items sit at their plan spot on the Items layer with their icon, other floors and unknown walls are left out`() {
        val items = listOf(
            ItemSpot("i1", "k", 30, 40, "Refrigerator", iconName = "Kitchen"),
            ItemSpot("i2", "upstairs", 10, 10, "Bedroom fan"),
        )
        val overlays = listOf(
            overlay("gone", OverlayKind.GAS, 0.1f to 0.5f, 0.9f to 0.5f, wallId = "missing"),
            overlay("plan", OverlayKind.GAS, 10f to 10f, 90f to 10f, wallId = null),
            overlay("empty", OverlayKind.GAS, wallId = "w"),
        )
        val marks = PlanMarks.place(listOf(kitchen), overlays, mapOf("w" to WallKey("k", 0)), items)
        val item = marks.single()
        assertThat(item.target).isEqualTo(MarkerTarget.ItemMark("i1"))
        assertThat(item.points).containsExactly(OverlayPoint(30f, 40f))
        assertThat(item.layer).isEqualTo(BlueprintLayer.ITEMS)
        assertThat(item.iconName).isEqualTo("Kitchen")
        assertThat(item.inward).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `each mark shows only with its layer on, studs and other marks always show`() {
        val walls = mapOf("w" to WallKey("k", 0))
        val marks = PlanMarks.place(
            listOf(kitchen),
            listOf(overlay("gas", OverlayKind.GAS, 0.2f to 0.5f), overlay("stud", OverlayKind.STUD, 0.4f to 0.5f)),
            walls,
            listOf(ItemSpot("i1", "k", 30, 40, "Refrigerator")),
        )
        val ids = { set: LayerSet -> PlanMarks.visible(marks, set).map { it.target } }
        assertThat(ids(LayerSet())).containsExactly(MarkerTarget.OverlayMark("stud"))
        assertThat(ids(LayerSet(setOf(BlueprintLayer.GAS)))).containsExactly(MarkerTarget.OverlayMark("gas"), MarkerTarget.OverlayMark("stud"))
        assertThat(ids(LayerSet(setOf(BlueprintLayer.ITEMS)))).containsExactly(MarkerTarget.OverlayMark("stud"), MarkerTarget.ItemMark("i1"))
    }
}
