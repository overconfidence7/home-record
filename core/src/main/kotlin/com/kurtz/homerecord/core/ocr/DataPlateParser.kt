package com.kurtz.homerecord.core.ocr

/** Proposed equipment fields; every value is only a suggestion the user confirms or edits (AC-46). */
data class DataPlateFields(val make: String? = null, val model: String? = null, val serial: String? = null) {
    val isEmpty: Boolean get() = make == null && model == null && serial == null
}

/** Label-heuristic parser for recognized data-plate lines (AC-46). Pure. */
object DataPlateParser {
    private val BRANDS = listOf(
        "A. O. Smith" to Regex("""\bA\.?\s?O\.?\s?SMITH\b""", RegexOption.IGNORE_CASE),
        "Bradford White" to Regex("""\bBRADFORD\s+WHITE\b""", RegexOption.IGNORE_CASE),
        "American Standard" to Regex("""\bAMERICAN\s+STANDARD\b""", RegexOption.IGNORE_CASE),
    ) + listOf(
        "Rheem", "Ruud", "Carrier", "Trane", "Lennox", "Goodman", "Amana", "York", "Daikin", "Bosch", "Navien",
        "Rinnai", "Whirlpool", "Kenmore", "Samsung", "LG", "GE", "Maytag", "Frigidaire", "Mitsubishi", "Fujitsu",
        "Honeywell", "Heil", "Bryant", "Payne", "Weil-McLain", "Burnham", "Kohler", "Generac", "Mr. Cool", "Takagi",
    ).map { it to Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE) }

    private const val SEP = """\s*[:#.\-]?\s*"""
    private val MODEL_LABEL = Regex("""\b(?:MODEL\s*(?:NO\.?|NUMBER|NUM\.?|#)?|MOD\.?|M/N)(?![A-Za-z])$SEP(.*)""", RegexOption.IGNORE_CASE)
    private val SERIAL_LABEL = Regex("""\b(?:SERIAL\s*(?:NO\.?|NUMBER|NUM\.?|#)?|SER\.?|S/N)(?![A-Za-z])$SEP(.*)""", RegexOption.IGNORE_CASE)
    private val TOKEN = Regex("""[A-Za-z0-9][A-Za-z0-9\-/.]*[A-Za-z0-9]""")

    fun parse(lines: List<String>): DataPlateFields {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        return DataPlateFields(
            make = findMake(clean),
            model = findLabeled(clean, MODEL_LABEL),
            serial = findLabeled(clean, SERIAL_LABEL),
        )
    }

    private fun findMake(lines: List<String>): String? {
        for (line in lines) {
            for ((name, regex) in BRANDS) if (regex.containsMatchIn(line)) return name
        }
        return null
    }

    private fun findLabeled(lines: List<String>, label: Regex): String? {
        for ((i, line) in lines.withIndex()) {
            val match = label.find(line) ?: continue
            val sameLine = valueFrom(match.groupValues[1])
            if (sameLine != null) return sameLine
            // Label alone on its line: the value is the next line.
            if (match.groupValues[1].isBlank()) lines.getOrNull(i + 1)?.let { next ->
                if (label !== MODEL_LABEL && MODEL_LABEL.containsMatchIn(next)) return@let
                if (label !== SERIAL_LABEL && SERIAL_LABEL.containsMatchIn(next)) return@let
                valueFrom(next)?.let { return it }
            }
        }
        return null
    }

    /** First plausible token: at least 3 characters and containing a digit. */
    private fun valueFrom(text: String): String? =
        TOKEN.findAll(text).map { it.value }.firstOrNull { it.length >= 3 && it.any(Char::isDigit) }
}
