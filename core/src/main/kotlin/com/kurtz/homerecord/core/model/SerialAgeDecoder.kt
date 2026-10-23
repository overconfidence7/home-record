package com.kurtz.homerecord.core.model

import java.time.LocalDate

/** A manufacture date read from a serial number, with the rule that produced it (shown as "decoded from serial"). */
data class DecodedSerialDate(val date: String, val rule: String)

/**
 * Decodes manufacture dates from serial numbers. Pure. Each brand family has one documented rule; a
 * month-only rule yields the 1st of the month, a week rule yields the Monday of that week. Rules that
 * carry a single year digit or a letter code resolve to the latest year that is not after [today].
 *
 * Rules (positions are 1-based):
 * - Rheem/Ruud: MMYY, first four digits.
 * - AO Smith/State: YYWW, first four digits.
 * - Bradford White: letter 1 year (A=1991, skipping I, O, Q; 23-year cycle), letter 2 month (A=Jan).
 * - Carrier/Bryant/Payne: WWYY, first four digits.
 * - Trane/American Standard: Y WW L (one year digit, two week digits, a letter) or YY WW L.
 * - Lennox: YY WW then a letter.
 * - Goodman/Amana: YYMM, first four digits.
 * - York: letter 1 month (A=Jan), digit 2 year digit.
 * - Whirlpool/Maytag/KitchenAid: one or two plant letters, then Y WW.
 * - GE: letter 1 month (A=Jan, skipping I), letter 2 year (A,D,F,G,H,L,M,R,S,T,V,Z, A=2010, 12-year cycle).
 * - Samsung: 11 or 15 characters; character 8 year letter, character 9 month (1-9, A-C).
 * - LG: Y MM, first three digits.
 * - Bosch: FD followed by YYMM.
 */
object SerialAgeDecoder {
    private enum class Family { RHEEM, AOSMITH, BRADFORD, CARRIER, TRANE, LENNOX, GOODMAN, YORK, WHIRLPOOL, GE, SAMSUNG, LG, BOSCH }

    private val families = linkedMapOf(
        "rheem" to Family.RHEEM, "ruud" to Family.RHEEM,
        "ao smith" to Family.AOSMITH, "a o smith" to Family.AOSMITH, "state" to Family.AOSMITH,
        "bradford white" to Family.BRADFORD,
        "carrier" to Family.CARRIER, "bryant" to Family.CARRIER, "payne" to Family.CARRIER,
        "trane" to Family.TRANE, "american standard" to Family.TRANE,
        "lennox" to Family.LENNOX,
        "goodman" to Family.GOODMAN, "amana" to Family.GOODMAN,
        "york" to Family.YORK,
        "whirlpool" to Family.WHIRLPOOL, "maytag" to Family.WHIRLPOOL, "kitchenaid" to Family.WHIRLPOOL,
        "general electric" to Family.GE, "ge" to Family.GE,
        "samsung" to Family.SAMSUNG, "lg" to Family.LG, "bosch" to Family.BOSCH,
    )

    private const val BRADFORD_YEARS = "ABCDEFGHJKLMNPRSTUVWXYZ"
    private const val GE_YEARS = "ADFGHLMRSTVZ"
    private const val SAMSUNG_YEARS = "HJKLMNPRSTWXYZABCDEFG"
    private const val MONTHS = "ABCDEFGHJKLM"

    fun decode(make: String?, serial: String?, today: LocalDate = LocalDate.now()): DecodedSerialDate? {
        val family = familyOf(make) ?: return null
        val s = serial.orEmpty().uppercase().filter { it.isLetterOrDigit() }
        if (s.length < 4) return null
        val (date, rule) = when (family) {
            Family.RHEEM -> monthYear(s, today, "Rheem/Ruud MMYY") { s.digits(0, 2) to s.digits(2, 2) }
            Family.AOSMITH -> weekYear(s, today, "AO Smith/State YYWW") { s.digits(2, 2) to s.digits(0, 2) }
            Family.BRADFORD -> bradford(s, today)
            Family.CARRIER -> weekYear(s, today, "Carrier/Bryant/Payne WWYY") { s.digits(0, 2) to s.digits(2, 2) }
            Family.TRANE -> trane(s, today)
            Family.LENNOX -> lennox(s, today)
            Family.GOODMAN -> monthYear(s, today, "Goodman/Amana YYMM") { s.digits(2, 2) to s.digits(0, 2) }
            Family.YORK -> york(s, today)
            Family.WHIRLPOOL -> whirlpool(s, today)
            Family.GE -> ge(s, today)
            Family.SAMSUNG -> samsung(s, today)
            Family.LG -> lg(s, today)
            Family.BOSCH -> bosch(s, today)
        } ?: return null
        return if (date.isAfter(today)) null else DecodedSerialDate(date.toString(), rule)
    }

    private fun familyOf(make: String?): Family? {
        val m = make.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        if (m.isEmpty()) return null
        families[m]?.let { return it }
        return families.entries.firstOrNull { (name, _) -> m.split(' ').joinToString(" ").let { " $it ".contains(" $name ") } }?.value
    }

    private fun String.digits(start: Int, n: Int): Int? =
        if (length >= start + n && substring(start, start + n).all { it.isDigit() }) substring(start, start + n).toInt() else null

