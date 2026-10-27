package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

class DataPlateParserTest {
    @Test
    @AcceptanceCriterion("AC-C31")
    fun labelledLinesGiveMakeModelSerial() {
        val p = DataPlateParser.parse(listOf("RHEEM", "Water Heater", "MODEL NO: XE50T10H45U0", "SERIAL NO. M122012345", "50 GAL"))
        assertThat(p.make).isEqualTo("Rheem")
        assertThat(p.model).isEqualTo("XE50T10H45U0")
        assertThat(p.serial).isEqualTo("M122012345")
        assertThat(p.lines).hasSize(5)
    }

    @Test
    @AcceptanceCriterion("AC-C31")
    fun valueOnTheNextLineAndShortLabels() {
        val p = DataPlateParser.parse(listOf("Carrier", "MODEL", "59SC5A080S17", "S/N: 1812A12345"))
        assertThat(p.model).isEqualTo("59SC5A080S17")
        assertThat(p.serial).isEqualTo("1812A12345")
    }

    @Test
    @AcceptanceCriterion("AC-C31")
    fun nothingRecognizedStillReturnsLines() {
        val p = DataPlateParser.parse(listOf(" ", "Made in USA"))
        assertThat(p.make).isNull()
        assertThat(p.model).isNull()
        assertThat(p.serial).isNull()
        assertThat(p.lines).containsExactly("Made in USA")
    }

    @Test
    @AcceptanceCriterion("AC-C31")
    fun brandNeedsAWholeWord() {
        assertThat(DataPlateParser.parse(listOf("Statement of origin")).make).isNull()
        assertThat(DataPlateParser.parse(listOf("A.O. Smith")).make).isNull() // spacing variant not in list; user taps the line
        assertThat(DataPlateParser.parse(listOf("AO SMITH")).make).isEqualTo("A. O. Smith")
    }

    @Test
    @AcceptanceCriterion("AC-C31")
    fun repeatedLinesAreListedOnce() {
        val p = DataPlateParser.parse(listOf("WARNING", "120V", "MODEL: XE50T10H45U0", "120V", " WARNING ", "120  V"))
        assertThat(p.lines).containsExactly("WARNING", "120V", "MODEL: XE50T10H45U0", "120 V").inOrder()
        assertThat(p.model).isEqualTo("XE50T10H45U0")
    }
}
