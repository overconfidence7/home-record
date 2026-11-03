package com.kurtz.homerecord.housemanual

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.blueprint.PlanMark
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.scene.MarkerTarget
import com.kurtz.homerecord.housemodel.ui.HouseScreen
import com.kurtz.homerecord.housemodel.ui.HouseUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-C15 (T055a): the pipes, wires and items the blueprint's layers draw open their record on tap, like the 3D markers. */
@AcceptanceCriterion("AC-C15")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class BlueprintMarksScreenTest {
    @get:Rule val rule = createComposeRule()

    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val pipe = PlanMark(
        MarkerTarget.OverlayMark("o1"), "k", BlueprintLayer.PLUMBING, OverlayKind.WATER_COLD,
        listOf(OverlayPoint(42f, 0f), OverlayPoint(126f, 0f)), inward = OverlayPoint(0f, 1f), label = "Sink cold supply",
    )
    private val dishwasher = PlanMark(
        MarkerTarget.ItemMark("i1"), "k", BlueprintLayer.ITEMS, null, listOf(OverlayPoint(40f, 110f)), iconName = "Kitchen", label = "Dishwasher",
    )
    private val state = HouseUiState(
        loading = false, homeName = "Maple St", floors = listOf(FloorSummary("f1", "Main floor", 108, false, 1)), floorId = "f1",
        rooms = listOf(kitchen), marks = listOf(pipe, dishwasher), layers = LayerSet(setOf(BlueprintLayer.PLUMBING, BlueprintLayer.ITEMS)),
    )

    private fun show(onOpenMark: ((PlanMark) -> Unit)?, rooms: MutableList<String> = mutableListOf()) {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "hm") {
                    HouseScreen(
                        state = state, onEvent = {}, onOpenRoom = { rooms += it.id }, onEditPlan = {}, onEditShape = {}, onOpenFloors = {},
                        modifier = Modifier.size(411.dp, 800.dp), onOpenMark = onOpenMark,
                    )
                }
            }
        }
    }

    @Test fun tappingThePipeOrTheItemOpensIt() {
        val opened = mutableListOf<MarkerTarget>()
        val rooms = mutableListOf<String>()
        show({ opened += it.target }, rooms)
        // A finger on the drawn mark: the canvas finds it under the tap.
        rule.onNodeWithContentDescription("Sink cold supply").performTouchInput { click(center) }
        rule.onNodeWithContentDescription("Dishwasher").performTouchInput { click(center) }
        assertThat(opened).containsExactly(MarkerTarget.OverlayMark("o1"), MarkerTarget.ItemMark("i1")).inOrder()
        assertThat(rooms).isEmpty()
        // TalkBack's click on the mark's node opens it too (rule 13).
        rule.onNodeWithContentDescription("Dishwasher").performClick()
        assertThat(opened.last()).isEqualTo(MarkerTarget.ItemMark("i1"))
        rule.onNodeWithText("Tap a room to open it. Press and hold for more. Tap a pipe, wire or item to open it.").assertExists()
    }

    @Test fun withoutAnOpenerMarksStayOutOfTheWayAndTheRoomOpens() {
        val rooms = mutableListOf<String>()
        show(null, rooms)
        rule.onAllNodesWithTag("hm_plan_mark").fetchSemanticsNodes().let { assertThat(it).hasSize(2) }
        rule.onNodeWithContentDescription("Dishwasher").performTouchInput { click(center) }
        assertThat(rooms).containsExactly("k")
        rule.onNodeWithText("Tap a room to open it. Press and hold for more.").assertExists()
    }
}
