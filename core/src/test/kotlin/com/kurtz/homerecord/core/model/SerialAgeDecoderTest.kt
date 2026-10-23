package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class SerialAgeDecoderTest {
    private val today = LocalDate.of(2026, 10, 23)

    private fun date(make: String, serial: String) = SerialAgeDecoder.decode(make, serial, today)?.date

    // AC-C32
    @Test fun rheemAndRuudReadMonthYear() {
        assertThat(date("Rheem", "0415123456")).isEqualTo("2015-04-01")
        assertThat(date("Ruud", "1109X00012")).isEqualTo("2009-11-01")
    }

    // AC-C32
    @Test fun aoSmithAndStateReadYearWeek() {
        assertThat(date("A. O. Smith", "1304M123456")).isEqualTo("2013-01-22")
        assertThat(date("State", "0952A00001")).isEqualTo("2009-12-24")
    }

    // AC-C32
    @Test fun bradfordWhiteReadsYearAndMonthLetters() {
        assertThat(date("Bradford White", "RB1234567")).isEqualTo("2005-02-01")
        assertThat(date("Bradford White", "AC1234567")).isEqualTo("2014-03-01")
    }

    // AC-C32
    @Test fun carrierFamilyReadsWeekYear() {
        assertThat(date("Carrier", "1214E12345")).isEqualTo("2014-03-19")
        assertThat(date("Bryant", "0119A00001")).isEqualTo("2019-01-01")
        assertThat(date("Payne", "5208A00001")).isEqualTo("2008-12-23")
    }

    // AC-C32
    @Test fun traneFamilyReadsYearWeekLetter() {
        assertThat(date("Trane", "425LKJ")).isEqualTo("2024-06-17")
        assertThat(date("American Standard", "133BKJ12")).isEqualTo("2021-08-13")
        assertThat(date("Trane", "1406XA12")).isEqualTo("2014-02-05")
    }

    // AC-C32
    @Test fun lennoxReadsYearWeek() {
        assertThat(date("Lennox", "1810F01234")).isEqualTo("2018-03-05")
    }

    // AC-C32
    @Test fun goodmanAndAmanaReadYearMonth() {
        assertThat(date("Goodman", "1007123456")).isEqualTo("2010-07-01")
        assertThat(date("Amana", "2203456789")).isEqualTo("2022-03-01")
    }

    // AC-C32
    @Test fun yorkReadsMonthLetterAndYearDigit() {
        assertThat(date("York", "C1A1234567")).isEqualTo("2021-03-01")
        assertThat(date("York", "W1A0123456")).isNull()
    }

    // AC-C32
    @Test fun whirlpoolFamilyReadsYearDigitAndWeek() {
        assertThat(date("Whirlpool", "CS41234567")).isEqualTo("2024-03-18")
        assertThat(date("Maytag", "C2301234")).isEqualTo("2022-07-23")
        assertThat(date("KitchenAid", "FL10512345")).isEqualTo("2021-01-29")
    }

    // AC-C32
    @Test fun geReadsMonthAndYearLetters() {
        assertThat(date("GE", "AZ123456A")).isEqualTo("2021-01-01")
        assertThat(date("General Electric", "FA654321")).isEqualTo("2022-06-01")
    }

    // AC-C32
    @Test fun samsungReadsCharacters8And9() {
        assertThat(date("Samsung", "0AB9R4BM123456")).isNull() // 14 characters
        assertThat(date("Samsung", "ABCDEFGR9XYZ123")).isEqualTo("2013-09-01")
        assertThat(date("Samsung", "ABCDEFGTAXYZ123")).isEqualTo("2015-10-01")
    }

    // AC-C32
    @Test fun lgReadsYearDigitAndMonth() {
        assertThat(date("LG", "905KWFV12345")).isEqualTo("2019-05-01")
        assertThat(date("LG", "512KWMT00001")).isEqualTo("2025-12-01")
    }

    // AC-C32
    @Test fun boschReadsFdNumber() {
        assertThat(date("Bosch", "FD 9503 123")).isEqualTo("1995-03-01")
        assertThat(date("Bosch", "FD8905123456")).isEqualTo("1989-05-01")
        assertThat(date("Bosch", "FD1905123456")).isEqualTo("2019-05-01")
    }

    // AC-C32
    @Test fun unknownBrandOrBadSerialGivesNothing() {
        assertThat(date("Frigidaire", "1234567890")).isNull()
        assertThat(date("Rheem", "1315123456")).isNull()
        assertThat(date("Rheem", "12")).isNull()
        assertThat(SerialAgeDecoder.decode(null, "0415123456", today)).isNull()
    }

    // AC-C32
    @Test fun resultNamesTheRule() {
        assertThat(SerialAgeDecoder.decode("Rheem", "0415123456", today)?.rule).contains("Rheem")
    }
}
