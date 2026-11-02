package com.kurtz.homerecord.housemodel.walls

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C27: overlay points are stored in wall space (0..1 by 0..1), so a mark drawn on the Rough-in photo stays at the
// same wall position on the Finished photo.
class WallSpaceMapperTest {
    // Rough-in shot from the left, close up: the wall's far end is smaller.
    private val roughIn = WallOutline(PhotoPoint(0.05f, 0.10f), PhotoPoint(0.85f, 0.25f), PhotoPoint(0.85f, 0.75f), PhotoPoint(0.05f, 0.92f))
    // Finished shot from the right and further back.
    private val finished = WallOutline(PhotoPoint(0.30f, 0.30f), PhotoPoint(0.92f, 0.18f), PhotoPoint(0.92f, 0.86f), PhotoPoint(0.30f, 0.72f))

    @Test
    @AcceptanceCriterion("AC-C27")
    fun theOutlineCornersAreTheWallCorners() {
        assertNear(WallSpaceMapper.wallToPhoto(roughIn, WallPoint(0f, 0f)), roughIn.topLeft)
        assertNear(WallSpaceMapper.wallToPhoto(roughIn, WallPoint(1f, 0f)), roughIn.topRight)
        assertNear(WallSpaceMapper.wallToPhoto(roughIn, WallPoint(1f, 1f)), roughIn.bottomRight)
        assertNear(WallSpaceMapper.wallToPhoto(roughIn, WallPoint(0f, 1f)), roughIn.bottomLeft)
    }

    @Test
    @AcceptanceCriterion("AC-C27")
    fun photoToWallUndoesWallToPhoto() {
        listOf(WallPoint(0.5f, 0.5f), WallPoint(0.12f, 0.8f), WallPoint(0.9f, 0.05f), WallPoint(0.33f, 0.66f)).forEach { p ->
            val back = WallSpaceMapper.photoToWall(roughIn, WallSpaceMapper.wallToPhoto(roughIn, p))
            assertThat(back).isNotNull()
            assertThat(back?.x ?: 0f).isWithin(1e-4f).of(p.x)
            assertThat(back?.y ?: 0f).isWithin(1e-4f).of(p.y)
        }
    }

    @Test
    @AcceptanceCriterion("AC-C27")
    fun perspectiveIsNotTreatedAsAStretch() {
        // The wall's middle sits right of the photo midpoint of its ends when the far end is smaller (true perspective).
        val middle = WallSpaceMapper.wallToPhoto(roughIn, WallPoint(0.5f, 0.5f))
        val naive = (roughIn.topLeft.x + roughIn.topRight.x) / 2f
        assertThat(middle.x).isGreaterThan(naive + 0.01f)
    }

    @Test
    @AcceptanceCriterion("AC-C27")
    fun aPipeDrawnOnRoughInStaysPutOnFinished() {
        // A supply line drawn 30% along the wall from 20% down to the floor on the Rough-in photo.
        val drawn = listOf(WallPoint(0.3f, 0.2f), WallPoint(0.3f, 1f)).map { WallSpaceMapper.wallToPhoto(roughIn, it) }
        val stored = drawn.map { WallSpaceMapper.photoToWall(roughIn, it) }
        val onFinished = WallSpaceMapper.remap(drawn, roughIn, finished)

        assertThat(onFinished).isNotNull()
        // Shown on the Finished photo, each point is the same wall point read through the Finished outline.
        onFinished.orEmpty().zip(stored).forEach { (shown, wall) ->
            val read = WallSpaceMapper.photoToWall(finished, shown)
            assertThat(read?.x ?: 0f).isWithin(1e-4f).of(wall?.x ?: -1f)
            assertThat(read?.y ?: 0f).isWithin(1e-4f).of(wall?.y ?: -1f)
        }
        // And in photo pixels it moved: the two photos were taken from different spots.
        assertThat(onFinished.orEmpty()[0]).isNotEqualTo(drawn[0])
        assertThat(WallSpaceMapper.toInches(stored[0] ?: WallPoint(0f, 0f), lengthIn = 168, heightIn = 96)).isEqualTo(WallInches(50, 19))
    }

    @Test
    @AcceptanceCriterion("AC-C27")
    fun theTextureAndPlainWallAreAlreadyWallSpace() {
        assertThat(WallSpaceMapper.wallToPhoto(null, WallPoint(0.25f, 0.75f))).isEqualTo(PhotoPoint(0.25f, 0.75f))
        assertThat(WallSpaceMapper.photoToWall(null, PhotoPoint(1.4f, -0.2f))).isEqualTo(WallPoint(1f, 0f))
    }

    @Test
    @AcceptanceCriterion("AC-C27")
    fun pointsOffTheWallClampToItsEdgeAndTapsOffItAreRefused() {
        val outside = PhotoPoint(0.95f, 0.5f)
        assertThat(WallSpaceMapper.isOnWall(roughIn, outside)).isFalse()
        assertThat(WallSpaceMapper.isOnWall(roughIn, WallSpaceMapper.wallToPhoto(roughIn, WallPoint(0.5f, 0.5f)))).isTrue()
        assertThat(WallSpaceMapper.photoToWall(roughIn, outside)?.x).isEqualTo(1f)
    }

    @Test
    @AcceptanceCriterion("AC-C27")
    fun aCrossedOutlinePlacesNothing() {
        val crossed = WallOutline(roughIn.topRight, roughIn.topLeft, roughIn.bottomRight, roughIn.bottomLeft)
        assertThat(WallSpaceMapper.photoToWall(crossed, PhotoPoint(0.5f, 0.5f))).isNull()
        assertThat(WallSpaceMapper.remap(listOf(PhotoPoint(0.5f, 0.5f)), crossed, finished)).isNull()
        assertThat(WallSpaceMapper.isOnWall(crossed, PhotoPoint(0.5f, 0.5f))).isFalse()
    }

    @Test
    fun aRectangleOutlineIsAPlainScale() {
        val rect = WallOutline(PhotoPoint(0.2f, 0.1f), PhotoPoint(0.8f, 0.1f), PhotoPoint(0.8f, 0.9f), PhotoPoint(0.2f, 0.9f))
        assertNear(WallSpaceMapper.wallToPhoto(rect, WallPoint(0.5f, 0.25f)), PhotoPoint(0.5f, 0.3f))
    }

    private fun assertNear(actual: PhotoPoint, expected: PhotoPoint) {
        assertThat(actual.x).isWithin(1e-4f).of(expected.x)
        assertThat(actual.y).isWithin(1e-4f).of(expected.y)
    }
}
