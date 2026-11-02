package com.kurtz.homerecord.housemodel.walls

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C18: the outline screen shows the wall's length × height as a dashed guide and drags the nearest corner handle.
class OutlineGuideTest {
    @Test
    @AcceptanceCriterion("AC-C18")
    fun theGuideHasTheWallsShapeCenteredInThePhoto() {
        // A 14' × 8' wall in a 4:3 landscape photo: width-bound, 80% of the photo across.
        val g = checkNotNull(OutlineGuide.guide(4000, 3000, 168, 96))
        val wPx = (g.right - g.left) * 4000
        val hPx = (g.bottom - g.top) * 3000
        assertThat(wPx / hPx).isWithin(0.001f).of(168f / 96f)
        assertThat(wPx).isWithin(0.5f).of(3200f)
        assertThat(g.left).isWithin(0.0001f).of(1f - g.right)
        assertThat(g.top).isWithin(0.0001f).of(1f - g.bottom)
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun aTallWallInAPortraitPhotoIsBoundByHeight() {
        // A 3' × 8' wall beside a door in a 3:4 portrait photo.
        val g = checkNotNull(OutlineGuide.guide(3000, 4000, 36, 96))
        val hPx = (g.bottom - g.top) * 4000
        val wPx = (g.right - g.left) * 3000
        assertThat(hPx).isWithin(0.5f).of(3200f)
        assertThat(wPx / hPx).isWithin(0.001f).of(36f / 96f)
    }

    @Test
    fun noGuideWithoutASize() {
        assertThat(OutlineGuide.guide(0, 3000, 168, 96)).isNull()
        assertThat(OutlineGuide.guide(4000, 3000, 168, 0)).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun aTouchPicksTheNearestHandleWithinReach() {
        val outline = WallOutline.Initial
        // On a 1000 × 750 photo the top-right handle sits at (900, 75).
        assertThat(OutlineGuide.cornerAt(outline, 890f, 90f, 1000f, 750f, 60f)).isEqualTo(1)
        assertThat(OutlineGuide.cornerAt(outline, 110f, 660f, 1000f, 750f, 60f)).isEqualTo(3)
        assertThat(OutlineGuide.cornerAt(outline, 500f, 375f, 1000f, 750f, 60f)).isNull()
    }

    @Test
    fun theLoupeSitsAwayFromTheFinger() {
        assertThat(OutlineGuide.loupeOnRight(PhotoPoint(0.1f, 0.1f))).isTrue()
        assertThat(OutlineGuide.loupeOnRight(PhotoPoint(0.9f, 0.1f))).isFalse()
    }

    @Test
    fun theTextureSitsBesideThePhoto() {
        assertThat(AndroidWallTextureMaker.texturePathFor("/data/files/media/abc.jpg")).isEqualTo("/data/files/media/abc-wall.jpg")
    }
}
