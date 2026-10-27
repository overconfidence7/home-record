package com.kurtz.homerecord.housemodel.items

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
import org.junit.Test

// AC-C30: the Type sheet's search over category and subtype (ui-standards rule 11)
class SubtypeSearchTest {
    @Test fun blankQueryListsEveryCategoryInOrder() {
        val out = SubtypeSearch.matching("  ")
        assertThat(out.map { it.first }).containsExactlyElementsIn(ItemCategory.entries).inOrder()
        assertThat(out.sumOf { it.second.size }).isEqualTo(ItemCategory.all().size)
    }

    @Test fun prefixOfASubtypeFindsIt() {
        val out = SubtypeSearch.matching("FURN")
        assertThat(out.flatMap { it.second }.map { it.id }).contains("furnace")
        assertThat(out.map { it.first }).containsExactly(ItemCategory.HEATING_COOLING)
    }

    @Test fun categoryWordListsTheWholeCategory() {
        val out = SubtypeSearch.matching("heating")
        val heating = out.single { it.first == ItemCategory.HEATING_COOLING }
        assertThat(heating.second).containsExactlyElementsIn(ItemCategory.HEATING_COOLING.subtypes)
    }

    @Test fun everyWordMustMatchAndMidWordDoesNot() {
        assertThat(SubtypeSearch.matching("range hood").flatMap { it.second }.map { it.id }).containsExactly("range_hood")
        assertThat(SubtypeSearch.matching("urnace")).isEmpty()
    }
}
