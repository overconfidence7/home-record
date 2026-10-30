package com.kurtz.homerecord.housemodel.items

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

class ItemListBuilderTest {
    private fun row(id: String, name: String, cat: ItemCategory, room: String, w: WarrantyStatus = WarrantyStatus.Unknown, make: String? = null) =
        ItemRow(id, name, make, null, cat, "x", "r-$room", room, w)

    /** Stands in for the string resources the app reads; a search must match what the screen shows. */
    private val names = mapOf(ItemCategory.WATER to "Water", ItemCategory.HEATING_COOLING to "Heating and cooling", ItemCategory.APPLIANCES to "Appliances")
    private fun build(grouping: ItemGrouping, filter: ItemFilter = ItemFilter()) = ItemListBuilder.build(rows, grouping, filter) { names.getValue(it) }

    private val rows = listOf(
        row("1", "Water heater", ItemCategory.WATER, "Basement", WarrantyStatus.Expired("2024-01-01"), "Rheem"),
        row("2", "Furnace", ItemCategory.HEATING_COOLING, "Basement", WarrantyStatus.Active("2030-01-01"), "Carrier"),
        row("3", "Dishwasher", ItemCategory.APPLIANCES, "Kitchen", WarrantyStatus.Expiring("2026-11-01", 9)),
        row("4", "Refrigerator", ItemCategory.APPLIANCES, "Kitchen"),
    )

    @Test @AcceptanceCriterion("AC-H6")
    fun groupsByCategoryWithCountsInCategoryOrder() {
        val g = build(ItemGrouping.CATEGORY)
        assertThat(g.map { it.category }).containsExactly(ItemCategory.APPLIANCES, ItemCategory.HEATING_COOLING, ItemCategory.WATER).inOrder()
        // Keys are enum ids, never display text (cr:label-strings)
        assertThat(g.map { it.key }).containsExactly("APPLIANCES", "HEATING_COOLING", "WATER").inOrder()
        assertThat(g.first().count).isEqualTo(2)
        assertThat(g.first().items.map { it.name }).containsExactly("Dishwasher", "Refrigerator").inOrder()
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun groupsByRoomAlphabetically() {
        val g = build(ItemGrouping.ROOM)
        assertThat(g.map { it.title to it.count }).containsExactly("Basement" to 2, "Kitchen" to 2).inOrder()
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun filtersByEachWarrantyStatus() {
        fun ids(f: WarrantyFilter) = build(ItemGrouping.CATEGORY, ItemFilter(warranty = f)).flatMap { it.items }.map { it.id }
        assertThat(ids(WarrantyFilter.ACTIVE)).containsExactly("2")
        assertThat(ids(WarrantyFilter.EXPIRING)).containsExactly("3")
        assertThat(ids(WarrantyFilter.EXPIRED)).containsExactly("1")
        assertThat(ids(WarrantyFilter.UNKNOWN)).containsExactly("4")
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun filtersByCategoryRoomAndQueryTogether() {
        val f = ItemFilter(category = ItemCategory.APPLIANCES, roomId = "r-Kitchen", query = "dish")
        assertThat(build(ItemGrouping.CATEGORY, f).flatMap { it.items }.map { it.id }).containsExactly("3")
        assertThat(build(ItemGrouping.CATEGORY, ItemFilter(query = "rheem basement")).flatMap { it.items }.map { it.id }).containsExactly("1")
        assertThat(build(ItemGrouping.CATEGORY, ItemFilter(query = "zzz"))).isEmpty()
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun searchMatchesTheCategoryNameShown() {
        assertThat(build(ItemGrouping.CATEGORY, ItemFilter(query = "heating")).flatMap { it.items }.map { it.id }).containsExactly("2")
        val french = ItemListBuilder.build(rows, ItemGrouping.CATEGORY, ItemFilter(query = "chauffage")) {
            if (it == ItemCategory.HEATING_COOLING) "Chauffage et climatisation" else it.name
        }
        assertThat(french.flatMap { it.items }.map { it.id }).containsExactly("2")
    }
}
