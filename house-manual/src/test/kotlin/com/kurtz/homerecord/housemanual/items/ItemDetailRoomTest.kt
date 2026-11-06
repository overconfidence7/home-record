package com.kurtz.homerecord.housemanual.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H7: the room chip under the hero opens the item's room; overflow Move to room lists rooms and moves the item.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemDetailRoomTest {
    @get:Rule val rule = createComposeRule()

    private fun state(roomId: String, roomName: String, choices: List<MoveChoice> = emptyList()) = ItemDetailUiState(
        loading = false,
        detail = ItemDetail(
            ItemEntity(id = "dw", homeId = "h1", roomId = roomId, category = ItemCategory.APPLIANCES.name, subtype = "dishwasher", name = "Dishwasher", createdAt = 1, updatedAt = 1),
            ItemCategory.APPLIANCES, "Dishwasher", roomName, null, WarrantyStatus.Unknown, emptyList(),
        ),
        showMove = choices.isNotEmpty(), moveChoices = choices,
    )

    @Test
    @AcceptanceCriterion("AC-H7")
    fun theRoomChipOpensTheRoom() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ItemDetailScreen(state("k", "Kitchen"), onEvent = { events += it }) }
        rule.onNodeWithTag("hm_item_detail_room").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(ItemDetailEvent.OpenRoom("k"))
    }

    @Test
    fun exteriorIsALabelNotALink() {
        rule.setContent { ItemDetailScreen(state(PseudoRooms.EXTERIOR, "Exterior"), onEvent = {}) }
        rule.onNodeWithTag("hm_item_detail_room").assertIsNotEnabled()
    }

    @Test
    @AcceptanceCriterion("AC-H7")
    fun overflowOffersMoveToRoom() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { ItemDetailScreen(state("k", "Kitchen"), onEvent = { events += it }) }
        rule.onNodeWithTag("hm_item_detail_more").performClick()
        rule.onNodeWithTag("hm_item_detail_move").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.ShowMove)
    }

    @Test
    fun theSheetChecksTheCurrentRoomAndPickingAnotherMovesIt() {
        val events = mutableListOf<ItemDetailEvent>()
        val choices = listOf(MoveChoice("k", "Kitchen", "Main floor"), MoveChoice("m", "Mudroom", "Main floor"), MoveChoice(PseudoRooms.EXTERIOR, "Exterior"))
        rule.setContent { MoveRoomContent(state("k", "Kitchen", choices), onEvent = { events += it }) }
        rule.onNodeWithText("Move to which room?").assertIsDisplayed()
        rule.onNode(hasContentDescription("Here now"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("hm_item_detail_move_1").performClick()
        rule.onNodeWithTag("hm_item_detail_move_0").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.MoveTo("m"), ItemDetailEvent.HideMove).inOrder()
    }
}
