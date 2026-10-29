package com.kurtz.homerecord.housemodel.plan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private fun rect(id: String, l: Int, t: Int, w: Int, h: Int) = PlanRoom.rectangle(id, id, "OTHER", l, t, w, h)

// AC-C8
class WallDeriverTest {
    private val deriver = WallDeriver()

    @Test fun rectangleHasFourWallsFacingOutward() {
        val walls = deriver.derive(listOf(rect("a", 0, 0, 144, 120)))
        assertThat(walls.map { it.orientation }).containsExactly("N", "E", "S", "W").inOrder()
        assertThat(walls.map { it.lengthIn }).containsExactly(144, 120, 144, 120).inOrder()
        assertThat(walls.all { it.exterior }).isTrue()
    }

    @Test fun orientationIndependentOfWinding() {
        val cw = rect("a", 0, 0, 100, 80)
        val ccw = cw.copy(points = cw.points.reversed())
        val byEdge = deriver.derive(listOf(ccw))
        assertThat(byEdge.map { it.orientation }.toSet()).containsExactly("N", "E", "S", "W")
        assertThat(byEdge.first { it.orientation == "N" }.lengthIn).isEqualTo(100)
    }

    @Test fun sharedEdgeIsInteriorForBothRooms() {
        val walls = deriver.derive(listOf(rect("a", 0, 0, 120, 120), rect("b", 120, 0, 120, 120)))
        val aEast = walls.first { it.roomId == "a" && it.orientation == "E" }
        val bWest = walls.first { it.roomId == "b" && it.orientation == "W" }
        assertThat(aEast.exterior).isFalse()
        assertThat(bWest.exterior).isFalse()
        assertThat(walls.first { it.roomId == "a" && it.orientation == "N" }.exterior).isTrue()
    }

    @Test fun sharedWithinFourInchesCounts() {
        val walls = deriver.derive(listOf(rect("a", 0, 0, 120, 120), rect("b", 123, 0, 120, 120)))
        assertThat(walls.first { it.roomId == "a" && it.orientation == "E" }.exterior).isFalse()
    }

    @Test fun gapOfTenInchesIsExterior() {
        val walls = deriver.derive(listOf(rect("a", 0, 0, 120, 120), rect("b", 130, 0, 120, 120)))
        assertThat(walls.first { it.roomId == "a" && it.orientation == "E" }.exterior).isTrue()
    }

    @Test fun cornerTouchOnlyIsNotShared() {
        val walls = deriver.derive(listOf(rect("a", 0, 0, 120, 120), rect("b", 120, 120, 120, 120)))
        assertThat(walls.all { it.exterior }).isTrue()
    }

    @Test fun partialSharedEdgeCountsForLongerWall() {
        val walls = deriver.derive(listOf(rect("a", 0, 0, 120, 240), rect("b", 120, 60, 120, 60)))
        assertThat(walls.first { it.roomId == "a" && it.orientation == "E" }.exterior).isFalse()
    }

    @Test fun diagonalEdgesGetRunningIndexAndHypotenuseLength() {
        val tri = PlanRoom("t", "t", "OTHER", listOf(Pt(0, 0), Pt(120, 0), Pt(0, 90)))
        val walls = deriver.derive(listOf(tri))
        assertThat(walls.map { it.orientation }).containsExactly("N", "D1", "W").inOrder()
        assertThat(walls[1].lengthIn).isEqualTo(150)
    }

    @Test fun rectangleBesideLShapeSharesOnlyTheTouchingWalls() {
        val l = PlanRoom("l", "l", "OTHER", listOf(Pt(0, 0), Pt(120, 0), Pt(120, 60), Pt(60, 60), Pt(60, 120), Pt(0, 120)))
        val r = rect("r", 120, 0, 100, 60)
        val walls = deriver.derive(listOf(l, r))
        assertThat(walls.single { it.roomId == "l" && it.edgeIndex == 1 }.exterior).isFalse()
        assertThat(walls.single { it.roomId == "r" && it.edgeIndex == 3 }.exterior).isFalse()
        assertThat(walls.single { it.roomId == "l" && it.edgeIndex == 0 }.exterior).isTrue()
        assertThat(walls.single { it.roomId == "r" && it.edgeIndex == 1 }.exterior).isTrue()
    }

