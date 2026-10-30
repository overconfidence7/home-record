package com.kurtz.homerecord.core.units

import java.util.Locale

/** Money is stored in cents as `Long`; this is the one parser and formatter for typed amounts. */
object Money {
    private val amount = Regex("""\d+(\.\d{1,2})?""")

    /** "1,299.50", "$1299" or "5.5" to cents; null when it isn't a non-negative amount with at most two decimals. */
    fun parseCents(raw: String): Long? {
        val t = raw.trim().removePrefix("$").replace(",", "")
        if (!amount.matches(t)) return null
        val (whole, frac) = t.split('.').let { it[0] to (it.getOrNull(1) ?: "") }
        return whole.toLongOrNull()?.let { it * 100 + frac.padEnd(2, '0').toLong() }
    }

    /** Cents to the editable form, "24.99". */
    fun format(cents: Long): String = String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100)
}
