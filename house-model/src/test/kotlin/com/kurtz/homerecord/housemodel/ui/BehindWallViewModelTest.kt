package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.RoomChoice
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// AC-F49: "Behind the wall" picks a room, then a wall, opens the camera, and sends the photo to the outline screen at
// Rough-in; the same wall's next capture (after drywall) goes at Finished.
@OptIn(ExperimentalCoroutinesApi::class)
class BehindWallViewModelTest {
    private val created = mutableListOf<BehindWallViewModel>()
    private val kitchenWalls = MutableStateFlow(
        listOf(
            RoomWall("kn", WallSide.NORTH, null, 168, 96),
            RoomWall("ke", WallSide.EAST, null, 144, 96),
        ),
    )
    private val bathWalls = MutableStateFlow(listOf(RoomWall("bn", WallSide.NORTH, null, 96, 96)))
    private val rooms = listOf(RoomChoice("k", "Kitchen", "Main floor"), RoomChoice("b", "Hall bath", "Upstairs"))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.forEach { it.viewModelScope.coroutineContext[Job]?.cancel() }
        Dispatchers.resetMain()
    }

    private fun vm(
        media: FakeWallMedia = FakeWallMedia(),
        saved: SavedStateHandle = SavedStateHandle(),
        choices: List<RoomChoice> = rooms,
        start: String? = null,
    ) = BehindWallViewModel(
        "Hale kitchen", { choices }, { id -> if (id == "k") kitchenWalls else bathWalls }, media, saved, startRoomId = start,
    ).also { created += it }

    @Test
    @AcceptanceCriterion("AC-F49")
    fun listsTheRoomsByFloorWithTheirWallProgress() {
        kitchenWalls.value = kitchenWalls.value.map { if (it.id == "kn") it.copy(thumbnails = mapOf(WallStage.ROUGH_IN to "kn.jpg")) else it }
        val s = vm().state.value
        assertThat(s.loading).isFalse()
        assertThat(s.roomId).isNull()
        assertThat(s.rooms.map { it.name }).containsExactly("Kitchen", "Hall bath").inOrder()
        val kitchen = s.rooms.first()
        assertThat(kitchen.floorName).isEqualTo("Main floor")
        assertThat(kitchen.progress.roughIn).isEqualTo(1)
        assertThat(kitchen.progress.total).isEqualTo(2)
    }

    @Test
    @AcceptanceCriterion("AC-F49")
    fun roomThenWallThenCameraOutlinesTheFirstPhotoAtRoughIn() {
        val media = FakeWallMedia()
        val vm = vm(media)
        vm.onEvent(BehindWallEvent.OpenRoom("k"))
        assertThat(vm.state.value.room?.walls?.map { it.id }).containsExactly("kn", "ke").inOrder()
        vm.onEvent(BehindWallEvent.PickWall("kn"))
        assertThat(vm.state.value.photoSheetFor).isEqualTo("kn")
        vm.onEvent(BehindWallEvent.TakePhoto)
        val launch = checkNotNull(vm.state.value.photoLaunch)
        assertThat(launch.target).isNotNull()
        vm.onEvent(BehindWallEvent.PhotoLaunchHandled)
        media.written += checkNotNull(launch.target).path
        vm.onEvent(BehindWallEvent.CameraResult(ok = true))
        assertThat(vm.state.value.outlineRequest).isEqualTo(WallOutlineRequest("kn", WallStage.ROUGH_IN, "media/1.jpg"))
        vm.onEvent(BehindWallEvent.OutlineOpened)
        assertThat(vm.state.value.outlineRequest).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-F49")
    fun theSecondCaptureOnTheSameWallAfterDrywallIsFinished() {
        val vm = vm()
        vm.onEvent(BehindWallEvent.OpenRoom("k"))
        // The Rough-in photo of the north wall saved by the outline screen comes back through the walls flow.
        kitchenWalls.value = kitchenWalls.value.map { if (it.id == "kn") it.copy(thumbnails = mapOf(WallStage.ROUGH_IN to "kn.jpg")) else it }
        val north = checkNotNull(vm.state.value.room?.walls?.first { it.id == "kn" })
        assertThat(north.nextBehindWallStage).isEqualTo(WallStage.FINISHED)
        vm.onEvent(BehindWallEvent.PickWall("kn"))
        vm.onEvent(BehindWallEvent.ChoosePhoto)
        assertThat(vm.state.value.photoLaunch).isEqualTo(WallPhotoLaunch(null))
        vm.onEvent(BehindWallEvent.GalleryResult("content://picked/2"))
        assertThat(vm.state.value.outlineRequest).isEqualTo(WallOutlineRequest("kn", WallStage.FINISHED, "media/picked.jpg"))
        // The east wall has no photo yet, so it is still Rough-in.
        assertThat(vm.state.value.room?.walls?.first { it.id == "ke" }?.nextBehindWallStage).isEqualTo(WallStage.ROUGH_IN)
    }

    @Test
    @AcceptanceCriterion("AC-F49")
    fun aCameraResultAfterProcessDeathLandsOnTheSameWallAndStage() {
        val saved = SavedStateHandle()
        val media = FakeWallMedia()
        val first = vm(media, saved)
        first.onEvent(BehindWallEvent.OpenRoom("k"))
        first.onEvent(BehindWallEvent.PickWall("kn"))
        first.onEvent(BehindWallEvent.TakePhoto)
        val path = checkNotNull(first.state.value.photoLaunch?.target).path
        media.written += path
        // A Rough-in photo arriving meanwhile must not move this capture to Finished: the stage was fixed when the camera opened.
        kitchenWalls.value = kitchenWalls.value.map { if (it.id == "kn") it.copy(thumbnails = mapOf(WallStage.ROUGH_IN to "kn.jpg")) else it }
        val again = vm(media, saved)
        assertThat(again.state.value.roomId).isEqualTo("k")
        again.onEvent(BehindWallEvent.CameraResult(ok = true))
        assertThat(again.state.value.outlineRequest).isEqualTo(WallOutlineRequest("kn", WallStage.ROUGH_IN, path))
    }

    @Test
    fun aCancelledCameraDiscardsTheEmptyFile() {
        val media = FakeWallMedia()
        val vm = vm(media)
        vm.onEvent(BehindWallEvent.OpenRoom("k"))
        vm.onEvent(BehindWallEvent.PickWall("ke"))
        vm.onEvent(BehindWallEvent.TakePhoto)
        val path = checkNotNull(vm.state.value.photoLaunch?.target).path
        vm.onEvent(BehindWallEvent.CameraResult(ok = false))
        assertThat(vm.state.value.outlineRequest).isNull()
        assertThat(media.discarded).containsExactly(path)
    }

    @Test
    fun aPhotoThatCannotBeCopiedSaysSo() {
        val media = FakeWallMedia().apply { importResult = null }
        val vm = vm(media)
        vm.onEvent(BehindWallEvent.OpenRoom("b"))
        vm.onEvent(BehindWallEvent.PickWall("bn"))
        vm.onEvent(BehindWallEvent.ChoosePhoto)
        vm.onEvent(BehindWallEvent.GalleryResult("content://picked/x"))
        assertThat(vm.state.value.photoImportFailed).isTrue()
        assertThat(vm.state.value.outlineRequest).isNull()
    }

    @Test
    fun backFromTheWallsGoesToTheRoomsThenLeaves() {
        val vm = vm()
        vm.onEvent(BehindWallEvent.OpenRoom("b"))
        assertThat(vm.back()).isTrue()
        assertThat(vm.state.value.roomId).isNull()
        assertThat(vm.back()).isFalse()
        // Opened on a room: Back leaves straight away.
        val direct = vm(start = "k")
        assertThat(direct.state.value.room?.name).isEqualTo("Kitchen")
        assertThat(direct.back()).isFalse()
    }

    @Test
    fun aRoomRemovedFromThePlanShowsAsMissing() {
        val vm = vm(choices = rooms.drop(1), saved = SavedStateHandle(mapOf("behindWallRoom" to "k")))
        assertThat(vm.state.value.roomMissing).isTrue()
    }

    @Test
    fun noRoomsIsTheEmptyState() {
        val s = vm(choices = emptyList()).state.value
        assertThat(s.loading).isFalse()
        assertThat(s.rooms).isEmpty()
    }
}
