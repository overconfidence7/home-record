package com.kurtz.homerecord.fieldlog.jobs

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.RoomUsage
import org.junit.Test

// AC-F48, ui-standards rule 16 (cr:fl-jobedit-room-delete): only an unplaced, empty room comes off FL-JobEdit without a confirm.
class RoomRemovalTest {
    private fun room(polygon: String = "[]") = RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", polygonJson = polygon, createdAt = 1, updatedAt = 1)
    private val none = RoomUsage(0, 0, 0)

    @Test fun aRoomNotSavedYetGoesAtOnce() {
        assertThat(RoomRemoval.of("Kitchen", null, null)).isNull()
    }

    @Test fun anUnplacedEmptyRoomGoesAtOnce() {
        assertThat(RoomRemoval.of("Kitchen", room(), none)).isNull()
        assertThat(RoomRemoval.of("Kitchen", room(" [ ] "), none)).isNull()
    }

    @Test fun aRoomOnThePlanAsksFirst() {
        val r = RoomRemoval.of("Kitchen", room("""[{"x":0,"y":0},{"x":144,"y":0},{"x":144,"y":120}]"""), none)
        assertThat(r).isEqualTo(RoomRemoval("Kitchen", placed = true, photos = 0, items = 0, tasks = 0))
    }

    @Test fun anUnplacedRoomWithPhotosItemsOrTasksAsksAndCountsThem() {
        assertThat(RoomRemoval.of("Kitchen", room(), RoomUsage(photos = 3, items = 0, tasks = 0))).isEqualTo(RoomRemoval("Kitchen", false, 3, 0, 0))
        assertThat(RoomRemoval.of("Kitchen", room(), RoomUsage(0, 1, 0))?.items).isEqualTo(1)
        assertThat(RoomRemoval.of("Kitchen", room(), RoomUsage(0, 0, 2))?.tasks).isEqualTo(2)
    }
}
