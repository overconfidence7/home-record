package com.kurtz.homerecord.core.model

/** The Emergency screen's groups, in screen order (AC-C44). */
enum class ShutoffGroup { WATER, GAS, ELECTRICAL, HVAC, OTHER }

/** One shutoff as the Emergency screen shows it: its instructions are the item's notes, its photo the first item photo. */
data class Shutoff(
    val itemId: String,
    val name: String,
    val subtypeId: String,
    val roomName: String,
    val instructions: String,
    val photoPath: String?,
    val createdAt: Long,
)

data class ShutoffSection(val group: ShutoffGroup, val shutoffs: List<Shutoff>)

/**
 * Groups shutoffs for HM-Emergency (AC-C44): Water, Gas, Electrical, HVAC, Other, empty groups left out. Inside a group
 * the main comes first (water main, gas main or electrical main), then the rest by name.
 */
object ShutoffGroups {
    private val groups = mapOf(
        "water_main" to ShutoffGroup.WATER, "fixture_shutoff" to ShutoffGroup.WATER, "sprinkler" to ShutoffGroup.WATER,
        "sump" to ShutoffGroup.WATER, "cleanout" to ShutoffGroup.WATER,
        "gas_main" to ShutoffGroup.GAS, "propane_tank" to ShutoffGroup.GAS,
        "electrical_main" to ShutoffGroup.ELECTRICAL,
        "hvac_disconnect" to ShutoffGroup.HVAC,
    )
    private val mains = setOf("water_main", "gas_main", "electrical_main")

    fun groupOf(subtypeId: String): ShutoffGroup = groups[subtypeId] ?: ShutoffGroup.OTHER

    fun isMain(subtypeId: String): Boolean = subtypeId in mains

    fun sections(shutoffs: List<Shutoff>): List<ShutoffSection> {
        val byGroup = shutoffs.groupBy { groupOf(it.subtypeId) }
        return ShutoffGroup.entries.mapNotNull { g ->
            byGroup[g]?.sortedWith(
                compareByDescending<Shutoff> { isMain(it.subtypeId) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.createdAt },
            )?.let { ShutoffSection(g, it) }
        }
    }
}
