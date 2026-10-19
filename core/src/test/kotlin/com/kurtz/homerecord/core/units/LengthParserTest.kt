package com.kurtz.homerecord.core.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LengthParserTest {
    private fun imp(s: String) = LengthParser.parse(s, UnitSystem.IMPERIAL)
    private fun met(s: String) = LengthParser.parse(s, UnitSystem.METRIC)

    @Test fun feetAndInchesWithMarks() {
        assertThat(imp("12'6\"")).isEqualTo(150)
        assertThat(imp("12' 6")).isEqualTo(150)
        assertThat(imp("12 ft 6 in")).isEqualTo(150)
    }

    @Test fun decimalFeet() {
        assertThat(imp("12.5")).isEqualTo(150)
        assertThat(imp("8")).isEqualTo(96)
    }

    @Test fun spaceSeparatedFeetAndInches() {
        assertThat(imp("12 6")).isEqualTo(150)
        assertThat(imp("8 0")).isEqualTo(96)
    }

    @Test fun feetOnlyAndInchesOnly() {
        assertThat(imp("10'")).isEqualTo(120)
        assertThat(imp("30\"")).isEqualTo(30)
    }

    @Test fun curlyQuotesAreAccepted() {
        assertThat(imp("12’6”")).isEqualTo(150)
    }

    @Test fun metricMetresCentimetresMillimetres() {
        assertThat(met("3.8 m")).isEqualTo(150)
        assertThat(met("3.8")).isEqualTo(150)
        assertThat(met("3,8 m")).isEqualTo(150)
        assertThat(met("380 cm")).isEqualTo(150)
        assertThat(met("3800mm")).isEqualTo(150)
    }

    @Test fun rejectsGarbageAndNegatives() {
        assertThat(imp("")).isNull()
        assertThat(imp("abc")).isNull()
        assertThat(imp("-5")).isNull()
        assertThat(imp("12'6\"x")).isNull()
        assertThat(met("3.8 ft")).isNull()
        assertThat(imp("99999999")).isNull()
    }

    @Test fun formatsBothSystems() {
        assertThat(LengthParser.format(150)).isEqualTo("12' 6\"")
        assertThat(LengthParser.format(144)).isEqualTo("12'")
        assertThat(LengthParser.format(9)).isEqualTo("9\"")
        assertThat(LengthParser.format(150, UnitSystem.METRIC)).isEqualTo("3.81 m")
    }

    @Test fun formatThenParseRoundTrips() {
        for (i in listOf(0, 1, 11, 12, 13, 96, 150, 1234)) {
            assertThat(imp(LengthParser.format(i))).isEqualTo(i)
        }
    }
}
