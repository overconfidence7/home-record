package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker

/** One printed line of the panel legend. Every field is display text; empty values are [LegendRowBuilder.EMPTY]. */
data class LegendRow(
    val slot: String,
    val label: String,
    val amps: String,
    val poles: String,
    val type: String,
    val rooms: String,
)

/** AC-23 / AC-24: one row per slot in panel order; two-pole prints once, tandems print as "N a" / "N b". */
object LegendRowBuilder {
    const val EMPTY = "—"

    fun build(
        slotCount: Int,
        breakers: List<Breaker>,
        roomNamesFor: (Breaker) -> List<String>,
        typeLabel: (Breaker) -> String,
    ): List<LegendRow> {
        val rows = mutableListOf<LegendRow>()
        for (slot in 1..slotCount) {
            val here = breakers.filter { slot in it.slots }
            val tandem = here.filter { it.tandemIndex != null }
            when {
                tandem.isNotEmpty() -> {
                    rows += rowFor("$slot a", tandem.firstOrNull { it.tandemIndex == 0 }, roomNamesFor, typeLabel)
                    rows += rowFor("$slot b", tandem.firstOrNull { it.tandemIndex == 1 }, roomNamesFor, typeLabel)
                }
                here.isEmpty() -> rows += rowFor(slot.toString(), null, roomNamesFor, typeLabel)
                else -> {
                    val breaker = here.first()
                    // A multi-slot breaker prints once, at its first slot.
                    if (slot == breaker.slots.min()) {
                        rows += rowFor(breaker.slots.sorted().joinToString(", "), breaker, roomNamesFor, typeLabel)
                    }
                }
            }
        }
        return rows
    }

    private fun rowFor(
        slot: String,
        breaker: Breaker?,
        roomNamesFor: (Breaker) -> List<String>,
        typeLabel: (Breaker) -> String,
    ): LegendRow {
        if (breaker == null) return LegendRow(slot, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY)
        return LegendRow(
            slot = slot,
            label = breaker.label.ifBlank { EMPTY },
            amps = breaker.amps?.toString() ?: EMPTY,
            poles = breaker.poles.toString(),
            type = typeLabel(breaker),
            rooms = roomNamesFor(breaker).sorted().joinToString(", ").ifEmpty { EMPTY },
        )
    }
}
