package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C44: the Emergency screen groups shutoffs Water / Gas / Electrical / HVAC / Other with mains first.
class ShutoffGroupsTest {
    private fun s(id: String, subtype: String, name: String = id, at: Long = 0) =
        Shutoff(id, name, subtype, roomName = "Basement", instructions = "", photoPath = null, createdAt = at)

    @Test
    @AcceptanceCriterion("AC-C44")
    fun groupsComeInScreenOrderWithMainsFirstAndEmptyGroupsLeftOut() {
        val sections = ShutoffGroups.sections(
            listOf(
                s("sink", "fixture_shutoff", "Kitchen sink cold valve"),
                s("ac", "hvac_disconnect", "AC disconnect"),
                s("water", "water_main", "Water main"),
                s("gas", "gas_main", "Gas meter valve"),
                s("hose", "fixture_shutoff", "Hose bib valve"),
            ),
        )
        assertThat(sections.map { it.group }).containsExactly(ShutoffGroup.WATER, ShutoffGroup.GAS, ShutoffGroup.HVAC).inOrder()
        assertThat(sections[0].shutoffs.map { it.itemId }).containsExactly("water", "hose", "sink").inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C44")
    fun subtypesMapToTheirGroupsAndUnknownOnesToOther() {
        assertThat(ShutoffGroups.groupOf("propane_tank")).isEqualTo(ShutoffGroup.GAS)
        assertThat(ShutoffGroups.groupOf("electrical_main")).isEqualTo(ShutoffGroup.ELECTRICAL)
        assertThat(ShutoffGroups.groupOf("sump")).isEqualTo(ShutoffGroup.WATER)
        assertThat(ShutoffGroups.groupOf("something_new")).isEqualTo(ShutoffGroup.OTHER)
        assertThat(ShutoffGroups.isMain("electrical_main")).isTrue()
        assertThat(ShutoffGroups.isMain("fixture_shutoff")).isFalse()
    }

    @Test
    fun namesTieBreakByAgeSoTheOrderIsStable() {
        val sections = ShutoffGroups.sections(listOf(s("b", "sump", "Valve", at = 2), s("a", "sump", "valve", at = 1)))
        assertThat(sections.single().shutoffs.map { it.itemId }).containsExactly("a", "b").inOrder()
    }
}
