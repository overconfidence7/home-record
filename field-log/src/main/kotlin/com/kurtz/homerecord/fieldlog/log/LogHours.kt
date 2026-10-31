package com.kurtz.homerecord.fieldlog.log

/**
 * Reads the hours someone types for a crew line in the daily log: `7.5`, `7`, `7:30`, `7h 30m`, `7h`, `45m`.
 * Anything else, a negative time or more than a day, is null so the sheet can say so.
 */
object LogHours {
    private val hm = Regex("""^(\d{1,2})\s*h(?:\s*(\d{1,2})\s*m)?$""")
    private val mOnly = Regex("""^(\d{1,4})\s*m$""")
    private val colon = Regex("""^(\d{1,2}):(\d{2})$""")
    private val decimal = Regex("""^\d{0,2}(?:[.,]\d{1,2})?$""")

    fun minutes(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        val total = hm.matchEntire(t)?.let { m ->
            val mins = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (mins >= 60) null else m.groupValues[1].toInt() * 60 + mins
        } ?: mOnly.matchEntire(t)?.groupValues?.get(1)?.toInt()
            ?: colon.matchEntire(t)?.let { m ->
                val mins = m.groupValues[2].toInt()
                if (mins >= 60) null else m.groupValues[1].toInt() * 60 + mins
            }
            ?: if (decimal.matches(t) && t != "." && t != ",") Math.round(t.replace(',', '.').toDouble() * 60).toInt() else null
        return total?.takeIf { it in 0..24 * 60 }
    }

    /** How a saved line shows in the field, in decimal hours for the number keypad: `7.5`, `7.33`, `8`. */
    fun text(minutes: Int): String =
        java.math.BigDecimal(minutes).divide(java.math.BigDecimal(60), 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