    private fun fullYear(yy: Int, today: LocalDate): Int {
        val y = 2000 + yy
        return if (y <= today.year) y else 1900 + yy
    }

    /** Latest year ending in [digit] that is not after today's year. */
    private fun yearFromDigit(digit: Int, today: LocalDate): Int {
        var y = today.year - today.year % 10 + digit
        if (y > today.year) y -= 10
        return y
    }

    private fun monthDate(year: Int, month: Int): LocalDate? =
        if (month in 1..12) LocalDate.of(year, month, 1) else null

    private fun weekDate(year: Int, week: Int): LocalDate? =
        if (week in 1..53) LocalDate.of(year, 1, 1).plusWeeks((week - 1).toLong()) else null

    private inline fun monthYear(s: String, today: LocalDate, rule: String, parts: () -> Pair<Int?, Int?>): Pair<LocalDate, String>? {
        val (m, yy) = parts()
        if (m == null || yy == null) return null
        return monthDate(fullYear(yy, today), m)?.let { it to rule }
    }

    private inline fun weekYear(s: String, today: LocalDate, rule: String, parts: () -> Pair<Int?, Int?>): Pair<LocalDate, String>? {
        val (w, yy) = parts()
        if (w == null || yy == null) return null
        return weekDate(fullYear(yy, today), w)?.let { it to rule }
    }

    private fun bradford(s: String, today: LocalDate): Pair<LocalDate, String>? {
        val yi = BRADFORD_YEARS.indexOf(s[0])
        val mi = MONTHS.indexOf(s[1])
        if (yi < 0 || mi < 0) return null
        var year = 1991 + yi
        while (year + BRADFORD_YEARS.length <= today.year) year += BRADFORD_YEARS.length
        return monthDate(year, mi + 1)?.let { it to "Bradford White year letter, month letter" }
    }

    private fun trane(s: String, today: LocalDate): Pair<LocalDate, String>? {
        val rule = "Trane/American Standard year, week, letter"
        if (s.length > 3 && s[3].isLetter()) {
            val y = s.digits(0, 1)
            val w = s.digits(1, 2)
            if (y != null && w != null) return weekDate(yearFromDigit(y, today), w)?.let { it to rule }
        }
        if (s.length > 4 && s[4].isLetter()) {
            val yy = s.digits(0, 2)
            val w = s.digits(2, 2)
            if (yy != null && w != null) return weekDate(fullYear(yy, today), w)?.let { it to rule }
        }
        return null
    }

    private fun lennox(s: String, today: LocalDate): Pair<LocalDate, String>? {
        if (s.length < 5 || !s[4].isLetter()) return null
        val yy = s.digits(0, 2) ?: return null
        val w = s.digits(2, 2) ?: return null
        return weekDate(fullYear(yy, today), w)?.let { it to "Lennox YYWW, letter" }
    }

    private fun york(s: String, today: LocalDate): Pair<LocalDate, String>? {
        val mi = MONTHS.indexOf(s[0])
        val y = s.digits(1, 1) ?: return null
        if (mi < 0) return null
        return monthDate(yearFromDigit(y, today), mi + 1)?.let { it to "York month letter, year digit" }
    }

    private fun whirlpool(s: String, today: LocalDate): Pair<LocalDate, String>? {
        val rule = "Whirlpool/Maytag/KitchenAid plant letters, year digit, week"
        val letters = s.takeWhile { it.isLetter() }.length
        if (letters !in 1..2) return null
        val y = s.digits(letters, 1) ?: return null
        val w = s.digits(letters + 1, 2) ?: return null
        return weekDate(yearFromDigit(y, today), w)?.let { it to rule }
    }

    private fun ge(s: String, today: LocalDate): Pair<LocalDate, String>? {
        val mi = MONTHS.indexOf(s[0])
        val yi = GE_YEARS.indexOf(s[1])
        if (mi < 0 || yi < 0) return null
        var year = 2010 + yi
        while (year + GE_YEARS.length <= today.year) year += GE_YEARS.length
        return monthDate(year, mi + 1)?.let { it to "GE month letter, year letter" }
    }

    private fun samsung(s: String, today: LocalDate): Pair<LocalDate, String>? {
        if (s.length != 11 && s.length != 15) return null
        val yi = SAMSUNG_YEARS.indexOf(s[7])
        val month = "123456789ABC".indexOf(s[8]) + 1
        if (yi < 0 || month == 0) return null
        var year = 2006 + yi
        while (year + SAMSUNG_YEARS.length <= today.year) year += SAMSUNG_YEARS.length
        return monthDate(year, month)?.let { it to "Samsung year letter (character 8), month (character 9)" }
    }

    private fun lg(s: String, today: LocalDate): Pair<LocalDate, String>? {
        val y = s.digits(0, 1) ?: return null
        val m = s.digits(1, 2) ?: return null
        return monthDate(yearFromDigit(y, today), m)?.let { it to "LG year digit, month" }
    }

    private fun bosch(s: String, today: LocalDate): Pair<LocalDate, String>? {
        if (!s.startsWith("FD")) return null
        val yy = s.digits(2, 2) ?: return null
        val m = s.digits(4, 2) ?: return null
        return monthDate(fullYear(yy, today), m)?.let { it to "Bosch FD number YYMM" }
    }
}
