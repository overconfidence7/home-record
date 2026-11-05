package com.kurtz.homerecord.housemanual.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.LinkConfidence
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.panel.FeedingBreaker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C41 / AC-H7: an item fed by a breaker shows "Breaker 14" on its page; the chip opens that breaker's panel.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemDetailPowerTest {
    @get:Rule val rule = createComposeRule()

    private val fridge = ItemEntity(id = "fridge", homeId = "h1", category = ItemCategory.APPLIANCES.name, subtype = "refrigerator", name = "Refrigerator", createdAt = 1, updatedAt = 1)

    private fun fed(id: String, slot: Int, panelItem: String, panel: String) = FeedingBreaker(
        Breaker(id, "p-$panelItem", listOf(slot), 1, 20, BreakerType.STANDARD, "Refrigerator", LinkConfidence.LIKELY), panelItem, panel, LinkConfidence.LIKELY,
    )

    private fun show(feeding: List<FeedingBreaker>, events: MutableList<ItemDetailEvent>) = rule.setContent {
        ItemDetailScreen(ItemDetailUiState(loading = false, detail = ItemDetail(fridge, ItemCategory.APPLIANCES, "Refrigerator", "Kitchen", null, WarrantyStatus.Unknown, emptyList()), feeding = feeding), onEvent = { events += it })
    }

    @Test
    @AcceptanceCriterion("AC-C41")
    fun anItemFedByOneBreakerShowsItsNumberAndTheChipOpensThatBreaker() {
        val events = mutableListOf<ItemDetailEvent>()
        val b = fed("b12", 12, "panel", "Main panel")
        show(listOf(b), events)
        rule.onNodeWithTag("hm_item_detail_power").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Breaker 12").performClick()
        assertThat(events).contains(ItemDetailEvent.OpenBreaker(b))
    }

    @Test
    @AcceptanceCriterion("AC-H7")
    fun anItemFedFromTwoPanelsNamesThePanelOnEachChip() {
        show(listOf(fed("b12", 12, "main", "Main panel"), fed("g3", 3, "garage", "Garage sub-panel")), mutableListOf())
        rule.onNodeWithText("Main panel, breaker 12").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Garage sub-panel, breaker 3").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anItemNoBreakerFeedsHasNoPowerSection() {
        show(emptyList(), mutableListOf())
        rule.onNodeWithTag("hm_item_detail_power").assertDoesNotExist()
    }
}
