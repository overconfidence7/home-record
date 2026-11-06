package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.ui.RoomEvent
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.RoomUiState
import com.kurtz.homerecord.housemodel.ui.roomItemsFor
import com.kurtz.homerecord.housemodel.ui.roomShutoffsFor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C45: the room page's "Shutoffs here" lists the room's shutoff items, mains first; a row opens the shutoff.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomShutoffsTest {
    @get:Rule val rule = createComposeRule()

    private val kitchen = PlanRoom(id = "k", name = "Kitchen", points = listOf(Pt(0, 0), Pt(168, 0), Pt(168, 144), Pt(0, 144)))

    private fun row(id: String, name: String, sub: String, cat: ItemCategory = ItemCategory.SHUTOFFS, room: String = "k") =
        ItemRow(id, name, null, null, cat, sub, room, "Kitchen", WarrantyStatus.Unknown)

    @Test
    @AcceptanceCriterion("AC-C45")
    fun shutoffsHereListsTheRoomsShutoffsMainsFirstAndOpensOne() {
        val rows = listOf(
            row("a", "A sink valve", "fixture_shutoff"), row("wm", "Water main", "water_main"),
            row("fridge", "Refrigerator", "refrigerator", ItemCategory.APPLIANCES), row("other", "Bath valve", "fixture_shutoff", room = "bath"),
        )
        val shutoffs = roomShutoffsFor(roomItemsFor(rows, "k"))
        assertThat(shutoffs.map { it.id }).containsExactly("wm", "a").inOrder()
        val events = mutableListOf<RoomEvent>()
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                RoomScreen(RoomUiState(loading = false, room = kitchen, shutoffs = shutoffs), onEvent = { events += it })
            }
        }
        rule.onNodeWithText("Shutoffs here").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("hm_room_shutoff_1").performScrollTo().performClick()
        assertThat(events).containsExactly(RoomEvent.OpenItem("a"))
    }

    @Test
    fun aRoomWithoutShutoffsSaysHowToAddOne() {
        rule.setContent { RoomScreen(RoomUiState(loading = false, room = kitchen, shutoffs = emptyList()), onEvent = {}) }
        rule.onNodeWithText("No shutoffs in this room. Add the valve under the sink or the water main as an item here.").performScrollTo().assertIsDisplayed()
    }
}
