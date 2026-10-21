package com.kurtz.homerecord.housemodel.plan

import com.kurtz.homerecord.core.testing.AcceptanceCriterion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlanEditorTest {
    private fun editor(vararg rooms: PlanRoom) = PlanEditor(rooms.toList())
    private fun rect(id: String, l: Int, t: Int, w: Int, h: Int) = PlanRoom.rectangle(id, id, "OTHER", l, t, w, h)

    @AcceptanceCriterion("AC-C6")
    @Test fun secondRoomAddedAtTheSameSpotLandsBesideTheFirst() {
        val e = PlanEditor(emptyList())
        val a = e.addRoom("a", "Kitchen", RoomKind.KITCHEN, Pt(600, 400))
        val b = e.addRoom("b", "Living room", RoomKind.OTHER, Pt(600, 400))
        assertThat(PlanValidator().overlaps(a, b)).isFalse()
        assertThat(b.minX).isAtLeast(a.maxX)
    }

    @Test fun addRoomIsTwelveByTwelveFeetCenteredOnViewportAndSnapped() {
        val e = editor()
        val room = e.addRoom("a", "Kitchen", RoomKind.KITCHEN, Pt(600, 400))
        assertThat(room.widthIn).isEqualTo(144)
        assertThat(room.lengthIn).isEqualTo(144)
        assertThat(room.minX).isEqualTo(528)
        assertThat(room.minY).isEqualTo(330) // 400 - 72 = 328 snaps to the 6 in grid
        assertThat(room.kind).isEqualTo("KITCHEN")
    }

    @Test fun dragMoveSnapsAndCommitsOneUndoStep() {
        val e = editor(rect("a", 0, 0, 144, 144))
        e.dragMove("a", 20, 13)
        e.dragMove("a", 50, 31)
        assertThat(e.canUndo).isFalse()
        e.commit()
        assertThat(e.rooms.single().minX).isEqualTo(48)
        assertThat(e.rooms.single().minY).isEqualTo(30)
        e.undo()
        assertThat(e.rooms.single().minX).isEqualTo(0)
        assertThat(e.canUndo).isFalse()
        e.redo()
        assertThat(e.rooms.single().minX).isEqualTo(48)
    }

    @Test fun dragMoveSnapsOntoNeighborWall() {
        val e = editor(rect("a", 0, 0, 144, 144), rect("b", 300, 0, 100, 100))
        e.dragMove("b", -153, 0) // would land at x=147, 3 in from a's east wall at 144
        e.commit()
        assertThat(e.rooms.first { it.id == "b" }.minX).isEqualTo(144)
    }

    @Test fun dragEdgeResizesAndRefusesTooSmall() {
        val e = editor(rect("a", 0, 0, 144, 144))
        e.dragEdge("a", 1, 60) // east edge out
        e.commit()
        assertThat(e.rooms.single().widthIn).isEqualTo(204)
        e.dragEdge("a", 1, -400)
        e.commit()
        assertThat(e.rooms.single().widthIn).isEqualTo(204)
    }

    @Test fun setSizeKeepsTopLeftAndIgnoresPolygons() {
        val e = editor(rect("a", 24, 36, 144, 144))
        e.setSize("a", 180, 120)
        assertThat(e.rooms.single().minX).isEqualTo(24)
        assertThat(e.rooms.single().widthIn).isEqualTo(180)
        assertThat(e.rooms.single().lengthIn).isEqualTo(120)
        val l = PlanRoom("l", "L", "OTHER", listOf(Pt(0, 0), Pt(100, 0), Pt(100, 50), Pt(50, 50), Pt(50, 100), Pt(0, 100)))
        val e2 = editor(l)
        e2.setSize("l", 200, 200)
        assertThat(e2.rooms.single()).isEqualTo(l)
    }

    @Test fun renameKindDeleteAreUndoable() {
        val e = editor(rect("a", 0, 0, 144, 144))
        e.rename("a", "Den")
        e.setKind("a", RoomKind.OFFICE)
        e.delete("a")
        assertThat(e.rooms).isEmpty()
        e.undo()
        assertThat(e.rooms.single().kind).isEqualTo("OFFICE")
        e.undo()
        e.undo()
        assertThat(e.rooms.single().name).isEqualTo("a")
    }

    @AcceptanceCriterion("AC-C10")
    @Test fun holdsFiftyUndoSteps() {
        val e = editor(rect("a", 0, 0, 144, 144))
        repeat(60) { e.rename("a", "n$it") }
        repeat(60) { e.undo() }
        assertThat(e.rooms.single().name).isEqualTo("a")
    }

    @Test fun placeReplacesRoomWithSameId() {
        val e = editor()
        e.place(rect("g", 0, 0, 240, 240))
        e.place(rect("g", 10, 10, 240, 240))
        assertThat(e.rooms).hasSize(1)
    }

    @Test fun addCornerOnEdgeInsertsSnappedPointAndMakesAnLShapePossible() {
        val e = editor(rect("a", 0, 0, 144, 144))
        val index = e.addCorner("a", 0, Pt(100, 5))
        assertThat(index).isEqualTo(1)
        assertThat(e.rooms.single().points[1]).isEqualTo(Pt(102, 0))
        assertThat(e.rooms.single().points).hasSize(5)
        e.undo()
        assertThat(e.rooms.single().points).hasSize(4)
    }

    @Test fun addCornerNearAnExistingCornerIsIgnored() {
        val e = editor(rect("a", 0, 0, 144, 144))
        assertThat(e.addCorner("a", 0, Pt(2, 0))).isNull()
        assertThat(e.rooms.single().points).hasSize(4)
    }

    @AcceptanceCriterion("AC-C7")
    @Test fun dragCornerBuildsAnLShapeInOneUndoStep() {
        val e = editor(rect("a", 0, 0, 144, 144))
        e.addCorner("a", 1, Pt(144, 72)) // east edge midpoint becomes index 2
        e.dragCorner("a", 2, -70, 3)
        e.dragCorner("a", 2, -72, 0)
        e.commit()
        val room = e.rooms.single()
        assertThat(room.points[2]).isEqualTo(Pt(72, 72))
        assertThat(room.isSimple()).isTrue()
        e.undo()
        assertThat(e.rooms.single().points[2]).isEqualTo(Pt(144, 72))
    }

    @Test fun dragCornerAcrossTheOutlineIsRejected() {
        val e = editor(rect("a", 0, 0, 144, 144))
        e.dragCorner("a", 2, -194, -72) // swings the SE corner past the west wall, so two edges cross
        e.commit()
        assertThat(e.rooms.single().points[2]).isEqualTo(Pt(144, 144))
        assertThat(e.canUndo).isFalse()
    }

    @Test fun deleteCornerKeepsAtLeastThree() {
        val e = editor(rect("a", 0, 0, 144, 144))
        assertThat(e.deleteCorner("a", 3)).isTrue()
        assertThat(e.rooms.single().points).hasSize(3)
        assertThat(e.deleteCorner("a", 0)).isFalse()
        assertThat(e.rooms.single().points).hasSize(3)
    }
}
