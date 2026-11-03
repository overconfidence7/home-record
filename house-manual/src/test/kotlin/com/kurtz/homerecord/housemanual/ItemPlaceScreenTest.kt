package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.items.ItemDetailEvent
import com.kurtz.homerecord.housemanual.items.WhereSection
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.items.ItemPlace
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.ItemPlaceEvent
import com.kurtz.homerecord.housemodel.ui.ItemPlaceScreen
import com.kurtz.homerecord.housemodel.ui.ItemPlaceUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C30 position: the item page's Where row opens Place on plan; a tap on the plan places the item; Remove takes it off.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemPlaceScreenTest {
    @get:Rule val rule = createComposeRule()

    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val floors = listOf(FloorSummary("f1", "Main", 96, false, 1), FloorSummary("f0", "Basement", 96, false, 0, -108))

    private fun state(spot: ItemPlace? = null, rooms: List<PlanRoom> = listOf(kitchen)) = ItemPlaceUiState(
        loading = false, itemId = "i1", itemName = "Refrigerator", iconName = "Kitchen", floors = floors, floorId = "f1", rooms = rooms, spot = spot,
    )

    private fun show(s: ItemPlaceUiState, events: MutableList<ItemPlaceEvent>) = rule.setContent {
        HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
            CompositionLocalProvider(LocalTagPrefix provides "hm") { ItemPlaceScreen(s, { events += it }) }
        }
    }

    @Test
    @AcceptanceCriterion("AC-C30")
    fun aTapOnThePlanPlacesTheItem() {
        val events = mutableListOf<ItemPlaceEvent>()
        show(state(), events)
        rule.onNodeWithText("Tap the plan where this item sits.").assertExists()
        rule.onNodeWithTag("hm_item_place_remove").assertDoesNotExist()
        rule.onNodeWithTag("hm_item_place_plan").performTouchInput { click(center) }
        val place = events.filterIsInstance<ItemPlaceEvent.Place>().single()
        // The middle of the canvas is the middle of the only room.
        assertThat(place.xIn).isIn(60..108)
        assertThat(place.yIn).isIn(48..96)
        rule.onNodeWithTag("hm_item_place_floor_f0").performClick()
        assertThat(events).contains(ItemPlaceEvent.PickFloor("f0"))
    }

    @Test
    @AcceptanceCriterion("AC-C30")
    fun aPlacedItemSaysWhereItIsAndCanBeRemoved() {
        val events = mutableListOf<ItemPlaceEvent>()
        show(state(spot = ItemPlace("k", 40, 50)), events)
        rule.onNodeWithText("In Kitchen. Tap the plan to move it.").assertExists()
        rule.onNodeWithTag("hm_plan_mark").assertExists()
        rule.onNodeWithTag("hm_item_place_remove").performClick()
        rule.onNodeWithTag("hm_item_place_done").performClick()
        assertThat(events).containsExactly(ItemPlaceEvent.Remove, ItemPlaceEvent.Done).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C30")
    fun aFloorWithNoRoomsOffersTheEditor() {
        val events = mutableListOf<ItemPlaceEvent>()
        show(state(rooms = emptyList()), events)
        rule.onNodeWithText("No rooms on Main yet").assertExists()
        rule.onNodeWithTag("hm_item_place_edit_plan").performClick()
        assertThat(events).containsExactly(ItemPlaceEvent.EditPlan)
    }

    private fun detail(roomId: String?, roomName: String, x: Int? = null) = ItemDetail(
        item = ItemEntity(id = "i1", homeId = "h1", roomId = roomId, category = "APPLIANCES", subtype = "refrigerator", name = "Refrigerator", planX = x, planY = x, createdAt = 0, updatedAt = 0),
        category = ItemCategory.APPLIANCES, subtypeLabel = "Refrigerator", roomName = roomName, warranty = null, status = WarrantyStatus.Unknown, photos = emptyList(),
    )

    @Test
    @AcceptanceCriterion("AC-C30")
    fun theWhereRowSaysIfTheItemIsOnThePlanAndOpensPlaceOnPlan() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { WhereSection(detail("k", "Kitchen", x = 40)) { events += it } } }
        rule.onNodeWithText("Where").assertExists()
        rule.onNodeWithText("On the plan").assertExists()
        rule.onNodeWithTag("hm_item_detail_place").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.Place)
    }

    @Test
    @AcceptanceCriterion("AC-C30")
    fun anExteriorItemStaysOffThePlan() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { WhereSection(detail(PseudoRooms.EXTERIOR, "Exterior")) { events += it } } }
        rule.onNodeWithText("Not on a floor, so it stays off the plan.").assertExists()
        rule.onNodeWithTag("hm_item_detail_place").performClick()
        assertThat(events).isEmpty()
    }
}
