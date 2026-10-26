package com.kurtz.homerecord.fieldlog.capture

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.photo.JobPhotos
import com.kurtz.homerecord.core.photo.LensChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeLocation(var granted: Boolean = true) : LocationProvider {
    override fun hasPermission() = granted
    override suspend fun currentFix(timeoutMs: Long) = if (granted) GeoFix(34.05, -118.25, 6f) else null
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CaptureViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private var remembered: Pair<String?, String?> = null to null
    private val location = FakeLocation()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Ortiz house", createdAt = 1, updatedAt = 1))
            db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main", createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "r2", floorId = "f1", name = "Garage", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(
                JobEntity(id = "j1", companyId = "c1", homeId = "h1", name = "Ortiz rewire", phasesJson = """["Rough-in","Finish"]""", createdAt = 1, updatedAt = 1),
            )
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(jobId: String = "j1", last: Pair<String?, String?> = null to null, hasCamera: Boolean = true): CaptureViewModel {
        val photos = JobPhotos(db, { _, _, _, _, _, _ -> }, { "c1" }, { 9_000L })
        val deps = CaptureDeps(
            jobId, db.jobDao(), db.roomDao(), photos, location, { last }, { room, phase -> remembered = room to phase }, { hasCamera },
        )
        return CaptureViewModel(deps, SavedStateHandle())
    }

    private suspend fun photosAfter(n: Int) = withTimeout(10_000) { db.photoDao().observeForJob("j1").first { it.size >= n } }

    private fun CaptureViewModel.loaded(): CaptureUiState = runBlocking { withTimeout(10_000) { state.first { !it.loading } } }

    // AC-F24
    @Test fun loadsRoomsAndPhasesForTheJob() {
        val s = vm().loaded()
        assertThat(s.loading).isFalse()
        assertThat(s.rooms.map { it.name }).containsExactly("Kitchen", "Garage")
        assertThat(s.phases).containsExactly("Rough-in", "Finish").inOrder()
        assertThat(s.access).isEqualTo(CameraAccess.Granted)
    }

    // AC-F24
    @Test fun shutterSavesPhotoWithJobRoomPhaseAndFix() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.SelectRoom("r1"))
        vm.onEvent(CaptureEvent.SelectPhase("Finish"))
        vm.onEvent(CaptureEvent.Captured("/media/a.jpg"))
        val row = photosAfter(1).single()
        assertThat(row.roomId).isEqualTo("r1")
        assertThat(row.stage).isEqualTo("Finish")
        assertThat(row.lat).isEqualTo(34.05)
        assertThat(withTimeout(10_000) { vm.state.first { it.savedCount >= 1 } }.savedCount).isEqualTo(1)
    }

    // AC-F24
    @Test fun withoutLocationPermissionPhotoIsStillSaved() = runBlocking<Unit> {
        location.granted = false
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.Captured("/media/b.jpg"))
        val row = photosAfter(1).single()
        assertThat(row.lat).isNull()
    }

    // AC-F24
    @Test fun chipsAreRememberedBetweenShots() {
        val first = vm().also { it.loaded() }
        first.onEvent(CaptureEvent.SelectRoom("r2"))
        first.onEvent(CaptureEvent.SelectPhase("Rough-in"))
        assertThat(remembered).isEqualTo("r2" to "Rough-in")
        val second = vm(last = remembered).also { it.loaded() }
        assertThat(second.state.value.roomId).isEqualTo("r2")
        assertThat(second.state.value.phase).isEqualTo("Rough-in")
    }

    // AC-F24
    @Test fun importedGalleryPhotosAreAllSaved() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.Imported(listOf("/media/1.jpg", "/media/2.jpg")))
        assertThat(photosAfter(1)).hasSize(2)
        assertThat(withTimeout(10_000) { vm.state.first { it.savedCount >= 2 } }.savedCount).isEqualTo(2)
    }

    // AC-F29
    @Test fun singleCameraDeviceHidesFlipAndUsesTheBackLens() {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.CameraInfo(hasBack = false, hasFront = true))
        assertThat(vm.state.value.lens).isEqualTo(LensChoice.Front)
        assertThat(vm.state.value.canFlip).isFalse()
        vm.onEvent(CaptureEvent.Flip)
        assertThat(vm.state.value.lens).isEqualTo(LensChoice.Front)
    }

    // AC-F29
    @Test fun flipWorksWhenBothLensesExist() {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.CameraInfo(hasBack = true, hasFront = true))
        assertThat(vm.state.value.canFlip).isTrue()
        vm.onEvent(CaptureEvent.Flip)
        assertThat(vm.state.value.lens).isEqualTo(LensChoice.Front)
    }

    // AC-F29
    @Test fun noCameraAtAllSaysSo() {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.CameraInfo(hasBack = false, hasFront = false))
        assertThat(vm.state.value.lens).isEqualTo(LensChoice.None)
        assertThat(vm.state.value.message).isEqualTo(CaptureMessage.NoCamera)
    }

    // AC-F29
    @Test fun deniedPermissionShowsTheDeniedState() {
        val vm = vm(hasCamera = false)
        assertThat(vm.state.value.access).isEqualTo(CameraAccess.Unknown)
        vm.onEvent(CaptureEvent.PermissionResult(false))
        assertThat(vm.state.value.access).isEqualTo(CameraAccess.Denied)
    }

    @Test fun missingJobReportsItAndSavesNothing() = runBlocking<Unit> {
        val vm = vm(jobId = "gone")
        vm.loaded()
        assertThat(vm.state.value.message).isEqualTo(CaptureMessage.JobGone)
        vm.onEvent(CaptureEvent.Captured("/media/z.jpg"))
        assertThat(vm.state.value.savedCount).isEqualTo(0)
    }

    @Test fun flashCyclesOffAutoOn() {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.CycleFlash)
        assertThat(vm.state.value.flash).isEqualTo(FlashMode.Auto)
        vm.onEvent(CaptureEvent.CycleFlash)
        vm.onEvent(CaptureEvent.CycleFlash)
        assertThat(vm.state.value.flash).isEqualTo(FlashMode.Off)
    }
}
