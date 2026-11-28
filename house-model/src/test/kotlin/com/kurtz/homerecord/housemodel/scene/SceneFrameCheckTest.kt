package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C20: the 3D drawn signal only counts a frame with walls on it ([judge:qa-3d-drawn-tag-blank])
class SceneFrameCheckTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val faintPink = 0xFFFCF4FA.toInt() // the washed-out slab the judge saw on a white view
    private val cream = 0xFFF3E3C3.toInt() // kitchen wall tint
    private val floorGrey = 0xFFBDB6AC.toInt()

    private fun frame(vararg parts: Pair<Int, Int>): IntArray = parts.flatMap { (color, n) -> List(n) { color } }.toIntArray()

    @AcceptanceCriterion("AC-C20")
    @Test fun aBlankWhiteOrBlackFrameIsNotDrawn() {
        assertThat(SceneFrameCheck.check(frame(white to 1000)).drawn).isFalse()
        assertThat(SceneFrameCheck.check(frame(black to 1000)).drawn).isFalse()
        assertThat(SceneFrameCheck.check(IntArray(0)).drawn).isFalse()
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun aFaintWashedOutSlabOnWhiteIsNotDrawn() {
        val result = SceneFrameCheck.check(frame(white to 500, faintPink to 500))
        assertThat(result.drawn).isFalse()
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun nameTagsAloneOnABlankViewAreNotDrawn() {
        // Dark label text covers a few percent of the view; with no walls the frame is still blank.
        val result = SceneFrameCheck.check(frame(white to 960, black to 40))
        assertThat(result.drawn).isFalse()
        assertThat(result.share).isWithin(0.001f).of(0.04f)
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun wallsAndFloorOnTheBackgroundAreDrawn() {
        val result = SceneFrameCheck.check(frame(white to 600, cream to 250, floorGrey to 150))
        assertThat(result.drawn).isTrue()
        assertThat(result.share).isWithin(0.001f).of(0.4f)
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun standingInsideARoomFilledWithWallsIsDrawn() {
        // The wall tint is the most common colour; the floor stands out from it.
        val result = SceneFrameCheck.check(frame(cream to 700, floorGrey to 300))
        assertThat(result.drawn).isTrue()
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun aGentleGradientCountsAsOneBackground() {
        val gradient = IntArray(1000) { i -> val v = 240 + i % 16; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        assertThat(SceneFrameCheck.dominant(gradient) shr 4 and 0x0F0F0F).isEqualTo(0x0F0F0F)
        assertThat(SceneFrameCheck.check(gradient).drawn).isFalse()
    }

    // [auto:3d:house-manual:house-manual]: with post-processing off, lit walls reach the surface unencoded (a cream wall
    // lit to about 0.8 of its linear colour is about B79D70); the light surface stays as stored. A floor of such walls
    // and floor on the surface clears the threshold; the washed-out FFF7FE slab on FEFEFE from 884bf2f4 does not.
    @AcceptanceCriterion("AC-C20")
    @Test fun unencodedLitWallsOnTheLightSurfaceAreDrawn() {
        val surface = 0xFFF4F3FA.toInt()
        val litCream = 0xFFB79D70.toInt()
        val litFloor = 0xFF7A736A.toInt()
        assertThat(SceneFrameCheck.check(frame(surface to 700, litCream to 150, litFloor to 150)).drawn).isTrue()
        assertThat(SceneFrameCheck.check(frame(0xFFFEFEFE.toInt() to 300, 0xFFFFF7FE.toInt() to 700)).drawn).isFalse()
    }

    // [judge:hm-3d-room-blank-recurs]: a room view that came out white or the surface colour is blank, measured against
    // the surface the scene clears to, even when the hint text or a lighter wash makes another colour the most common.
    @AcceptanceCriterion("AC-C20")
    @Test fun anAllWhiteOrAllSurfaceRoomViewIsNotDrawn() {
        val lightSurface = 0xFFF4F3FA.toInt()
        val darkSurface = 0xFF1D1B20.toInt()
        assertThat(SceneFrameCheck.check(frame(white to 1000), lightSurface).drawn).isFalse()
        assertThat(SceneFrameCheck.check(frame(lightSurface to 1000), lightSurface).drawn).isFalse()
        assertThat(SceneFrameCheck.check(frame(darkSurface to 1000), darkSurface).drawn).isFalse()
        // A white wash covering most of the view, with the surface showing around it: something "stands out" from the
        // wash, but nothing stands out from the surface.
        assertThat(SceneFrameCheck.check(frame(white to 800, lightSurface to 200), lightSurface).drawn).isFalse()
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun aRoomViewFilledWithWallsIsDrawnOnEitherSurface() {
        val litCream = 0xFFB79D70.toInt()
        val litFloor = 0xFF7A736A.toInt()
        assertThat(SceneFrameCheck.check(frame(litCream to 700, litFloor to 300), 0xFFF4F3FA.toInt()).drawn).isTrue()
        assertThat(SceneFrameCheck.check(frame(litCream to 700, litFloor to 300), 0xFF1D1B20.toInt()).drawn).isTrue()
    }
}
