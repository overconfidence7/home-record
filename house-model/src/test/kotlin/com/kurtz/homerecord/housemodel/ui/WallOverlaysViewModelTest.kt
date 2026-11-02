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
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.PhotoPoint
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallSide
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

// AC-C25, AC-C26, AC-C27, AC-C28: HM-Wall draws an overlay by tapping points, saves it with kind, label, note, depth and stage
// in wall space, keeps it in place on every stage's picture, drags its points and deletes it.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WallOverlaysViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var wallPhotos: WallPhotoRepository
    private lateinit var overlays: OverlayRepository
    private val maker = FakeTextureMaker()
    private val created = mutableListOf<WallOverlaysViewModel>()
    private lateinit var northWall: String

    private fun test(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        runBlocking { block() }
    }

    @Before fun setUp() = test {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        homes = HomeRepository(db, context.resources::getString)
        wallPhotos = WallPhotoRepository(db)
        overlays = OverlayRepository(db)
        val homeId = homes.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floor = homes.observeFloors(homeId).first { it.isNotEmpty() }.single { it.name == "Main" }
        homes.savePlan(floor.id, listOf(PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)))
        northWall = wallPhotos.observeRoomWalls("k").first { it.size == 4 }.first { it.side == WallSide.NORTH }.id
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(stage: WallStage = WallStage.ROUGH_IN, saved: SavedStateHandle = SavedStateHandle(), select: String? = null) =
        WallOverlaysViewModel(homes, wallPhotos, overlays, maker, "k", northWall, stage, select, saved).also { created += it }

    private suspend fun loaded(model: WallOverlaysViewModel) = withTimeout(10_000) { model.state.first { !it.loading } }

    private suspend fun stored() = overlays.observeForWall(northWall).first()

    /** Draws a three point hot water run and saves it with its fields; returns the new overlay's id. */
    private suspend fun drawPipe(model: WallOverlaysViewModel): String {
        model.onEvent(WallOverlaysEvent.StartDrawing)
        model.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(0.2f, 0.9f)))
        model.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(0.2f, 0.5f)))
        model.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(0.6f, 0.5f)))
        model.onEvent(WallOverlaysEvent.FinishDrawing)
        model.onEvent(WallOverlaysEvent.SetKind(OverlayKind.WATER_HOT))
        model.onEvent(WallOverlaysEvent.SetLabel("Sink supply"))
        model.onEvent(WallOverlaysEvent.SetNote("PEX, 1/2 in"))
        model.onEvent(WallOverlaysEvent.SetDepth(OverlayDepth.BEHIND_DRYWALL))
        model.onEvent(WallOverlaysEvent.Save)
        return withTimeout(10_000) { model.state.first { it.form == null && it.selectedId != null && it.overlays.isNotEmpty() } }.selectedId!!
    }

    @Test
    @AcceptanceCriterion("AC-C25", "AC-C26")
    fun drawingTapsThenSavingStoresARunInWallSpaceWithItsFields() = test {
        val model = vm()
        loaded(model)
        val id = drawPipe(model)
        val o = stored().single()
        assertThat(o.id).isEqualTo(id)
        assertThat(o.kind).isEqualTo(OverlayKind.WATER_HOT)
        assertThat(o.label).isEqualTo("Sink supply")
        assertThat(o.note).isEqualTo("PEX, 1/2 in")
        assertThat(o.depth).isEqualTo(OverlayDepth.BEHIND_DRYWALL)
        assertThat(o.stage).isEqualTo(WallStage.ROUGH_IN)
        assertThat(o.wallId).isEqualTo(northWall)
        assertThat(o.roomId).isEqualTo("k")
        assertThat(o.points).containsExactly(OverlayPoint(0.2f, 0.9f), OverlayPoint(0.2f, 0.5f), OverlayPoint(0.6f, 0.5f)).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C25")
    fun oneTapMakesASpotAndUndoAndCancelDropPoints() = test {
        val model = vm()
        loaded(model)
        model.onEvent(WallOverlaysEvent.StartDrawing)
        model.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(0.5f, 0.5f)))
        model.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(1.4f, -0.2f)))
        assertThat(model.state.value.drawing).containsExactly(OverlayPoint(0.5f, 0.5f), OverlayPoint(1f, 0f)).inOrder()
        model.onEvent(WallOverlaysEvent.UndoPoint)
        model.onEvent(WallOverlaysEvent.FinishDrawing)
        assertThat(model.state.value.form?.points).containsExactly(OverlayPoint(0.5f, 0.5f))
        model.onEvent(WallOverlaysEvent.CloseForm)
        model.onEvent(WallOverlaysEvent.StartDrawing)
        model.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(0.1f, 0.1f)))
        model.onEvent(WallOverlaysEvent.CancelDrawing)
        assertThat(model.state.value.drawing).isNull()
        assertThat(model.state.value.form).isNull()
        assertThat(stored()).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun saveWithoutAKindShowsTheErrorAndSavesNothing() = test {
        val model = vm()
        loaded(model)
        model.onEvent(WallOverlaysEvent.StartDrawing)
        model.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(0.5f, 0.5f)))
        model.onEvent(WallOverlaysEvent.FinishDrawing)
        model.onEvent(WallOverlaysEvent.Save)
        assertThat(model.state.value.form?.triedSave).isTrue()
        assertThat(model.state.value.form?.canSave).isFalse()
        assertThat(stored()).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C27")
    fun anOverlayDrawnAtRoughInStaysPutWhenTheWallSwitchesToFinished() = test {
        val roughIn = WallOutline(PhotoPoint(0.1f, 0.1f), PhotoPoint(0.9f, 0.15f), PhotoPoint(0.88f, 0.9f), PhotoPoint(0.12f, 0.85f))
        wallPhotos.save(northWall, WallStage.ROUGH_IN, "media/rough.jpg", "media/rough-wall.jpg", roughIn)
        wallPhotos.save(northWall, WallStage.FINISHED, "media/done.jpg", null, WallOutline.Initial)
        val model = vm()
        loaded(model)
        withTimeout(10_000) { model.state.first { it.picture != null } }
        assertThat(model.state.value.picture?.outline).isNull()
        val id = drawPipe(model)
        val before = model.state.value.overlays.single { it.id == id }.points
        model.onEvent(WallOverlaysEvent.SelectStage(WallStage.FINISHED))
        val finished = withTimeout(10_000) { model.state.first { it.picture?.path == "media/done.jpg" } }
        // The Finished photo has no straightened copy, so it is drawn through its own outline; the points stay in wall space.
        assertThat(finished.picture?.outline).isEqualTo(WallOutline.Initial)
        assertThat(finished.overlays.single { it.id == id }.points).isEqualTo(before)
        assertThat(finished.overlays.single { it.id == id }.stage).isEqualTo(WallStage.ROUGH_IN)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun draggingAPointMovesItAndDeleteRemovesTheOverlay() = test {
        val model = vm()
        loaded(model)
        val id = drawPipe(model)
        model.onEvent(WallOverlaysEvent.MovePoint(id, 2, OverlayPoint(0.8f, 0.4f)))
        assertThat(model.state.value.overlays.single().points[2]).isEqualTo(OverlayPoint(0.8f, 0.4f))
        withTimeout(10_000) { overlays.observeForWall(northWall).first { it.singleOrNull()?.points?.get(2) == OverlayPoint(0.8f, 0.4f) } }
        model.onEvent(WallOverlaysEvent.AskDelete)
        assertThat(model.state.value.confirmDelete).isTrue()
        model.onEvent(WallOverlaysEvent.ConfirmDelete)
        withTimeout(10_000) { overlays.observeForWall(northWall).first { it.isEmpty() } }
        assertThat(model.state.value.selectedId).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun editChangesTheFieldsAndKeepsThePoints() = test {
        val model = vm()
        loaded(model)
        val id = drawPipe(model)
        model.onEvent(WallOverlaysEvent.EditSelected)
        val form = model.state.value.form
        assertThat(form?.editingId).isEqualTo(id)
        assertThat(form?.label).isEqualTo("Sink supply")
        assertThat(form?.dirty).isFalse()
        model.onEvent(WallOverlaysEvent.SetKind(OverlayKind.WATER_COLD))
        model.onEvent(WallOverlaysEvent.SetFormStage(WallStage.FINISHED))
        model.onEvent(WallOverlaysEvent.Save)
        val saved = withTimeout(10_000) { overlays.observeForWall(northWall).first { it.singleOrNull()?.kind == OverlayKind.WATER_COLD } }.single()
        assertThat(saved.stage).isEqualTo(WallStage.FINISHED)
        assertThat(saved.points).hasSize(3)
        assertThat(saved.label).isEqualTo("Sink supply")
    }

    @Test
    fun theDrawingTheFormAndTheStageSurviveProcessDeath() = test {
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        loaded(first)
        first.onEvent(WallOverlaysEvent.SelectStage(WallStage.FINISHED))
        first.onEvent(WallOverlaysEvent.StartDrawing)
        first.onEvent(WallOverlaysEvent.AddPoint(OverlayPoint(0.3f, 0.3f)))
        val second = vm(saved = saved)
        assertThat(second.state.value.stage).isEqualTo(WallStage.FINISHED)
        assertThat(second.state.value.drawing).containsExactly(OverlayPoint(0.3f, 0.3f))
        second.onEvent(WallOverlaysEvent.FinishDrawing)
        second.onEvent(WallOverlaysEvent.SetLabel("Outlet by the sink"))
        second.onEvent(WallOverlaysEvent.OpenSheet(OverlayFormSheet.KIND))
        val third = vm(saved = saved).state.value.form
        assertThat(third?.label).isEqualTo("Outlet by the sink")
        assertThat(third?.stage).isEqualTo(WallStage.FINISHED)
        assertThat(third?.sheet).isEqualTo(OverlayFormSheet.KIND)
        assertThat(third?.dirty).isTrue()
    }

    @Test
    fun opensWithTheOverlayPickedFromTheRoomPageAndPlainWallWithoutAPhoto() = test {
        val draft = com.kurtz.homerecord.housemodel.overlays.OverlayDraft(OverlayKind.OUTLET, listOf(OverlayPoint(0.5f, 0.8f)), WallStage.FINISHED)
        val homeId = homes.roomLocation("k")!!.homeId
        val id = (overlays.addOnWall(homeId, northWall, draft) as com.kurtz.homerecord.housemodel.overlays.OverlaySave.Saved).id
        val model = vm(stage = WallStage.FINISHED, select = id)
        val state = withTimeout(10_000) { model.state.first { !it.loading && it.overlays.isNotEmpty() } }
        assertThat(state.selected?.kind).isEqualTo(OverlayKind.OUTLET)
        assertThat(state.picture).isNull()
        assertThat(state.aspect).isWithin(0.001f).of(168f / state.wall!!.heightIn)
    }
}
