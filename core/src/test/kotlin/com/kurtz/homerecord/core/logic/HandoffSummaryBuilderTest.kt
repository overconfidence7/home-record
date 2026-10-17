package com.kurtz.homerecord.core.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class HandoffSummaryBuilderTest {
    private val labels = HandoffLabels("Rooms", "Walls", "BW", "Equip", "Finishes", "Shutoffs", "Panels")

    @Test fun `sections list every count in order`() {
        val sections = HandoffSummaryBuilder.sections(HandoffCounts(3, 4, 5, 6, 0, 7, 1), labels)
        assertEquals(
            listOf("Rooms" to "3", "Walls" to "4", "BW" to "5", "Equip" to "6", "Finishes" to "0", "Shutoffs" to "7", "Panels" to "1"),
            sections.map { it.label to it.text },
        )
    }

    @Test fun `file names strip unsafe characters`() {
        assertEquals("handoff-ab-1.pdf", HandoffSummaryBuilder.pdfFileName("ab/../-1"))
        assertEquals("handoff-ab-1.homerecord", HandoffSummaryBuilder.bundleFileName("ab/../-1"))
    }
}
