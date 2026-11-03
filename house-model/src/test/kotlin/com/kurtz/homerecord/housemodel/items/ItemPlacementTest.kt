package com.kurtz.homerecord.housemodel.items

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.blueprint.RoomHitTester
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test

// AC-C30 position: a tap on the plan gives the item's room and spot.
class ItemPlacementTest {
    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val living = PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 180, 144)
    private val pantry = PlanRoom.rectangle("p", "Pantry", "OTHER", 24, 24, 48, 48)

    @AcceptanceCriterion("AC-C30")
    @Test fun aTapInsideARoomPutsTheItemThereAtThatSpot() {
        assertThat(ItemPlacement.place(listOf(kitchen, living), 60, 70)).isEqualTo(ItemPlace("k", 60, 70))
        assertThat(ItemPlacement.place(listOf(kitchen, living), 200, 30)).isEqualTo(ItemPlace("l", 200, 30))
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun theSmallestRoomUnderTheTapWins() {
        assertThat(ItemPlacement.place(listOf(kitchen, pantry), 40, 40)?.roomId).isEqualTo("p")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun aTapOutsideTheHouseLandsJustInsideTheNearestRoom() {
        // 20" above the kitchen's top wall: straight down to 6" inside it.
        assertThat(ItemPlacement.place(listOf(kitchen, living), 80, -20)).isEqualTo(ItemPlace("k", 80, ItemPlacement.INSET_IN))
        // Past the living room's right wall.
        assertThat(ItemPlacement.place(listOf(kitchen, living), 400, 72)).isEqualTo(ItemPlace("l", 348 - ItemPlacement.INSET_IN, 72))
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun aTapOffACornerStaysClearOfBothWalls() {
        val at = checkNotNull(ItemPlacement.place(listOf(kitchen), -30, -30))
        assertThat(at.roomId).isEqualTo("k")
        assertThat(at.xIn).isAtLeast(ItemPlacement.INSET_IN)
        assertThat(at.yIn).isAtLeast(ItemPlacement.INSET_IN)
        assertThat(at.xIn).isAtMost(ItemPlacement.INSET_IN + 1)
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun anLShapedRoomKeepsTheSpotInsideItsArm() {
        // L: 0..240 wide at the top, 0..96 wide below y 96; a tap in the notch goes to the nearest wall, inside the L.
        val l = PlanRoom("L", "Family room", points = listOf(Pt(0, 0), Pt(240, 0), Pt(240, 96), Pt(96, 96), Pt(96, 192), Pt(0, 192)))
        val at = checkNotNull(ItemPlacement.place(listOf(l), 200, 110))
        assertThat(RoomHitTester.contains(l, at.xIn.toFloat(), at.yIn.toFloat())).isTrue()
        assertThat(at).isEqualTo(ItemPlace("L", 200, 96 - ItemPlacement.INSET_IN))
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun noDrawnRoomsGivesNoSpot() {
        assertThat(ItemPlacement.place(emptyList(), 10, 10)).isNull()
        assertThat(ItemPlacement.place(listOf(PlanRoom("u", "Unplaced", points = emptyList())), 10, 10)).isNull()
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun aSpotShowsOnlyOnTheFloorOfItsRoom() {
        assertThat(ItemPlacement.onFloor(listOf(kitchen), "k", 10, 20)).isEqualTo(ItemPlace("k", 10, 20))
        assertThat(ItemPlacement.onFloor(listOf(living), "k", 10, 20)).isNull()
        assertThat(ItemPlacement.onFloor(listOf(kitchen), "k", null, 20)).isNull()
    }
}
