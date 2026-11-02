package com.kurtz.homerecord.housemanual

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.OverlayForm
import com.kurtz.homerecord.housemodel.ui.PlanEditorEvent
import com.kurtz.homerecord.housemodel.ui.PlanEditorScreen
import com.kurtz.homerecord.housemodel.ui.PlanEditorUiState
import com.kurtz.homerecord.housemodel.ui.WallOverlaysEvent
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The plan editor's Overlays mode (AC-C25, AC-C28): switch modes, draw by taps, pick an overlay, drag its point, delete it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlanEditorOverlaysScreenTest {
    @get:Rule val rule = createComposeRule()

    private val gas = Overlay(
        "g1", "h", "k", null, OverlayKind.GAS, "Range gas line", null, OverlayDepth.IN_SLAB,
        listOf(OverlayPoint(20f, 30f), OverlayPoint(150f, 30f)), WallStage.ROUGH_IN, null, null, 0,
    )

    private val state = PlanEditorUiState(
        loading = false,
        floors = listOf(FloorSummary("f1", "Main", ceilingHeightIn = 96, halfLevel = false, roomCount = 1)),
        floorId = "f1",
        rooms = listOf(PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)),
        zoom = 1f, panX = 16f, panY = 16f,
        canEditOverlays = true,
    )

    private fun show(s: PlanEditorUiState, events: MutableList<PlanEditorEvent>) =
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { PlanEditorScreen(s, { events += it }, onBack = {}) } }

    private fun List<PlanEditorEvent>.overlayEvents() = filterIsInstance<PlanEditorEvent.OverlayEvent>().map { it.event }

    @Test @AcceptanceCriterion("AC-C25")
    fun theModeSwitchTurnsOverlaysOn() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state, events)
        rule.onNodeWithTag("hm_plan_mode_overlays").performClick()
        assertThat(events.last()).isEqualTo(PlanEditorEvent.SetOverlayMode(true))
    }

    @Test @AcceptanceCriterion("AC-C25")
    fun inOverlaysModeAddOverlayStartsDrawing() {
        val overlays = mutableListOf<PlanEditorEvent>()
        show(state.copy(overlayMode = true), overlays)
        rule.onNodeWithTag("hm_plan_overlays_hint").assertExists()
        rule.onNodeWithTag("hm_plan_add_overlay").performClick()
        assertThat(overlays.overlayEvents()).containsExactly(WallOverlaysEvent.StartDrawing)
    }

    @Test @AcceptanceCriterion("AC-C25")
    fun whileDrawingATapOnThePlanAddsAPointInPlanInches() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(overlayMode = true, drawing = emptyList()), events)
        rule.onNodeWithTag("hm_plan_draw_hint").assertExists()
        // 40 x 60 in on the plan: zoom 1 dp per inch, panned 16 dp.
        rule.onNodeWithTag("hm_plan_canvas").performTouchInput { click(Offset(16f * density + 40f * density, 16f * density + 60f * density)) }
        val added = events.overlayEvents().filterIsInstance<WallOverlaysEvent.AddPoint>().single().point
        assertThat(added.x).isWithin(0.5f).of(40f)
        assertThat(added.y).isWithin(0.5f).of(60f)
        rule.onNodeWithTag("hm_plan_draw_cancel").performClick()
        assertThat(events.overlayEvents().last()).isEqualTo(WallOverlaysEvent.CancelDrawing)
    }

    @Test @AcceptanceCriterion("AC-C28")
    fun anOverlayIsANamedNodeThatPicksIt() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(overlayMode = true, overlays = listOf(gas)), events)
        rule.onNodeWithContentDescription("Range gas line").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events.overlayEvents().last()).isEqualTo(WallOverlaysEvent.Select("g1"))
    }

    @Test @AcceptanceCriterion("AC-C28")
    fun aPickedOverlayDragsItsPointAndOffersEditAndDelete() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(overlayMode = true, overlays = listOf(gas), overlaySelectedId = "g1"), events)
        rule.onNodeWithTag("hm_plan_overlay_g1").assertIsSelected()
        rule.onNodeWithTag("hm_plan_canvas").performTouchInput {
            val from = Offset((16f + 150f) * density, (16f + 30f) * density)
            swipe(from, from + Offset(0f, 40f * density), 300)
        }
        val moved = events.overlayEvents().filterIsInstance<WallOverlaysEvent.MovePoint>().single()
        assertThat(moved.id).isEqualTo("g1")
        assertThat(moved.index).isEqualTo(1)
        assertThat(moved.to.y).isWithin(1f).of(70f)
        rule.onNodeWithTag("hm_plan_overlay_edit").performClick()
        rule.onNodeWithTag("hm_plan_overlay_delete").performClick()
        assertThat(events.overlayEvents()).containsAtLeast(WallOverlaysEvent.EditSelected, WallOverlaysEvent.AskDelete).inOrder()
    }

    @Test @AcceptanceCriterion("AC-C28")
    fun deleteAsksFirst() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(overlayMode = true, overlays = listOf(gas), overlaySelectedId = "g1", confirmDeleteOverlay = true), events)
        rule.onNodeWithTag("hm_plan_overlay_delete_confirm").performClick()
        assertThat(events.overlayEvents().last()).isEqualTo(WallOverlaysEvent.ConfirmDelete)
    }

    @Test @AcceptanceCriterion("AC-C26")
    fun anOpenFormShowsInPlaceOfThePlan() {
        val events = mutableListOf<PlanEditorEvent>()
        show(state.copy(overlayMode = true, overlayForm = OverlayForm(points = listOf(OverlayPoint(10f, 10f)))), events)
        rule.onNodeWithTag("hm_overlay_form_kind").assertExists()
        rule.onNodeWithTag("hm_plan_canvas").assertDoesNotExist()
    }
}
