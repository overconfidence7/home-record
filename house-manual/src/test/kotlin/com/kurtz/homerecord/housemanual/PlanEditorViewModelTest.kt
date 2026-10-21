package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemanual.plan.PlanEditorEvent
import com.kurtz.homerecord.housemanual.plan.PlanEditorViewModel
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.plan.RoomKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.kurtz.homerecord.housemodel.FloorPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlanEditorViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private var ids = 0

    /** Real time on purpose: Room answers on its own threads. */
    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HomeRepository(db)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(handle: SavedStateHandle = SavedStateHandle()): PlanEditorViewModel {
        val homeId = repo.observeHome().let { f ->
            f.first()?.id ?: repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, true))
        }
        return PlanEditorViewModel(repo, homeId, handle, newId = { "room-${ids++}" }).also { vm ->
            withTimeout(10_000) { vm.state.first { !it.loading } }
        }
    }

    /** Room runs queries on its own threads, so saves land a moment after the event. */
    private suspend fun savedPlan(vm: PlanEditorViewModel, until: (FloorPlan) -> Boolean): FloorPlan = withContext(Dispatchers.Default) {
        withTimeout(10_000) {
            var plan = repo.loadPlan(vm.state.value.floorId!!)
            while (!until(plan)) {
                delay(20)
                plan = repo.loadPlan(vm.state.value.floorId!!)
            }
            plan
        }
    }

    @Test fun opensOnTheFloorWithRoomsOrTheFirstFloorAndListsUnplacedRooms() = test {
        val vm = vm()
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.floors.first { it.id == s.floorId }.name).isEqualTo("Main")
        assertThat(s.unplaced.map { it.name }).containsExactly("Garage")
    }

    @Test fun addRoomSelectsItFocusesNameAndSavesAtOnce() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        val s = vm.state.value
        assertThat(s.rooms).hasSize(1)
        assertThat(s.selectedId).isEqualTo(s.rooms.single().id)
        assertThat(s.focusName).isTrue()
        assertThat(savedPlan(vm) { it.placed.size == 1 }.placed).hasSize(1)
    }

    @Test fun renameKindAndSizeStickAndUndoRedoWork() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        vm.onEvent(PlanEditorEvent.Rename("Kitchen"))
        vm.onEvent(PlanEditorEvent.SetKind(RoomKind.KITCHEN))
        vm.onEvent(PlanEditorEvent.SetSize(180, 120))
        assertThat(vm.state.value.selected?.widthIn).isEqualTo(180)
        vm.onEvent(PlanEditorEvent.Undo)
        assertThat(vm.state.value.selected?.widthIn).isEqualTo(144)
        vm.onEvent(PlanEditorEvent.Redo)
        assertThat(vm.state.value.selected?.name).isEqualTo("Kitchen")
        assertThat(savedPlan(vm) { it.placed.singleOrNull()?.kind == "KITCHEN" }.placed.single().kind).isEqualTo("KITCHEN")
    }

    @Test fun dragMoveIsOneUndoStepAndSavesOnDragEnd() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        val id = vm.state.value.selectedId!!
        val startX = vm.state.value.selected!!.minX
        vm.onEvent(PlanEditorEvent.DragMove(id, 30, 0))
        vm.onEvent(PlanEditorEvent.DragMove(id, 60, 0))
        vm.onEvent(PlanEditorEvent.DragEnd)
        assertThat(vm.state.value.selected!!.minX).isEqualTo(startX + 60)
        vm.onEvent(PlanEditorEvent.Undo)
        assertThat(vm.state.value.selected!!.minX).isEqualTo(startX)
    }

    @Test fun tapAWallThenAddDoorSelectsItAndSaves() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        vm.onEvent(PlanEditorEvent.SelectWall(0, Pt(600, 328)))
        assertThat(vm.state.value.selectedWall).isEqualTo(0)
        vm.onEvent(PlanEditorEvent.AddOpening(OpeningKind.DOOR))
        val s = vm.state.value
        assertThat(s.selected!!.openings).hasSize(1)
        assertThat(s.selectedOpening?.kind).isEqualTo(OpeningKind.DOOR)
        assertThat(s.selectedWall).isNull()
        assertThat(savedPlan(vm) { it.placed.singleOrNull()?.openings?.size == 1 }.placed.single().openings).hasSize(1)
    }

    @Test fun dragOpeningWidthDeleteAndUndo() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        val room = vm.state.value.selected!!
        vm.onEvent(PlanEditorEvent.SelectWall(0, Pt(room.minX + 72, room.minY)))
        vm.onEvent(PlanEditorEvent.AddOpening(OpeningKind.WINDOW))
        val id = vm.state.value.selectedOpeningId!!
        vm.onEvent(PlanEditorEvent.DragOpening(id, Pt(room.minX + 120, room.minY)))
        vm.onEvent(PlanEditorEvent.DragEnd)
        assertThat(vm.state.value.selectedOpening!!.offsetIn).isEqualTo(102)
        vm.onEvent(PlanEditorEvent.SetOpeningWidth(48))
        assertThat(vm.state.value.selectedOpening!!.widthIn).isEqualTo(48)
        vm.onEvent(PlanEditorEvent.DeleteOpening)
        assertThat(vm.state.value.selected!!.openings).isEmpty()
        vm.onEvent(PlanEditorEvent.Undo)
        assertThat(vm.state.value.selected!!.openings).hasSize(1)
    }

    @Test fun stairsLinkToTheFloorAboveAndCanBeRelinked() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        vm.onEvent(PlanEditorEvent.AddStair)
        val stair = vm.state.value.selected!!.stairs.single()
        val floors = vm.state.value.floors
        val here = floors.indexOfFirst { it.id == vm.state.value.floorId }
        assertThat(stair.toFloorId).isEqualTo(floors[here + 1].id)
        vm.onEvent(PlanEditorEvent.LinkStair(stair.id, floors[here - 1].id))
        assertThat(vm.state.value.selected!!.stairs.single().toFloorId).isEqualTo(floors[here - 1].id)
        assertThat(savedPlan(vm) { it.placed.singleOrNull()?.stairs?.singleOrNull()?.toFloorId == floors[here - 1].id }.placed).hasSize(1)
        vm.onEvent(PlanEditorEvent.DeleteStair(stair.id))
        assertThat(vm.state.value.selected!!.stairs).isEmpty()
    }

    @Test fun deleteRemovesRoomAndClearsSelection() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        vm.onEvent(PlanEditorEvent.Delete)
        assertThat(vm.state.value.rooms).isEmpty()
        assertThat(vm.state.value.selectedId).isNull()
        assertThat(savedPlan(vm) { it.placed.isEmpty() }.placed).isEmpty()
    }

    @Test fun overlappingRoomIsFlaggedButStillSaved() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(620, 410)))
        val second = vm.state.value.rooms.last()
        vm.onEvent(PlanEditorEvent.DragMove(second.id, -60, 0))
        vm.onEvent(PlanEditorEvent.DragEnd)
        assertThat(vm.state.value.flaggedIds).hasSize(2)
        assertThat(savedPlan(vm) { it.placed.size == 2 }.placed).hasSize(2)
    }

    @Test fun placeRoomGivesAnUnplacedRoomAShape() = test {
        val vm = vm()
        val garage = vm.state.value.unplaced.single()
        vm.onEvent(PlanEditorEvent.PlaceRoom(garage.id, Pt(300, 300)))
        assertThat(vm.state.value.unplaced).isEmpty()
        assertThat(vm.state.value.rooms.single().name).isEqualTo("Garage")
        assertThat(savedPlan(vm) { it.unplaced.isEmpty() }.unplaced).isEmpty()
    }

    // AC-C12
    @Test fun floorSelectionViewAndSelectionSurviveARecreatedViewModel() = test {
        val handle = SavedStateHandle()
        val first = vm(handle)
        val upper = first.state.value.floors.first { it.name == "Upper" }
        first.onEvent(PlanEditorEvent.SelectFloor(upper.id))
        withTimeout(10_000) { first.state.first { it.floorId == upper.id } }
        first.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        first.onEvent(PlanEditorEvent.Transform(2f, 10f, 20f))
        val selected = first.state.value.selectedId
        savedPlan(first) { it.placed.size == 1 }

        val second = vm(handle)
        val s = withTimeout(10_000) { second.state.first { it.rooms.isNotEmpty() } }
        assertThat(s.floorId).isEqualTo(upper.id)
        assertThat(s.selectedId).isEqualTo(selected)
        assertThat(s.zoom).isEqualTo(2f)
        assertThat(s.panX).isEqualTo(10f)
        assertThat(s.rooms).hasSize(1)
    }

    @Test fun fitCentersAllRoomsInTheViewport() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        vm.onEvent(PlanEditorEvent.Fit(400f, 600f))
        val s = vm.state.value
        val room = s.rooms.single()
        val cx = (room.minX + room.maxX) / 2f * s.zoom + s.panX
        assertThat(cx).isWithin(1f).of(200f)
    }

    @Test fun editShapeAddsDragsAndDeletesCornersAndSavesTheLShape() = test {
        val vm = vm()
        vm.onEvent(PlanEditorEvent.AddRoom(Pt(600, 400)))
        vm.onEvent(PlanEditorEvent.SetEditShape(true))
        assertThat(vm.state.value.editShape).isTrue()
        val east = vm.state.value.selected!!.let { Pt(it.maxX, (it.minY + it.maxY) / 2) }
        vm.onEvent(PlanEditorEvent.AddCorner(1, east))
        assertThat(vm.state.value.selected!!.points).hasSize(5)
        vm.onEvent(PlanEditorEvent.DragCorner(2, -72, 0))
        vm.onEvent(PlanEditorEvent.DragEnd)
        assertThat(vm.state.value.selected!!.points[2].x).isEqualTo(east.x - 72)
        savedPlan(vm) { it.placed.singleOrNull()?.points?.size == 5 }
        vm.onEvent(PlanEditorEvent.DeleteCorner(2))
        assertThat(vm.state.value.selected!!.points).hasSize(4)
        vm.onEvent(PlanEditorEvent.Select(null))
        assertThat(vm.state.value.editShape).isFalse()
    }

    @Test fun pinchKeepsThePointUnderTheFingersFixed() {
        val s = com.kurtz.homerecord.housemanual.plan.PlanEditorUiState(zoom = 1f, panX = 10f, panY = 10f)
        val t = PlanEditorViewModel.zoomAround(s, 2f, 100f, 100f)
        assertThat(t.zoom).isEqualTo(2f)
        // World point under (100,100) is (90,90); after zoom it must still be at (100,100).
        assertThat(90f * t.zoom + t.panX).isWithin(0.01f).of(100f)
    }
}
