package com.kurtz.homerecord.housemodel.walls

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C18: the outline starts with each handle inset 10%; AC-C17: stages read back by name.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WallOutlineTest {
    @Test
    @AcceptanceCriterion("AC-C18")
    fun handlesStartInsetTenPercent() {
        assertThat(WallOutline.Initial.corners).containsExactly(
            PhotoPoint(0.1f, 0.1f), PhotoPoint(0.9f, 0.1f), PhotoPoint(0.9f, 0.9f), PhotoPoint(0.1f, 0.9f),
        ).inOrder()
        assertThat(WallOutline.Initial.isUsable()).isTrue()
        assertThat(WallOutline.Initial.area()).isWithin(0.0001f).of(0.64f)
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun aDraggedHandleStaysInsideThePhoto() {
        val moved = WallOutline.Initial.moved(2, PhotoPoint(1.4f, 0.95f))
        assertThat(moved.bottomRight).isEqualTo(PhotoPoint(1f, 0.95f))
        assertThat(moved.topLeft).isEqualTo(WallOutline.Initial.topLeft)
        assertThat(WallOutline.Initial.moved(7, PhotoPoint(0f, 0f))).isEqualTo(WallOutline.Initial)
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun crossedTinyOrInsideOutOutlinesAreNotUsable() {
        val crossed = WallOutline.Initial.moved(1, PhotoPoint(0.9f, 0.9f)).moved(2, PhotoPoint(0.9f, 0.1f))
        assertThat(crossed.isUsable()).isFalse()
        val dented = WallOutline.Initial.moved(2, PhotoPoint(0.3f, 0.3f))
        assertThat(dented.isUsable()).isFalse()
        val tiny = WallOutline(PhotoPoint(0.5f, 0.5f), PhotoPoint(0.55f, 0.5f), PhotoPoint(0.55f, 0.55f), PhotoPoint(0.5f, 0.55f))
        assertThat(tiny.isUsable()).isFalse()
        // A real wall seen at an angle is fine.
        val leaning = WallOutline(PhotoPoint(0.22f, 0.16f), PhotoPoint(0.76f, 0.21f), PhotoPoint(0.9f, 0.85f), PhotoPoint(0.1f, 0.8f))
        assertThat(leaning.isUsable()).isTrue()
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun cornersRoundTripThroughJsonAndDamagedJsonReadsAsNone() {
        val outline = WallOutline(PhotoPoint(0.22f, 0.16f), PhotoPoint(0.76f, 0.21f), PhotoPoint(0.9f, 0.85f), PhotoPoint(0.1f, 0.8f))
        assertThat(WallOutline.decode(outline.encode())).isEqualTo(outline)
        assertThat(WallOutline.decode("[]")).isNull()
        assertThat(WallOutline.decode("not json")).isNull()
        assertThat(WallOutline.decode(null)).isNull()
        assertThat(WallOutline.decode("""[{"x":0,"y":0},{"x":1,"y":0},{"x":1,"y":1}]""")).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun stagesReadBackByNameAndEachAppHasItsDefault() {
        assertThat(WallStage.entries.map { it.name }).containsExactly("FRAMING", "ROUGH_IN", "INSULATION", "FINISHED").inOrder()
        assertThat(WallStage.fromStored("ROUGH_IN")).isEqualTo(WallStage.ROUGH_IN)
        assertThat(WallStage.fromStored("drywall")).isEqualTo(WallStage.FINISHED)
        assertThat(WallStage.HouseManualDefault).isEqualTo(WallStage.FINISHED)
        assertThat(WallStage.BehindTheWall).isEqualTo(WallStage.ROUGH_IN)
    }
}
