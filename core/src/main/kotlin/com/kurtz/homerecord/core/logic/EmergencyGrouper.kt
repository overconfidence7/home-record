package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind

enum class EmergencyGroup { WATER, GAS, ELECTRICAL, OTHER }

/** AC-29: Water, Gas, Electrical, Other in that order; mains print first inside each group. */
object EmergencyGrouper {
    fun groupOf(kind: ShutoffKind): EmergencyGroup = when (kind) {
        ShutoffKind.WATER_MAIN, ShutoffKind.FIXTURE_VALVE -> EmergencyGroup.WATER
        ShutoffKind.GAS_MAIN -> EmergencyGroup.GAS
        ShutoffKind.ELECTRICAL_MAIN, ShutoffKind.HVAC_DISCONNECT -> EmergencyGroup.ELECTRICAL
        else -> EmergencyGroup.OTHER
    }

    private fun isMain(kind: ShutoffKind) =
        kind == ShutoffKind.WATER_MAIN || kind == ShutoffKind.GAS_MAIN || kind == ShutoffKind.ELECTRICAL_MAIN

    /** Only non-empty groups are returned. */
    fun group(shutoffs: List<Shutoff>): List<Pair<EmergencyGroup, List<Shutoff>>> =
        shutoffs.groupBy { groupOf(it.kind) }
            .map { (g, list) ->
                g to list.sortedWith(compareBy<Shutoff>({ if (isMain(it.kind)) 0 else 1 }, { it.kind.ordinal }, { it.createdAt }))
            }
            .sortedBy { it.first.ordinal }
}
