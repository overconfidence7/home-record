package com.kurtz.homerecord.housemodel.blueprint

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.scene.MarkerTarget
import org.junit.Test

// AC-C15 (T055a): a mark the blueprint's layers draw opens its record on tap, and room names keep clear of the marks.
class PlanMarkHitsTest {
    /** 2 px per inch, plan origin at (10, 20). */
    private val view = BlueprintView(2f, 10f, 20f)

    private val pipe = PlanMark(
        MarkerTarget.OverlayMark("pipe"), "k", BlueprintLayer.PLUMBING, OverlayKind.WATER_COLD,
        listOf(OverlayPoint(42f, 0f), OverlayPoint(126f, 0f)), inward = OverlayPoint(0f, 1f), label = "Sink cold supply",
    )
    private val fridge = PlanMark(
        MarkerTarget.ItemMark("fridge"), "k", BlueprintLayer.ITEMS, null, listOf(OverlayPoint(60f, 6f)), iconName = "Kitchen", label = "Refrigerator",
    )

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `a tap on a wall run opens it where it is drawn, set into the room`() {
        // The run is drawn 14 px inside the north wall: y = 20 + 14. Halfway along: x = 10 + 84 * 2.
        val hit = PlanMarkHitTester.markAt(listOf(pipe), view, 178f, 34f, insetPx = 14f, radiusPx = 24f)
        assertThat(hit?.target).isEqualTo(MarkerTarget.OverlayMark("pipe"))
        // Beyond the run's end by more than the radius: nothing.
        assertThat(PlanMarkHitTester.markAt(listOf(pipe), view, 300f, 34f, 14f, 24f)).isNull()
        // Inside the room, well clear of the wall: the room beneath gets the tap.
        assertThat(PlanMarkHitTester.markAt(listOf(pipe), view, 178f, 120f, 14f, 24f)).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `an item drawn over a run wins the tap, as it is drawn on top`() {
        // The fridge sits at (130, 32), 2 px from the run's line.
        val hit = PlanMarkHitTester.markAt(listOf(pipe, fridge), view, 131f, 34f, 14f, 24f)
        assertThat(hit?.target).isEqualTo(MarkerTarget.ItemMark("fridge"))
        assertThat(hit?.roomId).isEqualTo("k")
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `of two spots in reach the nearer one opens`() {
        val a = pipe.copy(target = MarkerTarget.OverlayMark("a"), points = listOf(OverlayPoint(20f, 50f)), inward = null)
        val b = pipe.copy(target = MarkerTarget.OverlayMark("b"), points = listOf(OverlayPoint(30f, 50f)), inward = null)
        // a at (50, 120), b at (70, 120).
        assertThat(PlanMarkHitTester.markAt(listOf(a, b), view, 66f, 120f, 14f, 24f)?.target).isEqualTo(MarkerTarget.OverlayMark("b"))
        assertThat(PlanMarkHitTester.markAt(listOf(a, b), view, 54f, 120f, 14f, 24f)?.target).isEqualTo(MarkerTarget.OverlayMark("a"))
    }

    private val room = LabelBox(0f, 0f, 400f, 300f)

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `a name with nothing in the way stays at its anchor`() {
        assertThat(LabelPlacer.place(200f, 150f, 120f, 30f, room, emptyList(), emptyList(), 4f)).isEqualTo(200f to 150f)
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `a name over an item steps clear of it inside the room`() {
        val item = LabelPlacer.alongRun(listOf(200f to 150f), 26f)
        val at = LabelPlacer.place(200f, 150f, 120f, 30f, room, emptyList(), item, 4f)
        val box = { c: Pair<Float, Float> -> LabelBox(c.first - 60f, c.second - 15f, c.first + 60f, c.second + 15f) }
        assertThat(at).isNotNull()
        val placed = box(at!!)
        assertThat(item.none { it.overlaps(placed) }).isTrue()
        assertThat(placed.inside(room)).isTrue()
        // The disc spans y 124..176; one step (34 px) up or down still touches it, two steps up clears it.
        assertThat(at).isEqualTo(200f to 82f)
    }

    @Test
    @AcceptanceCriterion("AC-C15")
    fun `a diagonal run blocks only its own path, and stairs always win`() {
        val run = LabelPlacer.alongRun(listOf(0f to 0f, 100f to 100f), 4f)
        assertThat(run.none { it.overlaps(LabelBox(140f, 135f, 260f, 165f)) }).isTrue()
        // Stairs over the whole room but the anchor: no spot is free of them, so the name is left out.
        val stairs = listOf(LabelBox(0f, 0f, 400f, 300f))
        assertThat(LabelPlacer.place(200f, 150f, 120f, 30f, room, stairs, emptyList(), 4f)).isNull()
        // A room smaller than its name with nothing in it keeps the name at the anchor, as before.
        assertThat(LabelPlacer.place(20f, 10f, 120f, 30f, LabelBox(0f, 0f, 40f, 20f), emptyList(), emptyList(), 4f)).isEqualTo(20f to 10f)
    }
}
