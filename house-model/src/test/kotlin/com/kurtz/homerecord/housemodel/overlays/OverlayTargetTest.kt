package com.kurtz.homerecord.housemodel.overlays

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Test

class OverlayTargetTest {
    private fun overlay(roomId: String?, wallId: String?, stage: WallStage = WallStage.ROUGH_IN) = Overlay(
        id = "o1", homeId = "h", roomId = roomId, wallId = wallId, kind = OverlayKind.WATER_COLD, label = "Sink cold supply",
        note = null, depth = OverlayDepth.BEHIND_DRYWALL, points = listOf(OverlayPoint(0.2f, 0.3f)), stage = stage,
        photoPath = null, itemId = null, updatedAt = 0,
    )

    @Test @AcceptanceCriterion("AC-C28")
    fun aWallOverlayOpensItsWallAtItsStageWithItPicked() {
        assertThat(OverlayTarget.of(overlay("r1", "w1"))).isEqualTo(OverlayTarget.Wall("r1", "w1", "ROUGH_IN", "o1"))
    }

    @Test @AcceptanceCriterion("AC-C28")
    fun aPlanOverlayOpensThePlanEditorWithItPicked() {
        assertThat(OverlayTarget.of(overlay("r1", null))).isEqualTo(OverlayTarget.Plan("o1"))
        assertThat(OverlayTarget.of(overlay(null, null))).isEqualTo(OverlayTarget.Plan("o1"))
    }
}
