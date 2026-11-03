package com.kurtz.homerecord.housemodel.overlays

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test

// R10: overlay coordinates after a wall is resized, split, merged or removed in the plan editor. AC-C27
@AcceptanceCriterion("AC-C27")
class WallOverlayCarrierTest {
    private val rect = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120))

    private fun pts(vararg xy: Float) = xy.toList().chunked(2).map { (x, y) -> OverlayPoint(x, y) }

    private fun assertOnWall(carried: CarriedOverlay, edge: Int, vararg xy: Float) {
        assertThat(carried).isInstanceOf(CarriedOverlay.OnWall::class.java)
        carried as CarriedOverlay.OnWall
        assertThat(carried.edgeIndex).isEqualTo(edge)
        val expected = pts(*xy)
        assertThat(carried.points).hasSize(expected.size)
        carried.points.zip(expected).forEach { (got, want) ->
            assertThat(got.x).isWithin(1e-4f).of(want.x)
            assertThat(got.y).isWithin(1e-4f).of(want.y)
        }
    }

    @Test fun aWallLengthenedByDraggingASideKeepsItsWallSpacePoints() {
        // East side dragged from 144 to 168: the north wall now runs 168" and the pipe stays between the same corners.
        val wider = listOf(Pt(0, 0), Pt(168, 0), Pt(168, 120), Pt(0, 120))
        assertThat(WallOverlayCarrier.carry(rect, 0, 0, wider, pts(0.25f, 0.5f))).isEqualTo(CarriedOverlay.Unchanged)
    }

    @Test fun aSideDraggedAcrossTheRoomKeepsItsOverlay() {
        val deeper = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 150), Pt(0, 150))
        assertThat(WallOverlayCarrier.carry(rect, 2, 2, deeper, pts(0.5f, 0.5f))).isEqualTo(CarriedOverlay.Unchanged)
    }

    @Test fun aSplitWallInARoomDrawnCounterClockwiseKeepsOverlaysLeftToRightFromInside() {
        // R10b: corners stored the other way round; the north wall is edge 2, running east to west, and wall x still
        // grows from the west (left) end as you face it from inside.
        val ccw = rect.reversed()
        val split = listOf(Pt(0, 120), Pt(144, 120), Pt(144, 0), Pt(72, 0), Pt(0, 0))
        // 18" from the west corner: the west part is the new edge 3, a quarter along it from its left end.
        assertOnWall(WallOverlayCarrier.carry(ccw, 2, 2, split, pts(0.125f, 0.4f)), 3, 0.25f, 0.4f)
        // 126" from the west corner: the east part (edge 2), three quarters along from its left end at 72".
        assertOnWall(WallOverlayCarrier.carry(ccw, 2, 2, split, pts(0.875f, 0.6f)), 2, 0.75f, 0.6f)
    }

    @Test fun aSplitWallHandsEachOverlayToThePartItSatOn() {
        val split = listOf(Pt(0, 0), Pt(72, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120))
        // 36" from the west corner stays on the first part, halfway along it.
        assertOnWall(WallOverlayCarrier.carry(rect, 0, 0, split, pts(0.25f, 0.4f)), 0, 0.5f, 0.4f)
        // 108" goes to the new wall from the corner at 72", halfway along it.
        assertOnWall(WallOverlayCarrier.carry(rect, 0, 0, split, pts(0.75f, 0.6f)), 1, 0.5f, 0.6f)
    }

    @Test fun aRunAcrossTheNewCornerGoesWhereMostOfItIsAndStopsAtTheCorner() {
        val split = listOf(Pt(0, 0), Pt(72, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120))
        assertOnWall(WallOverlayCarrier.carry(rect, 0, 0, split, pts(0.4f, 0.5f, 0.9f, 0.5f, 0.95f, 0.2f)), 1, 0f, 0.5f, 0.8f, 0.5f, 0.9f, 0.2f)
    }

    @Test fun aWallShortenedWithNothingElseOnItsLineKeepsItsWallSpacePoints() {
        // East side dragged in to 120": the north wall is shorter and nothing else runs along its line.
        val narrower = listOf(Pt(0, 0), Pt(120, 0), Pt(120, 120), Pt(0, 120))
        assertThat(WallOverlayCarrier.carry(rect, 0, 0, narrower, pts(0.75f, 0.3f))).isEqualTo(CarriedOverlay.Unchanged)
    }

    @Test fun mergedWallsKeepEveryOverlayAtItsPlaceAlongTheLine() {
        val withCorner = listOf(Pt(0, 0), Pt(72, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120))
        // The kept wall (edge 0, 72") and the removed one (edge 1) become one 144" wall.
        assertOnWall(WallOverlayCarrier.carry(withCorner, 0, 0, rect, pts(0.5f, 0.5f)), 0, 0.25f, 0.5f)
        assertOnWall(WallOverlayCarrier.carry(withCorner, 1, null, rect, pts(0.5f, 0.5f)), 0, 0.75f, 0.5f)
    }

    @Test fun anOverlayWhoseWallIsGoneGoesOntoThePlanWhereItWas() {
        val den = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 60), Pt(96, 60), Pt(96, 120), Pt(0, 120))
        val diagonal = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 60), Pt(96, 120), Pt(0, 120))
        // Edge 2 ran (144,60) → (96,60); a run from a quarter to three quarters along it lands at plan x 132 and 108.
        assertThat(WallOverlayCarrier.carry(den, 2, null, diagonal, pts(0.25f, 0.5f, 0.75f, 0.5f)))
            .isEqualTo(CarriedOverlay.OnPlan(pts(132f, 60f, 108f, 60f)))
    }
}
