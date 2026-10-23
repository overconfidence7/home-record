package com.kurtz.homerecord.housemodel.items

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

class ItemListBuilderTest {
    private fun row(id: String, name: String, cat: String, room: String, w: WarrantyStatus = WarrantyStatus.Unknown, make: String? = null) =
        ItemRow(id, name, make, null, cat, ItemListBuilder.categoryOrder(cat), "x", "r-$room", room, w)

    private val rows = listOf(
        row("1", "Water heater", "Water", "Basement", WarrantyStatus.Expired("2024-01-01"), "Rheem"),
        row("2", "Furnace", "Heating and cooling", "Basement", WarrantyStatus.Active("2030-01-01"), "Carrier"),
        row("3", "Dishwasher", "Appliances", "Kitchen", WarrantyStatus.Expiring("2026-11-01", 9)),
        row("4", "Refrigerator", "Appliances", "Kitchen"),
    )

    @Test @AcceptanceCriterion("AC-H6")
    fun groupsByCategoryWithCountsInCategoryOrder() {
        val g = ItemListBuilder.build(rows, ItemGrouping.CATEGORY)
        assertThat(g.map { it.title }).containsExactly("Appliances", "Heating and cooling", "Water").inOrder()
        assertThat(g.first().count).isEqualTo(2)
        assertThat(g.first().items.map { it.name }).containsExactly("Dishwasher", "Refrigerator").inOrder()
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun groupsByRoomAlphabetically() {
        val g = ItemListBuilder.build(rows, ItemGrouping.ROOM)
        assertThat(g.map { it.title to it.count }).containsExactly("Basement" to 2, "Kitchen" to 2).inOrder()
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun filtersByEachWarrantyStatus() {
        fun ids(f: WarrantyFilter) = ItemListBuilder.build(rows, ItemGrouping.CATEGORY, ItemFilter(warranty = f)).flatMap { it.items }.map { it.id }
        assertThat(ids(WarrantyFilter.ACTIVE)).containsExactly("2")
        assertThat(ids(WarrantyFilter.EXPIRING)).containsExactly("3")
        assertThat(ids(WarrantyFilter.EXPIRED)).containsExactly("1")
        assertThat(ids(WarrantyFilter.UNKNOWN)).containsExactly("4")
    }

    @Test @AcceptanceCriterion("AC-H6")
    fun filtersByCategoryRoomAndQueryTogether() {
        val f = ItemFilter(categoryLabel = "Appliances", roomId = "r-Kitchen", query = "dish")
        assertThat(ItemListBuilder.build(rows, ItemGrouping.CATEGORY, f).flatMap { it.items }.map { it.id }).containsExactly("3")
        assertThat(ItemListBuilder.build(rows, ItemGrouping.CATEGORY, ItemFilter(query = "rheem basement")).flatMap { it.items }.map { it.id }).containsExactly("1")
        assertThat(ItemListBuilder.build(rows, ItemGrouping.CATEGORY, ItemFilter(query = "zzz"))).isEmpty()
    }
}
