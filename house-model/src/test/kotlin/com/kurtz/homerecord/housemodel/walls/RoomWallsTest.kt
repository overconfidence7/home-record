package com.kurtz.homerecord.housemodel.walls

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C17: the room page lists its walls by orientation and length, each with its photo per stage.
class RoomWallsTest {
    private fun wall(id: String, edge: Int, orientation: String, length: Int) =
        WallEntity(id = id, roomId = "living", edgeIndex = edge, orientation = orientation, lengthIn = length, heightIn = 96, createdAt = 1, updatedAt = 1)

    private fun photo(id: String, wallId: String, stage: WallStage, original: String, texture: String?, takenAt: Long) = WallPhotoEntity(
        id = id, wallId = wallId, stage = stage.name, originalPath = original, texturePath = texture,
        takenAt = takenAt, createdAt = takenAt, updatedAt = takenAt,
    )

    @Test
    @AcceptanceCriterion("AC-C17")
    fun wallsListInCompassOrderAndSidesWithTwoWallsAreNumbered() {
        // An L-shaped living room traced clockwise: two north-facing and two east-facing runs.
        val walls = listOf(
            wall("a", 0, "N", 240), wall("b", 1, "E", 96), wall("c", 2, "N", 120),
            wall("d", 3, "E", 96), wall("e", 4, "S", 120), wall("f", 5, "W", 192), wall("g", 6, "D1", 34),
        )
        val list = RoomWalls.build(walls.shuffled(java.util.Random(7)), emptyList())

        assertThat(list.map { it.id }).containsExactly("a", "c", "b", "d", "e", "f", "g").inOrder()
        assertThat(list.map { it.side to it.number }).containsExactly(
            WallSide.NORTH to 1, WallSide.NORTH to 2, WallSide.EAST to 1, WallSide.EAST to 2,
            WallSide.SOUTH to null, WallSide.WEST to null, WallSide.ANGLED to null,
        ).inOrder()
        assertThat(list.first().lengthIn).isEqualTo(240)
        assertThat(list.first().thumbnails).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun eachStageShowsItsNewestPhotoPreferringTheStraightenedCopy() {
        val walls = listOf(wall("a", 0, "N", 168))
        val photos = listOf(
            photo("p1", "a", WallStage.ROUGH_IN, "/m/r1.jpg", "/m/r1-tex.jpg", takenAt = 10),
            photo("p2", "a", WallStage.ROUGH_IN, "/m/r2.jpg", "/m/r2-tex.jpg", takenAt = 20),
            photo("p3", "a", WallStage.FINISHED, "/m/f.jpg", null, takenAt = 30),
            photo("p4", "other-wall", WallStage.FRAMING, "/m/x.jpg", null, takenAt = 40),
        )
        val wall = RoomWalls.build(walls, photos).single()

        assertThat(wall.thumbnails).containsExactly(WallStage.ROUGH_IN, "/m/r2-tex.jpg", WallStage.FINISHED, "/m/f.jpg")
    }

    @Test fun unknownOrientationCountsAsAngled() {
        assertThat(WallSide.of("D3")).isEqualTo(WallSide.ANGLED)
        assertThat(WallSide.of("")).isEqualTo(WallSide.ANGLED)
        assertThat(WallSide.of("W")).isEqualTo(WallSide.WEST)
    }
}