    @Test fun zeroLengthEdgeIsNotLabelledAsAWallSide() {
        val odd = PlanRoom("z", "z", "OTHER", listOf(Pt(0, 0), Pt(0, 0), Pt(100, 0), Pt(100, 100)))
        val walls = deriver.derive(listOf(odd))
        assertThat(walls[0].lengthIn).isEqualTo(0)
        assertThat(walls[0].orientation).isEqualTo("D1")
    }

    @Test fun lShapeHasSixWalls() {
        val l = PlanRoom("l", "l", "OTHER", listOf(Pt(0, 0), Pt(120, 0), Pt(120, 60), Pt(60, 60), Pt(60, 120), Pt(0, 120)))
        assertThat(deriver.derive(listOf(l))).hasSize(6)
    }
}

// AC-C11
class PlanValidatorTest {
    private val v = PlanValidator()

    @Test fun overlappingRoomsAreFlagged() {
        val issues = v.validate(listOf(rect("a", 0, 0, 120, 120), rect("b", 60, 60, 120, 120)))
        assertThat(issues.map { it.kind }).containsExactly(PlanIssueKind.OVERLAP)
        assertThat(issues.single().roomIds).containsExactly("a", "b")
    }

    @Test fun touchingRoomsAreNotOverlap() {
        assertThat(v.validate(listOf(rect("a", 0, 0, 120, 120), rect("b", 120, 0, 120, 120)))).isEmpty()
        assertThat(v.validate(listOf(rect("a", 0, 0, 120, 120), rect("b", 120, 120, 120, 120)))).isEmpty()
    }

    @Test fun containedAndIdenticalRoomsOverlap() {
        assertThat(v.overlaps(rect("a", 0, 0, 240, 240), rect("b", 60, 60, 48, 48))).isTrue()
        assertThat(v.overlaps(rect("a", 0, 0, 120, 120), rect("b", 0, 0, 120, 120))).isTrue()
    }

    @Test fun crossShapedOverlapIsDetected() {
        assertThat(v.overlaps(rect("a", 0, 60, 240, 60), rect("b", 90, 0, 60, 180))).isTrue()
    }

    @Test fun notchOfLShapeDoesNotOverlapRoomInTheNotch() {
        val l = PlanRoom("l", "l", "OTHER", listOf(Pt(0, 0), Pt(120, 0), Pt(120, 60), Pt(60, 60), Pt(60, 120), Pt(0, 120)))
        val inNotch = rect("n", 70, 70, 48, 48)
        assertThat(v.overlaps(l, inNotch)).isFalse()
        assertThat(v.overlaps(l, rect("m", 10, 70, 40, 40))).isTrue()
    }

    @Test fun roomOverlappingOneArmOfAConcaveShapeIsFlagged() {
        val u = PlanRoom("u", "u", "OTHER", listOf(Pt(0, 0), Pt(180, 0), Pt(180, 120), Pt(120, 120), Pt(120, 60), Pt(60, 60), Pt(60, 120), Pt(0, 120)))
        assertThat(v.overlaps(u, rect("arm", 130, 80, 40, 60))).isTrue()
        assertThat(v.overlaps(u, rect("gap", 66, 66, 48, 48))).isFalse()
        assertThat(v.overlaps(u, rect("below", 0, 120, 180, 60))).isFalse()
    }

    @Test fun bowTieOutlineIsNotSimple() {
        val bow = PlanRoom("b", "b", "OTHER", listOf(Pt(0, 0), Pt(100, 100), Pt(100, 0), Pt(0, 100)))
        assertThat(bow.isSimple()).isFalse()
    }

    @Test fun smallRoomsAreFlagged() {
        val issues = v.validate(listOf(rect("a", 0, 0, 23, 120), rect("b", 200, 0, 24, 24)))
        assertThat(issues.map { it.kind to it.roomIds }).containsExactly(PlanIssueKind.TOO_SMALL to listOf("a"))
    }

