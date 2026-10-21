package com.kurtz.homerecord.housemanual.acceptance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.house.HouseDialog
import com.kurtz.homerecord.housemanual.house.HouseEvent
import com.kurtz.homerecord.housemanual.house.HouseScreen
import com.kurtz.homerecord.housemanual.house.HouseUiState
import com.kurtz.homerecord.housemanual.plan.PlanEditorEvent
import com.kurtz.homerecord.housemanual.plan.PlanEditorScreen
import com.kurtz.homerecord.housemanual.plan.PlanEditorUiState
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanAndBlueprintAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val basement = FloorSummary("f-basement", "Basement", 96, false, 0)
    private val main = FloorSummary("f-main", "Main", 108, false, 2)
    private val kitchen = PlanRoom.rectangle("r-kitchen", "Kitchen", "KITCHEN", 0, 0, 144, 120)
    private val living = PlanRoom.rectangle("r-living", "Living room", "LIVING_ROOM", 144, 0, 216, 180)

    private fun editor(state: PlanEditorUiState, events: MutableList<PlanEditorEvent>) = rule.setContent {
        HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
            PlanEditorScreen(state, onEvent = { events += it }, onBack = {})
        }
    }

    private val base = PlanEditorUiState(
        loading = false,
        floors = listOf(basement, main),
        floorId = main.id,
        rooms = listOf(kitchen, living),
        zoom = 0.5f,
    )

    @AcceptanceCriterion("AC-C5")
    @Test fun floorSwitcherIsAlwaysVisibleAndSwitchesFloors() {
        val events = mutableListOf<PlanEditorEvent>()
        editor(base, events)
        rule.onNodeWithTag("hm_plan_canvas").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_floor_f-main").assertIsDisplayed()
        rule.onNodeWithTag("hm_plan_floor_f-basement").performClick()
        assertThat(events).contains(PlanEditorEvent.SelectFloor("f-basement"))
    }

    @AcceptanceCriterion("AC-C6")
    @Test fun addRoomThenNameKindAndSizeFieldsAppearForTheSelection() {
        val events = mutableListOf<PlanEditorEvent>()
        editor(base, events)
        rule.onNodeWithTag("hm_plan_add_room").performClick()
        assertThat(events.filterIsInstance<PlanEditorEvent.AddRoom>()).hasSize(1)
    }

    @AcceptanceCriterion("AC-C6")
    @Test fun nameKindAndSizeFieldsAppearForTheSelection() {
        val events = mutableListOf<PlanEditorEvent>()
        editor(base.copy(selectedId = kitchen.id, focusName = true), events)
        listOf("hm_plan_room_name", "hm_plan_room_kind", "hm_plan_room_width", "hm_plan_room_length").forEach {
            rule.onNodeWithTag(it).assertExists()
        }
    }

    @AcceptanceCriterion("AC-U6")
    @Test fun deletingARoomAsksFirst() {
        val events = mutableListOf<PlanEditorEvent>()
        editor(base.copy(selectedId = kitchen.id), events)
        rule.onNodeWithTag("hm_plan_delete_room").performClick()
        assertThat(events).doesNotContain(PlanEditorEvent.Delete)
        rule.onNodeWithText("Delete").performClick()
        assertThat(events).contains(PlanEditorEvent.Delete)
    }

    @AcceptanceCriterion("AC-C7")
    @Test fun editShapeIsOfferedForTheSelectedRoom() {
        val events = mutableListOf<PlanEditorEvent>()
        editor(base.copy(selectedId = kitchen.id), events)
        rule.onNodeWithTag("hm_plan_edit_shape").performClick()
        assertThat(events).contains(PlanEditorEvent.SetEditShape(true))
    }

    @AcceptanceCriterion("AC-C9")
    @Test fun tappedWallOffersDoorAndWindow() {
        val events = mutableListOf<PlanEditorEvent>()
        editor(base.copy(selectedId = kitchen.id, selectedWall = 0, wallTap = Pt(72, 0)), events)
        rule.onNodeWithTag("hm_plan_add_door").performClick()
        rule.onNodeWithTag("hm_plan_add_window").performClick()
        assertThat(events).containsAtLeast(PlanEditorEvent.AddOpening(OpeningKind.DOOR), PlanEditorEvent.AddOpening(OpeningKind.WINDOW))
    }

    @AcceptanceCriterion("AC-C10")
    @Test fun undoAndRedoFollowTheHistoryAndSendEvents() {
        val events = mutableListOf<PlanEditorEvent>()
        editor(base.copy(canUndo = true, canRedo = false), events)
        rule.onNodeWithTag("hm_plan_undo").assertIsEnabled().performClick()
        rule.onNodeWithTag("hm_plan_redo").assertIsNotEnabled()
        assertThat(events).contains(PlanEditorEvent.Undo)
    }

    private fun house(state: HouseUiState, events: MutableList<HouseEvent>, onEdit: () -> Unit = {}) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "hm") {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                HouseScreen(state, onEvent = { events += it }, onOpenRoom = {}, onEditPlan = onEdit, onEditShape = {}, onOpenFloors = {})
            }
        }
    }

    private val houseState = HouseUiState(
        loading = false,
        homeName = "Maple Street",
        summary = "2 floors, 2 rooms",
        floors = listOf(basement, main),
        floorId = main.id,
        rooms = listOf(kitchen, living),
    )

    @AcceptanceCriterion("AC-C13")
    @Test fun blueprintShowsFloorChipsAndEditPlan() {
        var edited = false
        house(houseState, mutableListOf(), onEdit = { edited = true })
        rule.onNodeWithTag("hm_house_floor_f-main").assertIsDisplayed()
        rule.onNodeWithTag("hm_house_edit_plan").performClick()
        assertThat(edited).isTrue()
    }

    @AcceptanceCriterion("AC-C15")
    @Test fun everyLayerHasAToggleChip() {
        val events = mutableListOf<HouseEvent>()
        house(houseState, events)
        BlueprintLayer.entries.forEach { rule.onNodeWithTag("hm_layer_${it.wire}").assertExists() }
        rule.onNodeWithTag("hm_layer_${BlueprintLayer.PLUMBING.wire}").performClick()
        assertThat(events).contains(HouseEvent.ToggleLayer(BlueprintLayer.PLUMBING))
    }

    @AcceptanceCriterion("AC-C14")
    @Test fun longPressSheetOffersRenameEditShapeAndDelete() {
        val events = mutableListOf<HouseEvent>()
        house(houseState.copy(dialog = HouseDialog.Menu(kitchen)), events)
        rule.onNodeWithText("Edit shape").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onNodeWithText("Rename").performClick()
        assertThat(events).contains(HouseEvent.StartRename(kitchen))
    }
}
