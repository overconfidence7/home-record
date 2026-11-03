package com.kurtz.homerecord.housemodel.walls

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test

// R10a: wall photos through a split, a merge and a moved corner in the plan editor (AC-C17, AC-C19, AC-C27).
class WallPhotoCarrierTest {
    private val square = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120))
    private val notched = listOf(Pt(0, 0), Pt(72, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120))

    private fun box(left: Float, top: Float, right: Float, bottom: Float) =
        WallOutline(PhotoPoint(left, top), PhotoPoint(right, top), PhotoPoint(right, bottom), PhotoPoint(left, bottom))

    private fun assertOutline(actual: WallOutline, expected: WallOutline) {
        actual.corners.zip(expected.corners).forEach { (a, e) ->
            assertThat(a.x).isWithin(1e-4f).of(e.x)
            assertThat(a.y).isWithin(1e-4f).of(e.y)
        }
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun aSplitWallKeepsItsHalfOfThePhotoAndTheNewPartGetsTheOtherHalf() {
        val photo = CarriedPhoto("p1", "north", WallStage.FINISHED, box(0.1f, 0.2f, 0.9f, 0.8f))
        val moves = WallPhotoCarrier.carry(
            square, mapOf("north" to 0, "east" to 1, "south" to 2, "west" to 3),
            mapOf(0 to "north", 1 to "new", 2 to "east", 3 to "south", 4 to "west"), notched, listOf(photo),
        )
        assertThat(moves).hasSize(2)
        val recrop = moves.filterIsInstance<PhotoMove.Recrop>().single()
        val copy = moves.filterIsInstance<PhotoMove.Copy>().single()
        assertThat(recrop.rowId).isEqualTo("p1")
        assertOutline(recrop.outline, box(0.1f, 0.2f, 0.5f, 0.8f))
        assertThat(copy.rowId).isEqualTo("p1")
        assertThat(copy.toWallId).isEqualTo("new")
        assertOutline(copy.outline, box(0.5f, 0.2f, 0.9f, 0.8f))
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun aSplitWallInARoomDrawnCounterClockwiseKeepsTheHalfOnItsSide() {
        // R10b: corners stored the other way round, north wall on edge 2 running east to west. The photo's left half
        // is the west end as you face the wall from inside, so the part on the west (new edge 3) gets it.
        val ccw = square.reversed()
        val split = listOf(Pt(0, 120), Pt(144, 120), Pt(144, 0), Pt(72, 0), Pt(0, 0))
        val photo = CarriedPhoto("p1", "north", WallStage.FINISHED, box(0.1f, 0.2f, 0.9f, 0.8f))
        val moves = WallPhotoCarrier.carry(
            ccw, mapOf("south" to 0, "east" to 1, "north" to 2, "west" to 3),
            mapOf(0 to "south", 1 to "east", 2 to "north", 3 to "new", 4 to "west"), split, listOf(photo),
        )
        assertOutline(moves.filterIsInstance<PhotoMove.Recrop>().single().outline, box(0.5f, 0.2f, 0.9f, 0.8f))
        val copy = moves.filterIsInstance<PhotoMove.Copy>().single()
        assertThat(copy.toWallId).isEqualTo("new")
        assertOutline(copy.outline, box(0.1f, 0.2f, 0.5f, 0.8f))
    }

    @AcceptanceCriterion("AC-C27")
    @Test fun theCutFollowsThePhotosPerspectiveSoOverlaysStayOnTheirPart() {
        // A wall shot at an angle: the far end is smaller, so the wall's midpoint is not the outline's midpoint.
        val angled = WallOutline(PhotoPoint(0.1f, 0.1f), PhotoPoint(0.8f, 0.3f), PhotoPoint(0.8f, 0.7f), PhotoPoint(0.1f, 0.9f))
        val cut = checkNotNull(WallPhotoCarrier.crop(angled, 0f, 0.5f))
        val mid = WallSpaceMapper.wallToPhoto(angled, WallPoint(0.5f, 0f))
        assertThat(cut.topRight.x).isWithin(1e-4f).of(mid.x)
        assertThat(cut.topRight.y).isWithin(1e-4f).of(mid.y)
        assertThat(cut.topRight.x).isGreaterThan(0.45f)
        // A point drawn on the left half of the wall sits on the kept part at twice its distance along.
        val onWhole = WallSpaceMapper.wallToPhoto(angled, WallPoint(0.25f, 0.5f))
        val onPart = WallSpaceMapper.wallToPhoto(cut, WallPoint(0.5f, 0.5f))
        assertThat(onPart.x).isWithin(1e-3f).of(onWhole.x)
        assertThat(onPart.y).isWithin(1e-3f).of(onWhole.y)
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun aWallWhoseCornersMovedKeepsItsOutline() {
        val photo = CarriedPhoto("p1", "north", WallStage.FINISHED, box(0.1f, 0.2f, 0.9f, 0.8f))
        val walls = mapOf("north" to 0, "east" to 1, "south" to 2, "west" to 3)
        val wider = listOf(Pt(0, 0), Pt(168, 0), Pt(168, 120), Pt(0, 120))
        assertThat(WallPhotoCarrier.carry(square, walls, walls.entries.associate { (k, v) -> v to k }, wider, listOf(photo))).isEmpty()
        val shifted = listOf(Pt(0, -24), Pt(144, -24), Pt(144, 120), Pt(0, 120))
        assertThat(WallPhotoCarrier.carry(square, walls, walls.entries.associate { (k, v) -> v to k }, shifted, listOf(photo))).isEmpty()
    }

    @AcceptanceCriterion("AC-C19")
    @Test fun aMergedWallStretchesItsOwnPhotoAlongTheWallWhenThePhotoSawThatFar() {
        val walls = mapOf("a" to 0, "b" to 1, "east" to 2, "south" to 3, "west" to 4)
        val own = CarriedPhoto("pa", "a", WallStage.FINISHED, box(0.1f, 0.2f, 0.4f, 0.8f))
        val other = CarriedPhoto("pb", "b", WallStage.FINISHED, box(0.5f, 0.2f, 0.9f, 0.8f))
        val moves = WallPhotoCarrier.carry(notched, walls, mapOf(0 to "a", 1 to "east", 2 to "south", 3 to "west"), square, listOf(own, other))
        val recrop = moves.single() as PhotoMove.Recrop
        assertThat(recrop.rowId).isEqualTo("pa")
        assertOutline(recrop.outline, box(0.1f, 0.2f, 0.7f, 0.8f))
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun aMergedWallWithoutAPhotoTakesItsNeighboursKeepingTheOutlineWhenItRunsOffThePhoto() {
        val walls = mapOf("a" to 0, "b" to 1, "east" to 2, "south" to 3, "west" to 4)
        val tight = CarriedPhoto("pb", "b", WallStage.ROUGH_IN, box(0.6f, 0.2f, 0.9f, 0.8f))
        val wide = CarriedPhoto("pb2", "b", WallStage.FINISHED, box(0.1f, 0.2f, 0.9f, 0.8f))
        val moves = WallPhotoCarrier.carry(notched, walls, mapOf(0 to "a", 1 to "east", 2 to "south", 3 to "west"), square, listOf(tight, wide))
            .filterIsInstance<PhotoMove.Copy>().associateBy { it.rowId }
        assertThat(moves.keys).containsExactly("pb", "pb2")
        assertThat(moves.values.map { it.toWallId }.toSet()).containsExactly("a")
        // Rough-in saw the whole merged wall: stretched to it. Finished filled the frame: kept as it was.
        assertOutline(moves.getValue("pb").outline, box(0.3f, 0.2f, 0.9f, 0.8f))
        assertOutline(moves.getValue("pb2").outline, box(0.1f, 0.2f, 0.9f, 0.8f))
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun eachStageIsCutAndAPhotoWithoutAnOutlineIsTheWholeWall() {
        val rough = CarriedPhoto("r", "north", WallStage.ROUGH_IN, null)
        val finished = CarriedPhoto("f", "north", WallStage.FINISHED, box(0f, 0f, 1f, 1f))
        val moves = WallPhotoCarrier.carry(
            square, mapOf("north" to 0, "east" to 1, "south" to 2, "west" to 3),
            mapOf(0 to "north", 1 to "new", 2 to "east", 3 to "south", 4 to "west"), notched, listOf(rough, finished),
        )
        assertThat(moves.filterIsInstance<PhotoMove.Recrop>().map { it.rowId }).containsExactly("r", "f")
        assertThat(moves.filterIsInstance<PhotoMove.Copy>().map { it.rowId }).containsExactly("r", "f")
        moves.filterIsInstance<PhotoMove.Copy>().forEach { assertOutline(it.outline, box(0.5f, 0f, 1f, 1f)) }
    }
}
