package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import org.junit.Test
import kotlin.math.asin

// [judge:3d-default-framing]: the floor view opens with the whole floor on screen with a margin, tall view or wide.
class SceneFramingTest {
    @AcceptanceCriterion("AC-C20")
    @Test fun aOneRoomFloorFitsATallPhoneViewWithAMargin() {
        // Field Log F10: one 12' by 10' kitchen.
        val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 144, 120)
        val bounds = FloorScene.build(listOf(kitchen), 96, emptyMap()).focus
        val aspect = 1080f / 1180f
        val d = SceneFraming.fitDistance(bounds.radius, aspect)
        // The sphere around the floor takes less of the narrower (horizontal) field than the whole of it.
        val needed = asin(bounds.radius.coerceAtLeast(2f) / d)
        assertThat(needed.toDouble()).isLessThan(SceneFraming.halfHorizontalFov(SceneFraming.FLOOR_FOCAL_MM, aspect) / 1.1)
    }

    @Test fun aWideViewIsLimitedByItsHeight() {
        val tall = SceneFraming.fitDistance(5f, 0.5f)
        val wide = SceneFraming.fitDistance(5f, 2f)
        assertThat(wide).isLessThan(tall)
        // Wide: the vertical field decides.
        assertThat(wide).isWithin(0.01f).of((5 * 1.15 / kotlin.math.sin(SceneFraming.halfVerticalFov(28.0))).toFloat())
    }

    @Test fun anUnmeasuredViewIsTreatedAsSquare() {
        assertThat(SceneFraming.fitDistance(3f, 0f)).isEqualTo(SceneFraming.fitDistance(3f, 1f))
    }

    @Test fun theRoomLensIsWiderThanTheFloorLens() {
        assertThat(SceneFraming.halfVerticalFov(SceneFraming.ROOM_FOCAL_MM)).isGreaterThan(SceneFraming.halfVerticalFov(SceneFraming.FLOOR_FOCAL_MM))
    }
}
