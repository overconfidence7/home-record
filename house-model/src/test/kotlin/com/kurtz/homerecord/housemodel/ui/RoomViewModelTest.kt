package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private val created = mutableListOf<RoomViewModel>()

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HomeRepository(db, ApplicationProvider.getApplicationContext<Context>().resources::getString)
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(roomId: String, rows: MutableStateFlow<List<ItemRow>>? = null) = RoomViewModel(repo, roomId, rows).also { created += it }

    private fun wallsVm(roomId: String, stage: WallStage, saved: SavedStateHandle = SavedStateHandle(), media: FakeWallMedia? = null) =
        RoomViewModel(repo, roomId, walls = WallPhotoRepository(db).observeRoomWalls(roomId), defaultStage = stage, savedState = saved, media = media).also { created += it }

    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val living = PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 180, 144)

    private suspend fun mainFloor(): String {
        val homeId = repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floor = repo.observeFloors(homeId).first { it.isNotEmpty() }.single { it.name == "Main" }
        repo.savePlan(floor.id, listOf(kitchen, living))
        return floor.id
    }

    private fun row(id: String, name: String, roomId: String?, warranty: WarrantyStatus = WarrantyStatus.Unknown) = ItemRow(
        id = id, name = name, make = null, model = null, category = ItemCategory.APPLIANCES,
        subtypeId = "dishwasher", roomId = roomId, roomName = "", warranty = warranty,
    )

    @AcceptanceCriterion("AC-C14")
    @Test fun loadsTheRoomWithFloorAndHome() = test {
        mainFloor()
        val state = withTimeout(10_000) { vm("k").state.first { !it.loading } }
        assertThat(state.room?.name).isEqualTo("Kitchen")
        assertThat(state.floorName).isEqualTo("Main")
        assertThat(state.homeName).isEqualTo("Maple St")
        assertThat(state.canAddItem).isFalse()
        assertThat(state.items).isEmpty()
    }

    @AcceptanceCriterion("AC-C22")
    @Test fun viewIn3DPointsAtTheRoomsFloorOnlyWhereTheAppHas3D() = test {
        val floorId = mainFloor()
        val without = withTimeout(10_000) { vm("k").state.first { !it.loading && it.floorId != null } }
        assertThat(without.showView3D).isFalse()

        val model = RoomViewModel(repo, "k", canView3D = true).also { created += it }
        val state = withTimeout(10_000) { model.state.first { !it.loading && it.floorId != null } }
        assertThat(state.showView3D).isTrue()
        val target = checkNotNull(state.view3DTarget)
        assertThat(target.floorId).isEqualTo(floorId)
        assertThat(target.roomId).isEqualTo("k")
        assertThat(target.homeName).isEqualTo("Maple St")
        assertThat(target.homeId).isNotEmpty()
    }

    // AC-C14: the page follows the plan, so a rename shows and a delete turns it into Room not found
    @Test fun followsRenameAndDelete() = test {
        val floorId = mainFloor()
        val model = vm("k")
        withTimeout(10_000) { model.state.first { it.room?.name == "Kitchen" } }
        repo.savePlan(floorId, listOf(kitchen.copy(name = "Eat-in kitchen"), living))
        withTimeout(10_000) { model.state.first { it.room?.name == "Eat-in kitchen" } }
        repo.savePlan(floorId, listOf(living))
        val gone = withTimeout(10_000) { model.state.first { it.room == null } }
        assertThat(gone.loading).isFalse()
    }

    @Test fun unknownRoomIsNotFound() = test {
        mainFloor()
        val state = withTimeout(10_000) { vm("nope").state.first { !it.loading } }
        assertThat(state.room).isNull()
    }

    @AcceptanceCriterion("AC-H10")
    @Test fun listsOnlyThisRoomsItemsAndUpdates() = test {
        mainFloor()
        val active = WarrantyStatus.Active("2028-03-14")
        val rows = MutableStateFlow(listOf(row("1", "Range", "k"), row("2", "Dishwasher", "k", active), row("3", "Sofa", "l")))
        val model = vm("k", rows)
        val state = withTimeout(10_000) { model.state.first { it.items.size == 2 } }
        assertThat(state.canAddItem).isTrue()
        assertThat(state.items.map { it.name }).containsExactly("Dishwasher", "Range").inOrder()
        assertThat(state.items[0].warranty).isEqualTo(active)
        rows.value = rows.value + row("4", "Toaster", "k")
        assertThat(withTimeout(10_000) { model.state.first { it.items.size == 3 } }.items.map { it.name }).contains("Toaster")
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun listsTheRoomsWallsFromThePlanAndFollowsAResize() = test {
        val floorId = mainFloor()
        val model = wallsVm("k", WallStage.HouseManualDefault)
        val state = withTimeout(10_000) { model.state.first { it.walls.size == 4 } }
        assertThat(state.wallStage).isEqualTo(WallStage.FINISHED)
        assertThat(state.walls.map { it.side }).containsExactly(WallSide.NORTH, WallSide.EAST, WallSide.SOUTH, WallSide.WEST).inOrder()
        assertThat(state.walls.map { it.lengthIn }.sorted()).containsExactly(144, 144, 168, 168)

        repo.savePlan(floorId, listOf(PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 192, 144), PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 192, 0, 180, 144)))
        val resized = withTimeout(10_000) { model.state.first { s -> s.walls.any { it.lengthIn == 192 } } }
        assertThat(resized.walls).hasSize(4)
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun stageStartsOnTheAppDefaultAndTheChoiceSurvivesProcessDeath() = test {
        mainFloor()
        val saved = SavedStateHandle()
        val fieldLog = wallsVm("k", WallStage.BehindTheWall, saved)
        assertThat(fieldLog.state.value.wallStage).isEqualTo(WallStage.ROUGH_IN)
        fieldLog.selectStage(WallStage.INSULATION)
        assertThat(fieldLog.state.value.wallStage).isEqualTo(WallStage.INSULATION)

        val restored = wallsVm("k", WallStage.BehindTheWall, saved)
        assertThat(restored.state.value.wallStage).isEqualTo(WallStage.INSULATION)
    }

    @AcceptanceCriterion("AC-C18")
    @Test fun photographThisWallTakesAPhotoAndOpensTheOutlineAtTheChosenStage() = test {
        mainFloor()
        val media = FakeWallMedia()
        val saved = SavedStateHandle()
        val model = wallsVm("k", WallStage.BehindTheWall, saved, media)
        val wall = withTimeout(10_000) { model.state.first { it.walls.size == 4 } }.walls.first()
        assertThat(model.state.value.canPhotograph).isTrue()

        model.onPhotoEvent(RoomEvent.PhotographWall(wall.id))
        assertThat(model.state.value.photoSheetFor).isEqualTo(wall.id)
        model.onPhotoEvent(RoomEvent.TakeWallPhoto)
        val launch = checkNotNull(model.state.value.photoLaunch)
        val target = checkNotNull(launch.target)
        assertThat(model.state.value.photoSheetFor).isNull()
        model.onPhotoEvent(RoomEvent.PhotoLaunchHandled)

        // The camera result lands on a view model rebuilt after process death.
        media.written += target.path
        val restored = wallsVm("k", WallStage.BehindTheWall, saved, media)
        restored.onPhotoEvent(RoomEvent.CameraResult(ok = true))
        assertThat(restored.state.value.outlineRequest).isEqualTo(WallOutlineRequest(wall.id, WallStage.ROUGH_IN, target.path))
        restored.onPhotoEvent(RoomEvent.OutlineOpened)
        assertThat(restored.state.value.outlineRequest).isNull()
    }

    @AcceptanceCriterion("AC-C18")
    @Test fun aWallPhotoCanComeFromTheGalleryAndACancelledCameraLeavesNoFile() = test {
        mainFloor()
        val media = FakeWallMedia()
        val model = wallsVm("k", WallStage.HouseManualDefault, media = media)
        val wall = withTimeout(10_000) { model.state.first { it.walls.size == 4 } }.walls.last()

        model.onPhotoEvent(RoomEvent.PhotographWall(wall.id))
        model.onPhotoEvent(RoomEvent.TakeWallPhoto)
        val path = checkNotNull(model.state.value.photoLaunch?.target).path
        model.onPhotoEvent(RoomEvent.CameraResult(ok = false))
        assertThat(media.discarded).containsExactly(path)
        assertThat(model.state.value.outlineRequest).isNull()

        model.onPhotoEvent(RoomEvent.PhotographWall(wall.id))
        model.onPhotoEvent(RoomEvent.ChooseWallPhoto)
        assertThat(model.state.value.photoLaunch).isEqualTo(WallPhotoLaunch(null))
        model.onPhotoEvent(RoomEvent.GalleryResult("content://picker/42"))
        val request = withTimeout(10_000) { model.state.first { it.outlineRequest != null } }.outlineRequest
        assertThat(request).isEqualTo(WallOutlineRequest(wall.id, WallStage.FINISHED, "media/picked.jpg"))

        media.importResult = null
        model.onPhotoEvent(RoomEvent.OutlineOpened)
        model.onPhotoEvent(RoomEvent.PhotographWall(wall.id))
        model.onPhotoEvent(RoomEvent.ChooseWallPhoto)
        model.onPhotoEvent(RoomEvent.GalleryResult("content://picker/43"))
        assertThat(withTimeout(10_000) { model.state.first { it.photoImportFailed } }.outlineRequest).isNull()
    }

    @Test fun noPhotographWithoutSomewhereToKeepThePhoto() = test {
        mainFloor()
        val model = wallsVm("k", WallStage.HouseManualDefault)
        assertThat(model.state.value.canPhotograph).isFalse()
        model.onPhotoEvent(RoomEvent.PhotographWall("w"))
        assertThat(model.state.value.photoSheetFor).isNull()
    }
}
