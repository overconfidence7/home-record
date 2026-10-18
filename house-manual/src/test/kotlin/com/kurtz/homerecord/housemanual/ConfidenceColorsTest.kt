package com.kurtz.homerecord.housemanual

import com.kurtz.homerecord.core.model.Confidence
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConfidenceColorsTest {
    @Test
    fun everyConfidenceHasADistinctColorInBothThemes() {
        for (dark in listOf(false, true)) {
            val colors = Confidence.entries.map { confidenceColor(it, dark) }
            assertNotEquals(colors.size, 0)
            org.junit.Assert.assertEquals(colors.size, colors.toSet().size)
        }
    }

    @Test
    fun darkAndLightDifferForEachConfidence() {
        Confidence.entries.forEach { assertNotEquals(confidenceColor(it, false), confidenceColor(it, true)) }
    }
}
