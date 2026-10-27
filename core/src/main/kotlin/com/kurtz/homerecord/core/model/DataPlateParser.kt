package com.kurtz.homerecord.core.model

/** A field a recognized data-plate line can be assigned to. */
enum class PlateField { MAKE, MODEL, SERIAL }

/** What [DataPlateParser] proposes for each field, plus every recognized line for tap-to-assign. */
data class DataPlateProposal(
    val lines: List<String>,
    val make: String? = null,
    val model: String? = null,
    val serial: String? = null,
) {
    fun valueFor(field: PlateField): String? = when (field) {
        PlateField.MAKE -> make
        PlateField.MODEL -> model
        PlateField.SERIAL -> serial
    }
}

/** Proposes make, model and serial from OCR lines of an appliance label. Pure; no OCR here. */
object DataPlateParser {
    private val brands = listOf(
        "Rheem", "Ruud", "A. O. Smith", "AO Smith", "Bradford White", "State", "Carrier", "Bryant", "Payne",
        "Trane", "American Standard", "Lennox", "Goodman", "Amana", "York", "Whirlpool", "Maytag", "KitchenAid",
        "GE", "General Electric", "Samsung", "LG", "Bosch", "Frigidaire", "Kenmore", "Navien", "Rinnai", "Daikin",
    )
    private val modelLabel = Regex("""^(?:model|mod|m/n|mdl)\b\s*(?:no\.?|number|#)?\s*[:.#-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val serialLabel = Regex("""^(?:serial|ser|s/n|sn)\b\s*(?:no\.?|number|#)?\s*[:.#-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val bareModel = Regex("""^(?:model|m/n)\s*(?:no\.?|number|#)?\s*:?$""", RegexOption.IGNORE_CASE)
    private val bareSerial = Regex("""^(?:serial|s/n)\s*(?:no\.?|number|#)?\s*:?$""", RegexOption.IGNORE_CASE)
    private val code = Regex("""^[A-Z0-9][A-Z0-9\-/.]{3,}$""")

    fun parse(rawLines: List<String>): DataPlateProposal {
        // Real labels repeat lines ("120V", "WARNING"); one copy is enough to tap and keeps list keys unique.
        val lines = rawLines.map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.distinct()
        var model: String? = null
        var serial: String? = null
        for ((i, line) in lines.withIndex()) {
            if (serial == null) serial = labelled(serialLabel, bareSerial, line, lines.getOrNull(i + 1))
            if (model == null) model = labelled(modelLabel, bareModel, line, lines.getOrNull(i + 1))
        }
        val make = lines.firstNotNullOfOrNull { line ->
            brands.firstOrNull { b -> Regex("""(^|[^A-Za-z])${Regex.escape(b)}($|[^A-Za-z])""", RegexOption.IGNORE_CASE).containsMatchIn(line) }
        }?.let(::canonicalBrand)
        return DataPlateProposal(lines, make, model, serial)
    }

    // "MODEL: X" on one line, or the bare label with the value on the next line.
    private fun labelled(label: Regex, bare: Regex, line: String, next: String?): String? {
        label.find(line)?.groupValues?.get(1)?.trim()?.let { return it.takeIf { v -> code.matches(v.uppercase()) } }
        if (!bare.matches(line)) return null
        return next?.trim()?.takeIf { code.matches(it.uppercase()) }
    }

    private fun canonicalBrand(name: String): String = when (name.lowercase()) {
        "a. o. smith", "ao smith" -> "A. O. Smith"
        "ge", "general electric" -> "GE"
        "lg" -> "LG"
        else -> name
    }
}
