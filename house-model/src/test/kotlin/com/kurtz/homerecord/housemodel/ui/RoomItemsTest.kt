package com.kurtz.homerecord.housemodel.ui

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemodel.items.ItemRow
import org.junit.Test

class RoomItemsTest {
    private fun row(id: String, name: String, room: String?, make: String? = null, model: String? = null) = ItemRow(
        id = id, name = name, make = make, model = model, category = ItemCategory.APPLIANCES,
        subtypeId = "dishwasher", roomId = room, roomName = "", warranty = WarrantyStatus.Unknown,
    )

    // AC-H10
    @Test fun listsOnlyThisRoomsItemsByName() {
        val rows = listOf(row("1", "Range", "k"), row("2", "Dishwasher", "k", "Bosch", "SHP"), row("3", "Washer", "l"))
        val out = roomItemsFor(rows, "k")
        assertThat(out.map { it.name }).containsExactly("Dishwasher", "Range").inOrder()
        assertThat(out[0].detail).isEqualTo("Bosch SHP")
        assertThat(out[1].detail).isEmpty()
        assertThat(out[1].category).isEqualTo(ItemCategory.APPLIANCES)
    }

    // AC-H10: rows carry the subtype icon and the warranty status the Items tab shows
    @Test fun rowsCarryIconAndWarranty() {
        val active = WarrantyStatus.Active("2028-03-14")
        val out = roomItemsFor(listOf(row("1", "Dishwasher", "k").copy(warranty = active), row("2", "Thing", "k").copy(subtypeId = "no_such_type")), "k")
        assertThat(out[0].iconName).isEqualTo(com.kurtz.homerecord.core.model.ItemCategory.subtype("dishwasher")?.iconName)
        assertThat(out[0].warranty).isEqualTo(active)
        assertThat(out[1].iconName).isEqualTo("Category")
        assertThat(out[1].warranty).isEqualTo(WarrantyStatus.Unknown)
    }
}
