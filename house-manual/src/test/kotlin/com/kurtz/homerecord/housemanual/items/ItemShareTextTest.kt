package com.kurtz.homerecord.housemanual.items

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.items.ItemDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

// AC-H7 / SPEC HM-Item: the item page's Share action sends the item as plain text.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemShareTextTest {
    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val strings = { id: Int, args: Array<out Any> -> context.getString(id, *args) }

    private fun detail(make: String?, model: String?, serial: String?, status: WarrantyStatus) = ItemDetail(
        ItemEntity(id = "f", homeId = "h1", category = ItemCategory.HEATING_COOLING.name, subtype = "furnace", name = "Basement furnace", make = make, model = model, serial = serial, createdAt = 1, updatedAt = 1),
        ItemCategory.HEATING_COOLING, "Furnace", "Utility room", null, status, emptyList(),
    )

    @Test
    @AcceptanceCriterion("AC-H7")
    fun shareTextNamesTheItemItsRoomIdsAndWarranty() {
        val text = ItemShareText.build(detail("Carrier", "59TP6A060S17", "3416A12345", WarrantyStatus.Expired("2026-09-20")), strings, Locale.US)
        assertThat(text).isEqualTo(
            "Basement furnace\nFurnace, Utility room\nMake: Carrier\nModel: 59TP6A060S17\nSerial number: 3416A12345\nWarranty expired Sep 20, 2026",
        )
    }

    @Test
    fun blankFieldsAreLeftOut() {
        val text = ItemShareText.build(detail(" ", null, null, WarrantyStatus.Unknown), strings, Locale.US)
        assertThat(text).isEqualTo("Basement furnace\nFurnace, Utility room\nWarranty unknown")
    }

    @Test
    fun theTopBarShareButtonAsksToShare() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ItemDetailScreen(ItemDetailUiState(loading = false, detail = detail(null, null, null, WarrantyStatus.Unknown)), onEvent = { events += it }) }
        rule.onNodeWithTag("hm_item_detail_share").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.Share)
    }
}
