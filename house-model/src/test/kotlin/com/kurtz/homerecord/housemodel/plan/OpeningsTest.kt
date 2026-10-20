package com.kurtz.homerecord.housemodel.plan

import com.kurtz.homerecord.core.testing.AcceptanceCriterion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningsTest {
    private val room = PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 144, 120)

    private fun editor() = PlanEditor(listOf(room))

    @AcceptanceCriterion("AC-C9")
    @Test fun doorIsCenteredOnTheEdge() {
        val e = editor()
        e.addOpening("r1", "d1", 0, OpeningKind.DOOR)
        val o = e.rooms.single().openings.single()
        assertEquals(144 / 2 - 16, o.offsetIn)
        assertEquals(32, o.widthIn)
        assertEquals(OpeningKind.DOOR, o.kind)
    }

    @Test fun openingLandsWhereTapped() {
        val e = editor()
        e.addOpening("r1", "w1", 0, OpeningKind.WINDOW, Pt(30, 0))
        assertEquals(30 - 18, e.rooms.single().openings.single().offsetIn)
    }

    @Test fun dragSlidesAlongTheWallAndStaysOnIt() {
        val e = editor()
        e.addOpening("r1", "d1", 0, OpeningKind.DOOR)
        e.dragOpening("r1", "d1", Pt(500, 40))
        e.commit()
        val o = e.rooms.single().openings.single()
        assertEquals(0, o.edgeIndex)
        assertEquals(144 - 32, o.offsetIn)
        e.dragOpening("r1", "d1", Pt(-50, 0))
        e.commit()
        assertEquals(0, e.rooms.single().openings.single().offsetIn)
    }

    @Test fun widthIsClampedToTheWall() {
        val e = editor()
        e.addOpening("r1", "d1", 0, OpeningKind.DOOR)
        e.setOpeningWidth("r1", "d1", 60)
        assertEquals(60, e.rooms.single().openings.single().widthIn)
        e.setOpeningWidth("r1", "d1", 500)
        assertEquals(60, e.rooms.single().openings.single().widthIn)
        e.setOpeningWidth("r1", "d1", 4)
        assertEquals(60, e.rooms.single().openings.single().widthIn)
    }

    @Test fun shrinkingTheWallPullsTheOpeningIn() {
        val e = editor()
        e.addOpening("r1", "d1", 0, OpeningKind.DOOR, Pt(140, 0))
        e.setSize("r1", 60, 120)
        val o = e.rooms.single().openings.single()
        assertTrue(o.offsetIn + o.widthIn <= 60)
    }

    @Test fun undoRemovesTheOpening() {
        val e = editor()
        e.addOpening("r1", "d1", 0, OpeningKind.DOOR)
        e.undo()
        assertTrue(e.rooms.single().openings.isEmpty())
        e.redo()
        assertEquals(1, e.rooms.single().openings.size)
    }

    @Test fun tooShortWallTakesNoOpening() {
        val tiny = PlanRoom.rectangle("t", "Closet", "CLOSET", 0, 0, 144, 8)
        val e = PlanEditor(listOf(tiny))
        assertNull(e.addOpening("t", "d", 1, OpeningKind.DOOR))
        assertNotNull(e.addOpening("t", "d", 0, OpeningKind.DOOR))
    }

    @Test fun addingACornerKeepsOpeningsOnTheirWall() {
        val withDoors = room
            .withOpening(PlanOpening("a", 0, OpeningKind.DOOR, 10, 32, 80))
            .withOpening(PlanOpening("b", 0, OpeningKind.WINDOW, 100, 36, 48))
            .withOpening(PlanOpening("c", 1, OpeningKind.WINDOW, 20, 36, 48))
        val split = withDoors.withCornerOnEdge(0, Pt(72, 0))
        val byId = split.openings.associateBy { it.id }
        assertEquals(0, byId.getValue("a").edgeIndex)
        assertEquals(1, byId.getValue("b").edgeIndex)
        assertEquals(28, byId.getValue("b").offsetIn)
        assertEquals(2, byId.getValue("c").edgeIndex)
    }

    @Test fun deletingACornerDropsTheWallsItMerges() {
        val l = PlanRoom("l", "L", "OTHER", listOf(Pt(0, 0), Pt(144, 0), Pt(144, 60), Pt(72, 60), Pt(72, 120), Pt(0, 120)))
            .withOpening(PlanOpening("a", 0, OpeningKind.DOOR, 10, 32, 80))
            .withOpening(PlanOpening("b", 3, OpeningKind.WINDOW, 10, 36, 48))
            .withOpening(PlanOpening("c", 5, OpeningKind.WINDOW, 10, 36, 48))
        val next = l.withoutCorner(3)
        assertEquals(setOf("a", "c"), next.openings.map { it.id }.toSet())
        assertEquals(4, next.openings.first { it.id == "c" }.edgeIndex)
    }

    @Test fun stairsSitInsideTheRoomAndMoveWithIt() {
        val e = editor()
        assertTrue(e.addStair("r1", "s1", "floor-2"))
        val stair = e.rooms.single().stairs.single()
        assertEquals("floor-2", stair.toFloorId)
        assertTrue(stair.points.all { it.x in 0..144 && it.y in 0..120 })
        e.dragMove("r1", 24, 12)
        e.commit()
        val moved = e.rooms.single().stairs.single()
        assertEquals(stair.points.map { Pt(it.x + 24, it.y + 12) }, moved.points)
    }

    @AcceptanceCriterion("AC-C9")
    @Test fun stairsLinkAndDelete() {
        val e = editor()
        e.addStair("r1", "s1", null)
        e.linkStair("r1", "s1", "floor-0")
        assertEquals("floor-0", e.rooms.single().stairs.single().toFloorId)
        e.deleteStair("r1", "s1")
        assertFalse(e.rooms.single().stairs.isNotEmpty())
    }
}
