package com.kurtz.homerecord.housemanual.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

// AC-C45 / AC-H7: the item page shows "Shutoff: Kitchen sink cold valve"; the chip opens the shutoff, Change and the pick row open the picker.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemDetailShutoffTest {
    @get:Rule val rule = createComposeRule()

    private val dishwasher = ItemEntity(id = "dw", homeId = "h1", category = ItemCategory.APPLIANCES.name, subtype = "dishwasher", name = "Dishwasher", createdAt = 1, updatedAt = 1)
    private val valve = ShutoffChoice("valve", "Kitchen sink cold valve", "Kitchen")
    private val main = ShutoffChoice("main", "Water main", "Utility room")

    private fun state(linked: ShutoffChoice?, choices: List<ShutoffChoice>) = ItemDetailUiState(
        loading = false, detail = ItemDetail(dishwasher, ItemCategory.APPLIANCES, "Dishwasher", "Kitchen", null, WarrantyStatus.Unknown, emptyList()),
        shutoff = linked, shutoffChoices = choices,
    )

    @Test
    @AcceptanceCriterion("AC-C45")
    fun aLinkedShutoffShowsAsAChipThatOpensIt() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ItemDetailScreen(state(valve, listOf(main, valve)), onEvent = { events += it }) }
        rule.onNodeWithText("Shutoff: Kitchen sink cold valve").performScrollTo().performClick()
        rule.onNodeWithTag("hm_item_detail_shutoff_change").performScrollTo().performClick()
        assertThat(events).containsExactly(ItemDetailEvent.OpenShutoff("valve"), ItemDetailEvent.ShowShutoffPicker).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-H7")
    fun withNoneLinkedTheRowOffersThePicker() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ItemDetailScreen(state(null, listOf(main, valve)), onEvent = { events += it }) }
        rule.onNodeWithText("2 shutoffs in this home").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("hm_item_detail_shutoff_pick").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.ShowShutoffPicker)
    }

    @Test
    fun aHomeWithNoShutoffsHidesTheSection() {
        rule.setContent { ItemDetailScreen(state(null, emptyList()), onEvent = {}) }
        rule.onNodeWithTag("hm_item_detail_shutoff").assertDoesNotExist()
    }

    @Test
    @AcceptanceCriterion("AC-C45")
    fun thePickerListsShutoffsAndNoShutoffClears() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ShutoffPickerContent(state(valve, listOf(main, valve)), onEvent = { events += it }) }
        rule.onNodeWithText("Which shutoff turns this off?").assertIsDisplayed()
        rule.onNodeWithContentDescriptionLinked()
        rule.onNodeWithTag("hm_item_detail_shutoff_option_0").performClick()
        rule.onNodeWithTag("hm_item_detail_shutoff_none").performScrollTo().performClick()
        assertThat(events).containsExactly(ItemDetailEvent.PickShutoff("main"), ItemDetailEvent.PickShutoff(null)).inOrder()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionLinked() =
        onNode(androidx.compose.ui.test.hasContentDescription("Linked"), useUnmergedTree = true).assertExists()
}
