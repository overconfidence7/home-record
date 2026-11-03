package com.kurtz.homerecord.housemodel.ui

import org.junit.Assert.assertEquals
import org.junit.Test

// AC-U9 (200% clips nothing): the stage row's four labels share one size (V4: "Insulation" drew smaller than its neighbours).
class SegmentLabelSizeTest {
    @Test
    fun keepsTheLargestSizeWhenEveryLabelFits() {
        assertEquals(14f, SegmentLabelSize.largestFitting(7f, 14f, 0.25f) { true }, 0f)
    }

    @Test
    fun stepsDownToTheFirstSizeAtWhichTheLongestLabelFits() {
        // "Insulation" fits at 12 sp and under; the shorter labels would fit at 14 sp but share 12.
        assertEquals(12f, SegmentLabelSize.largestFitting(7f, 14f, 0.25f) { it <= 12f }, 0f)
    }

    @Test
    fun fallsBackToTheMinimumWhenNothingFits() {
        assertEquals(7f, SegmentLabelSize.largestFitting(7f, 14f, 0.25f) { false }, 0f)
    }

    @Test
    fun aMaximumAtOrBelowTheMinimumReturnsTheMinimum() {
        assertEquals(7f, SegmentLabelSize.largestFitting(7f, 6f, 0.25f) { true }, 0f)
    }
}
