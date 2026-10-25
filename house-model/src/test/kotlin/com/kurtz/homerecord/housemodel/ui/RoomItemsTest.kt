package com.kurtz.homerecord.housemodel.ui

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemodel.items.ItemRow
import org.junit.Test

class RoomItemsTest {
    private fun row(id: String, name: String, room: String?, make: String? = null, model: String? = null) = ItemRow(
        id = id, name = name, make = make, model = model, categoryLabel = "Appliances", categoryOrder = 0,
        subtypeId = "dishwasher", roomId = room, roomName = "", warranty = WarrantyStatus.Unknown,
    )

    // AC-H10
    @Test fun listsOnlyThisRoomsItemsByName() {
        val rows = listOf(row("1", "Range", "k"), row("2", "Dishwasher", "k", "Bosch", "SHP"), row("3", "Washer", "l"))
        val out = roomItemsFor(rows, "k")
        assertThat(out.map { it.name }).containsExactly("Dishwasher", "Range").inOrder()
        assertThat(out[0].detail).isEqualTo("Bosch SHP")
        assertThat(out[1].detail).isEqualTo("Appliances")
    }
}
