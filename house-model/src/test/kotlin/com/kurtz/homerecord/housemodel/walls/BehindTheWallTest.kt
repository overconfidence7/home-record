package com.kurtz.homerecord.housemodel.walls

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F49: the behind-the-wall capture writes Rough-in first; a second capture on the same wall after drywall writes Finished.
class BehindTheWallTest {
    @Test
    @AcceptanceCriterion("AC-F49")
    fun aWallWithNoPhotosIsCapturedAtRoughIn() {
        assertThat(BehindTheWall.captureStage(emptySet())).isEqualTo(WallStage.ROUGH_IN)
    }

    @Test
    @AcceptanceCriterion("AC-F49")
    fun theSecondCaptureAfterRoughInIsTheFinishedPhoto() {
        assertThat(BehindTheWall.captureStage(setOf(WallStage.ROUGH_IN))).isEqualTo(WallStage.FINISHED)
        assertThat(BehindTheWall.captureStage(setOf(WallStage.ROUGH_IN, WallStage.FINISHED))).isEqualTo(WallStage.FINISHED)
    }

    @Test
    @AcceptanceCriterion("AC-F49")
    fun aHomeownersFinishedPhotoAloneStillLeavesRoughInToTake() {
        // The homeowner photographed the finished wall before the remodel; the crew's open wall is still Rough-in.
        assertThat(BehindTheWall.captureStage(setOf(WallStage.FINISHED))).isEqualTo(WallStage.ROUGH_IN)
        assertThat(BehindTheWall.captureStage(setOf(WallStage.FRAMING, WallStage.INSULATION))).isEqualTo(WallStage.ROUGH_IN)
    }

    @Test
    @AcceptanceCriterion("AC-F49")
    fun progressCountsWallsAtEachStage() {
        val walls = listOf(
            RoomWall("w1", WallSide.NORTH, null, 168, 96, mapOf(WallStage.ROUGH_IN to "a.jpg", WallStage.FINISHED to "b.jpg")),
            RoomWall("w2", WallSide.EAST, null, 144, 96, mapOf(WallStage.ROUGH_IN to "c.jpg")),
            RoomWall("w3", WallSide.SOUTH, null, 168, 96),
            RoomWall("w4", WallSide.WEST, null, 144, 96, mapOf(WallStage.FINISHED to "d.jpg")),
        )
        val p = BehindTheWall.progress(walls)
        assertThat(p).isEqualTo(BehindTheWall.Progress(total = 4, roughIn = 2, finished = 2))
        assertThat(p.complete).isFalse()
        assertThat(BehindTheWall.progress(walls.take(2)).complete).isTrue()
        assertThat(BehindTheWall.progress(emptyList()).complete).isFalse()
    }
}
