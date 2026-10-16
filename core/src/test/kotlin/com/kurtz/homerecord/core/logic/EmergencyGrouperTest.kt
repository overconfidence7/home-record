package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyGrouperTest {
    private fun s(id: String, kind: ShutoffKind, created: Long = 0) =
        Shutoff(id, "h", kind, null, null, "x", created, created, Source.HOUSE_MANUAL)

    @Test fun groupsInOrderWaterGasElectricalOther() {
        val all = ShutoffKind.entries.mapIndexed { i, k -> s("$i", k) }.reversed()
        val groups = EmergencyGrouper.group(all)
        assertEquals(
            listOf(EmergencyGroup.WATER, EmergencyGroup.GAS, EmergencyGroup.ELECTRICAL, EmergencyGroup.OTHER),
            groups.map { it.first },
        )
    }

    @Test fun mainsFirstInsideGroup() {
        val groups = EmergencyGrouper.group(
            listOf(s("fv", ShutoffKind.FIXTURE_VALVE, 1), s("wm", ShutoffKind.WATER_MAIN, 2),
                s("hv", ShutoffKind.HVAC_DISCONNECT, 1), s("em", ShutoffKind.ELECTRICAL_MAIN, 2)),
        ).toMap()
        assertEquals(listOf("wm", "fv"), groups.getValue(EmergencyGroup.WATER).map { it.id })
        assertEquals(listOf("em", "hv"), groups.getValue(EmergencyGroup.ELECTRICAL).map { it.id })
    }

    @Test fun otherHoldsTheRest() {
        val g = EmergencyGrouper.group(listOf(s("a", ShutoffKind.SUMP), s("b", ShutoffKind.CLEANOUT), s("c", ShutoffKind.OTHER)))
        assertEquals(1, g.size)
        assertEquals(EmergencyGroup.OTHER, g[0].first)
    }

    @Test fun emptyGroupsOmittedAndEmptyInput() {
        assertTrue(EmergencyGrouper.group(emptyList()).isEmpty())
        assertEquals(1, EmergencyGrouper.group(listOf(s("g", ShutoffKind.GAS_MAIN))).size)
    }
}
