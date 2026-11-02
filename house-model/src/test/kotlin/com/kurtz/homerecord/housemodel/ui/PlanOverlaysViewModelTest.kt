package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayDraft
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.overlays.OverlaySave
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C25, AC-C26, AC-C28: the plan editor's Overlays mode draws a run or a spot on the floor plan in plan inches, files it
// under the room it starts in, drags its points, edits and deletes it; a search result opens it picked on its floor.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlanOverlaysViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var overlays: OverlayRepository
    private lateinit var homeId: String
    private lateinit var mainFloor: String
    private lateinit var upperFloor: String
    private val media = FakeWallMedia()
    private val created = mutableListOf<PlanEditorViewModel>()

    private fun test(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        runBlocking { block() }
    }

    @Before fun setUp() = test {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        homes = HomeRepository(db, context.resources::getString)
        overlays = OverlayRepository(db)
        homeId = homes.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floors = homes.observeFloors(homeId).first { it.size >= 3 }
        mainFloor = floors.single { it.name == "Main" }.id
        upperFloor = floors.last().id
        homes.savePlan(mainFloor, listOf(
            PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144),
            PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 192, 144),
        ))
        homes.savePlan(upperFloor, listOf(PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 0, 144, 144)))
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), floorId: String? = mainFloor, overlayId: String? = null, stage: WallStage = WallStage.FINISHED) =
        PlanEditorViewModel(
            homes, homeId, saved, initialFloorId = floorId, overlays = overlays, media = media, overlayStage = stage, initialOverlayId = overlayId,
        ).also { created += it }

    private suspend fun loaded(model: PlanEditorViewModel) = withTimeout(10_000) { model.state.first { !it.loading && it.rooms.isNotEmpty() } }

    private fun PlanEditorViewModel.overlay(event: WallOverlaysEvent) = onEvent(PlanEditorEvent.OverlayEvent(event))

    private suspend fun stored() = overlays.observeForHome(homeId).first()

    /** Draws a gas line from the living room into the kitchen and saves it; returns its id. */
    private suspend fun drawGasLine(model: PlanEditorViewModel): String {
        model.onEvent(PlanEditorEvent.SetOverlayMode(true))
        model.overlay(WallOverlaysEvent.StartDrawing)
        model.overlay(WallOverlaysEvent.AddPoint(OverlayPoint(300.4f, 72f)))
        model.overlay(WallOverlaysEvent.AddPoint(OverlayPoint(120f, 71.6f)))
        model.overlay(WallOverlaysEvent.FinishDrawing)
        model.overlay(WallOverlaysEvent.SetKind(OverlayKind.GAS))
        model.overlay(WallOverlaysEvent.SetLabel("Range gas line"))
        model.overlay(WallOverlaysEvent.SetDepth(OverlayDepth.IN_SLAB))
        model.overlay(WallOverlaysEvent.Save)
        return withTimeout(10_000) { model.state.first { it.overlayForm == null && it.overlaySelectedId != null && it.overlays.isNotEmpty() } }.overlaySelectedId!!
    }

    @Test
    @AcceptanceCriterion("AC-C25", "AC-C26")
    fun aRunDrawnOnThePlanIsSavedInWholePlanInchesUnderTheRoomItStartsIn() = test {
        val model = vm(stage = WallStage.ROUGH_IN)
        loaded(model)
        val id = drawGasLine(model)
        val o = stored().single()
        assertThat(o.id).isEqualTo(id)
        assertThat(o.wallId).isNull()
        assertThat(o.roomId).isEqualTo("l")
        assertThat(o.kind).isEqualTo(OverlayKind.GAS)
        assertThat(o.label).isEqualTo("Range gas line")
        assertThat(o.depth).isEqualTo(OverlayDepth.IN_SLAB)
        assertThat(o.stage).isEqualTo(WallStage.ROUGH_IN)
        assertThat(o.points).containsExactly(OverlayPoint(300f, 72f), OverlayPoint(120f, 72f)).inOrder()
        assertThat(model.state.value.overlays.map { it.id }).containsExactly(id)
        assertThat(model.state.value.selectedOverlay?.label).isEqualTo("Range gas line")
    }

    @Test
    @AcceptanceCriterion("AC-C25")
    fun aSpotOutsideEveryRoomGoesToTheNearestRoomAndPointsAreNotClampedToAWall() = test {
        val model = vm()
        loaded(model)
        model.onEvent(PlanEditorEvent.SetOverlayMode(true))
        model.overlay(WallOverlaysEvent.StartDrawing)
        model.overlay(WallOverlaysEvent.AddPoint(OverlayPoint(60f, 190f)))
        model.overlay(WallOverlaysEvent.FinishDrawing)
        model.overlay(WallOverlaysEvent.SetKind(OverlayKind.CLEANOUT))
        model.overlay(WallOverlaysEvent.Save)
        withTimeout(10_000) { model.state.first { it.overlays.isNotEmpty() } }
        val o = stored().single()
        assertThat(o.roomId).isEqualTo("k")
        assertThat(o.points).containsExactly(OverlayPoint(60f, 190f))
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun draggingAPointMovesItAndDeleteRemovesTheOverlay() = test {
        val model = vm()
        loaded(model)
        val id = drawGasLine(model)
        model.overlay(WallOverlaysEvent.MovePoint(id, 1, OverlayPoint(96.3f, 40f)))
        assertThat(model.state.value.selectedOverlay?.points?.get(1)).isEqualTo(OverlayPoint(96f, 40f))
        withTimeout(10_000) { overlays.observeForHome(homeId).first { it.single().points[1] == OverlayPoint(96f, 40f) } }
        model.overlay(WallOverlaysEvent.AskDelete)
        assertThat(model.state.value.confirmDeleteOverlay).isTrue()
        model.overlay(WallOverlaysEvent.ConfirmDelete)
        withTimeout(10_000) { overlays.observeForHome(homeId).first { it.isEmpty() } }
        assertThat(model.state.value.overlaySelectedId).isNull()
        assertThat(model.state.value.confirmDeleteOverlay).isFalse()
    }

    @Test
    @AcceptanceCriterion("AC-C26", "AC-C28")
    fun editingKeepsThePointsAndChangesTheFields() = test {
        val model = vm()
        loaded(model)
        val id = drawGasLine(model)
        model.overlay(WallOverlaysEvent.EditSelected)
        assertThat(model.state.value.overlayForm?.editingId).isEqualTo(id)
        model.overlay(WallOverlaysEvent.SetLabel("Dryer gas line"))
        model.overlay(WallOverlaysEvent.SetKind(OverlayKind.VALVE))
        model.overlay(WallOverlaysEvent.Save)
        withTimeout(10_000) { overlays.observeForHome(homeId).first { it.single().label == "Dryer gas line" } }
        val o = stored().single()
        assertThat(o.kind).isEqualTo(OverlayKind.VALVE)
        assertThat(o.points).containsExactly(OverlayPoint(300f, 72f), OverlayPoint(120f, 72f)).inOrder()
        assertThat(o.roomId).isEqualTo("l")
    }

    @Test
    @AcceptanceCriterion("AC-C25")
    fun theFloorShowsOnlyItsOwnPlanOverlays() = test {
        overlays.addOnPlan(homeId, "b", OverlayDraft(OverlayKind.DUCT, listOf(OverlayPoint(10f, 10f), OverlayPoint(100f, 10f)), WallStage.FINISHED))
        val wallId = WallPhotoRepository(db).observeRoomWalls("k").first { it.size == 4 }.first().id
        overlays.addOnWall(homeId, wallId, OverlayDraft(OverlayKind.WATER_COLD, listOf(OverlayPoint(0.5f, 0.5f)), WallStage.FINISHED))
        val kitchenDrain = (overlays.addOnPlan(homeId, "k", OverlayDraft(OverlayKind.DRAIN, listOf(OverlayPoint(40f, 40f)), WallStage.FINISHED)) as OverlaySave.Saved).id
        val model = vm()
        val state = withTimeout(10_000) { model.state.first { !it.loading && it.overlays.isNotEmpty() } }
        assertThat(state.overlays.map { it.id }).containsExactly(kitchenDrain)
        model.onEvent(PlanEditorEvent.SelectFloor(upperFloor))
        val upper = withTimeout(10_000) { model.state.first { it.floorId == upperFloor && it.rooms.isNotEmpty() } }
        assertThat(upper.overlays.map { it.kind }).containsExactly(OverlayKind.DUCT)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun openingOnAnOverlayStartsInOverlaysModeOnItsFloorWithItPicked() = test {
        val duct = (overlays.addOnPlan(homeId, "b", OverlayDraft(OverlayKind.DUCT, listOf(OverlayPoint(10f, 10f), OverlayPoint(100f, 10f)), WallStage.FINISHED)) as OverlaySave.Saved).id
        val model = vm(floorId = null, overlayId = duct)
        val state = withTimeout(10_000) { model.state.first { !it.loading && it.selectedOverlay != null } }
        assertThat(state.floorId).isEqualTo(upperFloor)
        assertThat(state.overlayMode).isTrue()
        assertThat(state.selectedOverlay?.id).isEqualTo(duct)
    }

    @Test
    @AcceptanceCriterion("AC-C25")
    fun overlaysModeLeavesRoomsAloneAndNeedsARoomToDrawIn() = test {
        val model = vm()
        loaded(model)
        model.onEvent(PlanEditorEvent.Select("k"))
        model.onEvent(PlanEditorEvent.SetOverlayMode(true))
        assertThat(model.state.value.selectedId).isNull()
        assertThat(model.state.value.overlayMode).isTrue()
        model.onEvent(PlanEditorEvent.SetOverlayMode(false))
        assertThat(model.state.value.overlayMode).isFalse()

        homes.savePlan(upperFloor, emptyList())
        model.onEvent(PlanEditorEvent.SelectFloor(upperFloor))
        withTimeout(10_000) { model.state.first { it.floorId == upperFloor && it.rooms.isEmpty() && !it.loading } }
        model.onEvent(PlanEditorEvent.SetOverlayMode(true))
        model.overlay(WallOverlaysEvent.StartDrawing)
        assertThat(model.state.value.drawing).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C25", "AC-C26")
    fun theModeThePointsAndTheFormSurviveProcessDeath() = test {
        val saved = SavedStateHandle()
        val first = vm(saved)
        loaded(first)
        first.onEvent(PlanEditorEvent.SetOverlayMode(true))
        first.overlay(WallOverlaysEvent.StartDrawing)
        first.overlay(WallOverlaysEvent.AddPoint(OverlayPoint(20f, 20f)))
        first.overlay(WallOverlaysEvent.AddPoint(OverlayPoint(80f, 20f)))
        val again = vm(saved)
        assertThat(again.state.value.overlayMode).isTrue()
        assertThat(again.state.value.drawing).containsExactly(OverlayPoint(20f, 20f), OverlayPoint(80f, 20f)).inOrder()
        again.overlay(WallOverlaysEvent.FinishDrawing)
        again.overlay(WallOverlaysEvent.SetKind(OverlayKind.ELECTRICAL_WIRE))
        again.overlay(WallOverlaysEvent.SetLabel("Kitchen circuit"))
        val third = vm(saved)
        assertThat(third.state.value.overlayForm?.kind).isEqualTo(OverlayKind.ELECTRICAL_WIRE)
        assertThat(third.state.value.overlayForm?.label).isEqualTo("Kitchen circuit")
        assertThat(third.state.value.overlayForm?.points).hasSize(2)
    }
}
