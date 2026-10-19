package com.kurtz.homerecord.core.units

import kotlin.math.roundToInt

enum class UnitSystem { IMPERIAL, METRIC }

/** Parses typed lengths into whole inches (the stored unit) and formats inches back for display. */
object LengthParser {
    private const val CM_PER_INCH = 2.54
    private const val MAX_INCHES = 100_000

    private val feetInches = Regex("""^(\d+(?:\.\d+)?)\s*(?:'|ft|feet|foot)\s*(?:(\d+(?:\.\d+)?)\s*(?:"|in|inches|inch)?)?$""")
    private val inchesOnly = Regex("""^(\d+(?:\.\d+)?)\s*(?:"|in|inches|inch)$""")
    private val spaced = Regex("""^(\d+)\s+(\d+(?:\.\d+)?)$""")
    private val plain = Regex("""^\d+(?:\.\d+)?$""")
    private val metric = Regex("""^(\d+(?:[.,]\d+)?)\s*(m|cm|mm)?$""")

    /**
     * Imperial: `12'6"`, `12' 6`, `12 ft 6 in`, `12.5` (feet), `12 6` (feet and inches), `30"`.
     * Metric: `3.8 m`, `3.8` (metres), `380 cm`, `3800 mm`.
     * Returns null for anything else or a negative or absurd value.
     */
    fun parse(input: String, system: UnitSystem = UnitSystem.IMPERIAL): Int? {
        val text = input.trim().lowercase().replace("’", "'").replace("”", "\"").replace("″", "\"").replace("′", "'")
        if (text.isEmpty()) return null
        val inches = when (system) {
            UnitSystem.IMPERIAL -> parseImperial(text)
            UnitSystem.METRIC -> parseMetric(text)
        } ?: return null
        val rounded = inches.roundToInt()
        return if (rounded in 0..MAX_INCHES) rounded else null
    }

    private fun parseImperial(text: String): Double? {
        feetInches.matchEntire(text)?.let { m ->
            val feet = m.groupValues[1].toDouble()
            val extra = m.groupValues[2].takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
            return feet * 12 + extra
        }
        inchesOnly.matchEntire(text)?.let { return it.groupValues[1].toDouble() }
        spaced.matchEntire(text)?.let { return it.groupValues[1].toDouble() * 12 + it.groupValues[2].toDouble() }
        if (plain.matches(text)) return text.toDouble() * 12
        return null
    }

    private fun parseMetric(text: String): Double? {
        val m = metric.matchEntire(text) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDouble()
        val cm = when (m.groupValues[2]) {
            "cm" -> value
            "mm" -> value / 10
            else -> value * 100
        }
        return cm / CM_PER_INCH
    }

    /** `12' 6"` for imperial, `3.81 m` for metric. */
    fun format(inches: Int, system: UnitSystem = UnitSystem.IMPERIAL): String = when (system) {
        UnitSystem.IMPERIAL -> {
            val feet = inches / 12
            val rest = inches % 12
            when {
                feet == 0 -> "$rest\""
                rest == 0 -> "$feet'"
                else -> "$feet' $rest\""
            }
        }
        UnitSystem.METRIC -> {
            val metres = inches * CM_PER_INCH / 100
            String.format(java.util.Locale.US, "%.2f m", metres)
        }
    }
}
