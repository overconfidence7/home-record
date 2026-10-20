package com.kurtz.homerecord.housemodel.blueprint

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test

class BlueprintTest {
    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 120, 120)
    private val lShape = PlanRoom("l", "Living", "LIVING_ROOM", listOf(Pt(120, 0), Pt(300, 0), Pt(300, 60), Pt(200, 60), Pt(200, 180), Pt(120, 180)))

    @Test fun layerSetRoundTrips() {
        val set = LayerSet().toggled(BlueprintLayer.PLUMBING).toggled(BlueprintLayer.ITEMS)
        assertThat(LayerSet.decode(set.encode())).isEqualTo(set)
        assertThat(set.toggled(BlueprintLayer.PLUMBING)).isEqualTo(LayerSet(setOf(BlueprintLayer.ITEMS)))
    }

    @Test fun layerDecodeIgnoresJunk() {
        assertThat(LayerSet.decode(null)).isEqualTo(LayerSet())
        assertThat(LayerSet.decode("gas,bogus, hvac")).isEqualTo(LayerSet(setOf(BlueprintLayer.GAS, BlueprintLayer.HVAC)))
    }

    @Test fun fitCentersAndKeepsMargin() {
        val view = BlueprintView.fit(listOf(kitchen), 400f, 300f, 20f)
        assertThat(view.scale).isWithin(0.001f).of(260f / 120f)
        assertThat(view.x(0)).isAtLeast(20f)
        assertThat(view.x(120)).isAtMost(380f)
        assertThat(view.y(0)).isAtLeast(20f)
        assertThat(view.y(120)).isAtMost(280f)
        assertThat(view.planX(view.x(60))).isWithin(0.01f).of(60f)
    }

    @Test fun fitEmptyIsIdentity() {
        assertThat(BlueprintView.fit(emptyList(), 100f, 100f, 10f)).isEqualTo(BlueprintView(1f, 0f, 0f))
    }

    @Test fun hitTestFindsRoomsAndRespectsConcaveNotch() {
        val rooms = listOf(kitchen, lShape)
        assertThat(RoomHitTester.roomAt(rooms, 60f, 60f)?.id).isEqualTo("k")
        assertThat(RoomHitTester.roomAt(rooms, 250f, 30f)?.id).isEqualTo("l")
        assertThat(RoomHitTester.roomAt(rooms, 250f, 120f)).isNull() // notch of the L
        assertThat(RoomHitTester.roomAt(rooms, 160f, 150f)?.id).isEqualTo("l")
    }

    @Test fun smallestRoomWinsWhereRoomsOverlap() {
        val closet = PlanRoom.rectangle("c", "Closet", "CLOSET", 10, 10, 30, 30)
        assertThat(RoomHitTester.roomAt(listOf(kitchen, closet), 20f, 20f)?.id).isEqualTo("c")
    }

    @Test fun labelAnchorStaysInsideConcaveRooms() {
        val (x, y) = labelAnchor(lShape)
        assertThat(RoomHitTester.contains(lShape, x, y)).isTrue()
        val (kx, ky) = labelAnchor(kitchen)
        assertThat(kx).isWithin(0.5f).of(60f)
        assertThat(ky).isWithin(0.5f).of(60f)
    }
}
