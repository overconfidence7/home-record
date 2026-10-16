package com.kurtz.homerecord.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataPlateParserTest {
    @Test fun waterHeaterPlate() {
        val f = DataPlateParser.parse(listOf("RHEEM", "Water Heater", "MODEL NO. XE50T10H45U0", "SERIAL NO. RHLN0912345678", "50 GAL"))
        assertEquals(DataPlateFields("Rheem", "XE50T10H45U0", "RHLN0912345678"), f)
    }

    @Test fun slashLabels() {
        val f = DataPlateParser.parse(listOf("CARRIER CORPORATION", "M/N: 24ACC636A003", "S/N 1809E12345"))
        assertEquals(DataPlateFields("Carrier", "24ACC636A003", "1809E12345"), f)
    }

    @Test fun modAndSerAbbreviations() {
        val f = DataPlateParser.parse(listOf("Mod: GSX140361", "Ser# 1904123456"))
        assertEquals(DataPlateFields(null, "GSX140361", "1904123456"), f)
    }

    @Test fun valueOnNextLine() {
        val f = DataPlateParser.parse(listOf("MODEL", "TCH4A0ABC", "SERIAL", "2012345678"))
        assertEquals("TCH4A0ABC", f.model)
        assertEquals("2012345678", f.serial)
    }

    @Test fun labelWithoutValueDoesNotStealOtherLabel() {
        val f = DataPlateParser.parse(listOf("MODEL", "SERIAL NO 123456"))
        assertNull(f.model)
        assertEquals("123456", f.serial)
    }

    @Test fun multiWordBrands() {
        assertEquals("A. O. Smith", DataPlateParser.parse(listOf("A.O. SMITH", "MODEL FPSH-50")).make)
        assertEquals("Bradford White", DataPlateParser.parse(listOf("Bradford  White Corp")).make)
    }

    @Test fun brandMustBeWholeWord() {
        assertNull(DataPlateParser.parse(listOf("GENERAL PURPOSE", "CHANGE FILTER")).make)
    }

    @Test fun modelWordPrefixIsNotALabel() {
        assertNull(DataPlateParser.parse(listOf("MODERN DESIGN 12345")).model)
    }

    @Test fun lowercaseAndPunctuation() {
        val f = DataPlateParser.parse(listOf("  model no: ab-1234x ", "serial number: 99887766 "))
        assertEquals("ab-1234x", f.model)
        assertEquals("99887766", f.serial)
    }

    @Test fun noTextGivesEmptyFields() {
        assertTrue(DataPlateParser.parse(emptyList()).isEmpty)
        assertTrue(DataPlateParser.parse(listOf("   ", "WARNING HOT")).isEmpty)
    }
}
