package com.kurtz.homerecord.housemodel.overlays

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Test

class PlanOverlayPlacerTest {
    private fun rect(id: String, x: Int, y: Int, w: Int, h: Int) =
        PlanRoom(id, id, points = listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h)))

    private val kitchen = rect("kitchen", 0, 0, 168, 144)
    private val pantry = rect("pantry", 120, 0, 48, 48) // inside the kitchen's bounds: the smaller room wins
    private val living = rect("living", 168, 0, 192, 144)

    private fun overlay(id: String, roomId: String?, wallId: String? = null) = Overlay(
        id = id, homeId = "h", roomId = roomId, wallId = wallId, kind = OverlayKind.GAS, label = "", note = null,
        depth = OverlayDepth.IN_SLAB, points = listOf(OverlayPoint(10f, 10f)), stage = WallStage.ROUGH_IN,
        photoPath = null, itemId = null, updatedAt = 0,
    )

    @Test @AcceptanceCriterion("AC-C25")
    fun aRunIsFiledUnderTheRoomItStartsIn() {
        val run = listOf(OverlayPoint(200f, 60f), OverlayPoint(40f, 60f))
        assertThat(PlanOverlayPlacer.roomFor(listOf(kitchen, living), run)).isEqualTo("living")
    }

    @Test @AcceptanceCriterion("AC-C25")
    fun aSpotInARoomInsideAnotherGoesToTheSmallerRoom() {
        assertThat(PlanOverlayPlacer.roomFor(listOf(kitchen, pantry), listOf(OverlayPoint(140f, 20f)))).isEqualTo("pantry")
    }

    @Test @AcceptanceCriterion("AC-C25")
    fun aSpotOutsideEveryRoomGoesToTheNearestWall() {
        // 30 in below the kitchen, 300 in from the living room's far corner.
        assertThat(PlanOverlayPlacer.roomFor(listOf(kitchen, living), listOf(OverlayPoint(60f, 174f)))).isEqualTo("kitchen")
        assertThat(PlanOverlayPlacer.roomFor(listOf(kitchen, living), listOf(OverlayPoint(400f, 20f)))).isEqualTo("living")
    }

    @Test
    fun noRoomsOrNoPointsFileNowhere() {
        assertThat(PlanOverlayPlacer.roomFor(emptyList(), listOf(OverlayPoint(1f, 1f)))).isNull()
        assertThat(PlanOverlayPlacer.roomFor(listOf(kitchen), emptyList())).isNull()
    }

    @Test @AcceptanceCriterion("AC-C25")
    fun aFloorShowsOnlyThePlanOverlaysOfItsRooms() {
        val all = listOf(
            overlay("gas", "kitchen"),
            overlay("wall-pipe", "kitchen", wallId = "w1"),
            overlay("upstairs", "bedroom"),
            overlay("loose", null),
            overlay("duct", "living"),
        )
        assertThat(PlanOverlayPlacer.onFloor(all, listOf(kitchen, living)).map { it.id }).containsExactly("gas", "duct").inOrder()
    }

    @Test
    fun distanceToWallsIsZeroOnAWallAndMeasuresStraightOut() {
        assertThat(PlanOverlayPlacer.distanceToWalls(kitchen, 84f, 0f)).isEqualTo(0f)
        assertThat(PlanOverlayPlacer.distanceToWalls(kitchen, 84f, -24f)).isEqualTo(24f)
        assertThat(PlanOverlayPlacer.distanceToWalls(kitchen, 84f, 20f)).isEqualTo(20f)
    }
}