    @Test fun zeroLengthEdgesDoNotCrash() {
        val odd = PlanRoom("z", "z", "OTHER", listOf(Pt(0, 0), Pt(0, 0), Pt(100, 0), Pt(100, 100)))
        v.validate(listOf(odd, rect("a", 0, 0, 120, 120)))
        WallDeriver().derive(listOf(odd, rect("a", 0, 0, 120, 120)))
    }
}

class GridSnapperTest {
    private val s = GridSnapper()

    @Test fun snapsToSixInches() {
        assertThat(s.snap(8)).isEqualTo(6)
        assertThat(s.snap(9)).isEqualTo(12)
        assertThat(s.snap(Pt(14, -4))).isEqualTo(Pt(12, -6))
    }

    @Test fun moveSnapsToNeighborEdge() {
        val room = rect("a", 0, 0, 120, 120)
        val neighbor = rect("b", 250, 0, 100, 120)
        // East side would land at 248, within 6 in of the neighbor's west side at 250.
        val near = s.snapMove(room, 128, 0, listOf(neighbor))
        assertThat(near.first).isEqualTo(130) // east side lands exactly on 250
        val far = s.snapMove(room, 61, 0, listOf(neighbor))
        assertThat(far.first).isEqualTo(60)
    }

    @Test fun moveFallsBackToGrid() {
        val room = rect("a", 5, 5, 120, 120)
        // minX 5 + 7 = 12 is on the grid, so dx stays 7; minY 5 + 8 = 13 snaps to 12, so dy is 7.
        assertThat(s.snapMove(room, 7, 8, emptyList())).isEqualTo(7 to 7)
    }

    @Test fun coordinateSnapsToNeighborOtherwiseGrid() {
        assertThat(s.snapCoordinate(247, listOf(250))).isEqualTo(250)
        assertThat(s.snapCoordinate(200, listOf(250))).isEqualTo(198)
    }
}

class UndoStackTest {
    @Test fun undoRedoRoundTrip() {
        val u = UndoStack(0)
        u.push(1); u.push(2)
        assertThat(u.undo()).isEqualTo(1)
        assertThat(u.undo()).isEqualTo(0)
        assertThat(u.canUndo).isFalse()
        assertThat(u.redo()).isEqualTo(1)
        u.push(9)
        assertThat(u.canRedo).isFalse()
    }

    @Test fun holdsAtLeastFiftySteps() {
        val u = UndoStack(0)
        for (i in 1..60) u.push(i)
        var steps = 0
        while (u.canUndo) { u.undo(); steps++ }
        assertThat(steps).isEqualTo(60)
    }

    @Test fun capacityDropsOldest() {
        val u = UndoStack(0, capacity = 3)
        for (i in 1..10) u.push(i)
        var steps = 0
        while (u.canUndo) { u.undo(); steps++ }
        assertThat(steps).isEqualTo(3)
        assertThat(u.current).isEqualTo(7)
    }

    @Test fun duplicateStatesIgnored() {
        val u = UndoStack(0); u.push(0)
        assertThat(u.canUndo).isFalse()
    }
}

class PlanModelTest {
    @Test fun cornerEditing() {
        val r = rect("a", 0, 0, 120, 120)
        val withCorner = r.withCornerOnEdge(0, Pt(60, 0))
        assertThat(withCorner.points).hasSize(5)
        assertThat(withCorner.withoutCorner(1).points).hasSize(4)
        val tri = PlanRoom("t", "t", "OTHER", listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10)))
        assertThat(tri.withoutCorner(0).points).hasSize(3)
    }

    @Test fun resizeEdgeMovesOneSide() {
        val r = rect("a", 0, 0, 120, 120).resizedEdge(1, 180)
        assertThat(r.widthIn).isEqualTo(180)
        assertThat(r.minX).isEqualTo(0)
        assertThat(r.areaSqIn()).isEqualTo(180L * 120)
    }

    @Test fun moveAndRemove() {
        val m = PlanModel().add(rect("a", 0, 0, 10, 10)).add(rect("b", 20, 0, 10, 10))
        assertThat(checkNotNull(m.update(checkNotNull(m.room("a")).moved(5, 5)).room("a")).minX).isEqualTo(5)
        assertThat(m.remove("a").rooms.map { it.id }).containsExactly("b")
    }
}
