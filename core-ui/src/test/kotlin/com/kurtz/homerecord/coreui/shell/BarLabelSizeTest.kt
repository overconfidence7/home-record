package com.kurtz.homerecord.coreui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/** [judge:nav-label-200-uneven]: every bar label shares one size, stepped down until the longest fits (rules 25, 32). */
class BarLabelSizeTest {
    // A label is 0.6 sp-wide per character per sp at font scale [scale].
    private fun width(scale: Float) = { label: String, sp: Float -> label.length * sp * 0.6f * scale }
    private val fieldLog = listOf("Today", "Jobs", "Clock", "Schedule", "More")

    @Test
    fun fullSizeWhenEveryLabelFits() {
        assertEquals(12f, BarLabelSize.pick(fieldLog, availablePx = 80f, widthPx = width(1f)), 0f)
    }

    @Test
    fun stepsDownUntilTheLongestFits() {
        // "Schedule" (8 chars) at scale 2: 9.6 px per sp; 80 px allows 8.33 sp, so 8.0 in half-sp steps.
        assertEquals(8f, BarLabelSize.pick(fieldLog, availablePx = 80f, widthPx = width(2f)), 0f)
    }

    @Test
    fun oneSizeForAllLabelsNotPerLabel() {
        val size = BarLabelSize.pick(fieldLog, availablePx = 80f, widthPx = width(2f))
        // The short labels would fit at 12 sp alone, but they take the shared size with "Schedule".
        assertEquals(true, width(2f)("Today", 12f) <= 80f)
        assertEquals(8f, size, 0f)
    }

    @Test
    fun floorsAtMinimumWhenNothingFits() {
        assertEquals(6f, BarLabelSize.pick(listOf("Reminders"), availablePx = 10f, widthPx = width(2f)), 0f)
    }
}
