package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.ui.PlanEditorEvent
import com.kurtz.homerecord.housemodel.ui.PlanEditorScreen
import com.kurtz.homerecord.housemodel.ui.PlanEditorUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The selection panel keeps Done on screen: a tapped wall or door shows only its own controls (judge:hm-plan-inspector-clipped). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlanEditorInspectorTest {
    @get:Rule val rule = createComposeRule()

    private val door = PlanOpening("d1", edgeIndex = 0, kind = OpeningKind.DOOR, offsetIn = 40, widthIn = 32, heightIn = 80)
    private val kitchen = PlanRoom(
        "k", "Kitchen", "KITCHEN", listOf(Pt(0, 0), Pt(168, 0), Pt(168, 150), Pt(0, 150)),
        openings = listOf(door), stairs = listOf(PlanStair("s1", points = listOf(Pt(20, 20), Pt(56, 20), Pt(56, 120), Pt(20, 120)))),
    )
    private val state = PlanEditorUiState(
        loading = false,
        floors = listOf(FloorSummary("f1", "Main", ceilingHeightIn = 96, halfLevel = false, roomCount = 1)),
        floorId = "f1",
        rooms = listOf(kitchen),
        selectedId = "k",
        zoom = 1f, panX = 16f, panY = 16f,
    )

    private fun show(s: PlanEditorUiState, events: MutableList<PlanEditorEvent> = mutableListOf()) =
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { PlanEditorScreen(s, { events += it }, onBack = {}) } }

    @AcceptanceCriterion("AC-C9")
    @Test fun tappedWallShowsOnlyDoorAndWindowWithDone() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(selectedWall = 1, wallTap = Pt(168, 60)), events)
        rule.onNodeWithTag("hm_plan_add_door").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_add_window").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_room_name").assertDoesNotExist()
        rule.onNodeWithTag("hm_plan_stairs_link").assertDoesNotExist()
        rule.onNodeWithTag("hm_plan_wall_done").assertIsDisplayed().performClick()
        assertThat(events.last()).isEqualTo(PlanEditorEvent.SelectOpening(null))
    }

    @AcceptanceCriterion("AC-C9")
    @Test fun selectedDoorShowsWidthAndDeleteWithDone() {
        show(state.copy(selectedOpeningId = "d1"))
        rule.onNodeWithTag("hm_plan_opening_width").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_delete_opening").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_wall_done").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_room_kind").assertDoesNotExist()
    }

    @Test fun roomPanelKeepsItsActionsOnScreen() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state, events)
        rule.onNodeWithTag("hm_plan_room_name").assertExists()
        rule.onNodeWithTag("hm_plan_stairs_link").assertExists()
        rule.onNodeWithTag("hm_plan_done").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_delete_room").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_done").performClick()
        assertThat(events.last()).isEqualTo(PlanEditorEvent.Select(null))
    }
}
