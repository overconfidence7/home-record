package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C20: the 3D view names its rooms, as the blueprint does ([judge:hm-3d-rooms-unlabeled])
class RoomLabelsTest {
    private fun room(id: String, name: String, x0: Float, z0: Float, x1: Float, z1: Float, floorY: Float = 0f) =
        SceneRoom(id, name, listOf(x0 to z0, x1 to z0, x1 to z1, x0 to z1), floorY, floorY + 2.4f)

    /** A camera looking down -z with x and y scaled into clip space by 1/10 and w = distance in front (z = -w). */
    private val toClip: (Float, Float, Float) -> FloatArray = { x, y, z -> floatArrayOf(x, y, 0f, -z / 10f) }

    @AcceptanceCriterion("AC-C20")
    @Test fun anchorsAtTheFootprintMiddleJustAboveTheFloor() {
        val a = RoomLabels.anchor(room("k", "Kitchen", 0f, -4f, 4f, -8f, floorY = 3f))
        assertThat(a).isEqualTo(ScenePoint(2f, 3f + RoomLabels.LIFT_M, -6f))
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun placesNamesInPixelsWithYDownAndDropsRoomsOffViewOrBehind() {
        val rooms = listOf(
            room("k", "Kitchen", -1f, -9f, 1f, -11f), // straight ahead, 10 m: the middle of the view
            room("b", "  ", -1f, -9f, 1f, -11f), // no name, no label
            room("g", "Garage", -1f, 9f, 1f, 11f), // behind the camera
            room("o", "Office", 30f, -9f, 32f, -11f), // far off to the right
        )
        val labels = RoomLabels.place(rooms, 1000, 2000, toClip)
        assertThat(labels.map { it.id }).containsExactly("k")
        val k = labels.single()
        assertThat(k.name).isEqualTo("Kitchen")
        assertThat(k.x).isWithin(0.5f).of(500f)
        // lifted 0.3 m above the floor: slightly above the middle (y grows downward on screen)
        assertThat(k.y).isLessThan(1000f)
        assertThat(RoomLabels.place(rooms, 0, 0, toClip)).isEmpty()
    }
}
