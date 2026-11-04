package com.kurtz.homerecord.core.material

/**
 * Finds the total on a scanned receipt from its recognized text lines (AC-F44: the amount is suggested from the
 * receipt total when one is found). Looks for a line that names the total ("Total", "Amount due", "Balance due",
 * "Grand total", "Total due") but not a subtotal, tax or savings line, and reads the amount on that line or, when the
 * scanner split the row, on the next line. The last such line wins, since receipts print the final total last.
 * Returns null when no total line has an amount: the person types it.
 */
object ReceiptTotal {
    private val totalWord = Regex("""\b(grand\s*total|total\s*due|amount\s*due|balance\s*due|total)\b""", RegexOption.IGNORE_CASE)
    private val notTotal = Regex("""sub\s*-?\s*total|\btax\b|saving|discount|items?\s*total|total\s*items|qty|quantity|tendered|change\b""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""(?<![\d.])\$?\s?(\d{1,3}(?:,\d{3})+|\d+)[.,](\d{2})(?!\d)""")

    fun find(lines: List<String>): Long? {
        var found: Long? = null
        for ((i, line) in lines.withIndex()) {
            if (!totalWord.containsMatchIn(line) || notTotal.containsMatchIn(line)) continue
            val cents = amountIn(line) ?: lines.getOrNull(i + 1)?.takeIf { !totalWord.containsMatchIn(it) }?.let(::amountIn)
            if (cents != null && cents > 0) found = cents
        }
        return found
    }

    /** The last amount with cents on [line] ("$1,234.56", "84.10", "84,10"), in cents. */
    internal fun amountIn(line: String): Long? = amount.findAll(line).lastOrNull()?.let { m ->
        m.groupValues[1].replace(",", "").toLongOrNull()?.let { it * 100 + m.groupValues[2].toLong() }
    }
}
