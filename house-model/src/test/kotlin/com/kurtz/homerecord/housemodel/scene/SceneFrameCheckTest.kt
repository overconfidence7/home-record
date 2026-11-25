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
}
