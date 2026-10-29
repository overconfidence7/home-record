package com.kurtz.homerecord.fieldlog.capture

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.photo.PhotoEdits
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.photo.JobNotes
import com.kurtz.homerecord.core.photo.JobPhotos
import com.kurtz.homerecord.core.photo.VoiceRecorder
import com.kurtz.homerecord.core.photo.LensChoice
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    /** When set, a fix waits for it, like a phone still looking for GPS. */
    var slow: CompletableDeferred<Unit>? = null
    override fun hasPermission() = granted
    override suspend fun currentFix(timeoutMs: Long): GeoFix? {
        slow?.await()
        return if (granted) GeoFix(34.05, -118.25, 6f) else null
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CaptureViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private var remembered: Pair<String?, String?> = null to null
    private val location = FakeLocation()
    private lateinit var appScope: CoroutineScope

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
        // The photo observer still collecting when the database closes would throw into the next test class.
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        appScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private class FakeRecorder(var canStart: Boolean = true, var length: Long? = 4_000L) : VoiceRecorder {
        var started: String? = null
        var stops = 0
        override fun start(path: String): Boolean { if (canStart) started = path; return canStart }
        override fun stop(): Long? { stops++; return length }
    }

    private val recorder = FakeRecorder()
    private val created = mutableListOf<CaptureViewModel>()

    private fun vm(
        jobId: String = "j1", last: Pair<String?, String?> = null to null, hasCamera: Boolean = true, store: ViewModelStore = ViewModelStore(),
        granted: () -> Boolean = { hasCamera },
        isOnPhone: suspend (String) -> Boolean = { true },
        downloadPhoto: suspend (String) -> com.kurtz.homerecord.core.photo.OnPhone = { com.kurtz.homerecord.core.photo.OnPhone.Ready },
    ): CaptureViewModel {
        val photos = JobPhotos(db, { _, _, _, _, _, _ -> }, { "c1" }, { 9_000L })
        val deps = CaptureDeps(
            jobId, db.jobDao(), db.roomDao(), photos, location, { last }, { room, phase -> remembered = room to phase }, granted,
            notes = JobNotes(db, { null }, { _, _, _, _, _, _ -> }, { "c1" }, { 9_000L }),
            recorder = recorder, newMemoPath = { "/media/memo.m4a" }, appScope = appScope,
            isOnPhone = isOnPhone, downloadPhoto = downloadPhoto,
        )
        val factory = viewModelFactory { initializer { CaptureViewModel(deps, SavedStateHandle()) } }
        return ViewModelProvider(store, factory)[CaptureViewModel::class.java].also { created += it }
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

    // AC-F25
    @Test fun afterShotLinksToTheGhostedBefore() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.Captured("/media/before.jpg"))
        val before = photosAfter(1).single()
        db.photoDao().upsert(before.copy(pairRole = PhotoEdits.BEFORE))
        val ghosted = withTimeout(10_000) { vm.state.first { it.ghost != null } }
        assertThat(ghosted.ghost?.photoId).isEqualTo(before.id)
        assertThat(ghosted.ghostOn).isFalse() // cr:fl-ghost-autopair: pairing is opt-in
        vm.onEvent(CaptureEvent.ToggleGhost)
        vm.onEvent(CaptureEvent.Captured("/media/after.jpg"))
        val after = photosAfter(2).first { it.path == "/media/after.jpg" }
        assertThat(after.beforeOfPhotoId).isEqualTo(before.id)
        assertThat(withTimeout(10_000) { vm.state.first { it.ghost == null } }.ghost).isNull()
    }

    // AC-F25, cr:fl-ghost-autopair: with a Before on the job, a shot is plain unless the overlay was turned on.
    @Test fun ghostOffTakesAPlainShot() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.Captured("/media/before.jpg"))
        db.photoDao().upsert(photosAfter(1).single().copy(pairRole = PhotoEdits.BEFORE))
        withTimeout(10_000) { vm.state.first { it.ghost != null } }
        vm.onEvent(CaptureEvent.Captured("/media/plain.jpg"))
        assertThat(photosAfter(2).first { it.path == "/media/plain.jpg" }.beforeOfPhotoId).isNull()
        vm.onEvent(CaptureEvent.ToggleGhost)
        vm.onEvent(CaptureEvent.ToggleGhost)
        vm.onEvent(CaptureEvent.Captured("/media/plain2.jpg"))
        assertThat(photosAfter(3).first { it.path == "/media/plain2.jpg" }.beforeOfPhotoId).isNull()
    }

    // AC-F25, cr:fl-ghost-autopair: a Before from another room is not offered, and a second quick shot is not paired.
    @Test fun ghostOnlyFromTheSelectedRoomAndOneAfterPerBefore() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.SelectRoom("r1"))
        vm.onEvent(CaptureEvent.Captured("/media/kitchen-before.jpg"))
        db.photoDao().upsert(photosAfter(1).single().copy(pairRole = PhotoEdits.BEFORE))
        withTimeout(10_000) { vm.state.first { it.ghost != null } }
        vm.onEvent(CaptureEvent.SelectRoom("r2"))
        assertThat(vm.state.value.ghost).isNull()
        vm.onEvent(CaptureEvent.Captured("/media/garage.jpg"))
        assertThat(photosAfter(2).first { it.path == "/media/garage.jpg" }.beforeOfPhotoId).isNull()
        vm.onEvent(CaptureEvent.SelectRoom("r1"))
        assertThat(vm.state.value.ghost).isNotNull()
        vm.onEvent(CaptureEvent.ToggleGhost)
        vm.onEvent(CaptureEvent.Captured("/media/kitchen-after.jpg"))
        assertThat(vm.state.value.ghostOn).isFalse()
        vm.onEvent(CaptureEvent.Captured("/media/kitchen-more.jpg"))
        val rows = photosAfter(4)
        assertThat(rows.first { it.path == "/media/kitchen-after.jpg" }.beforeOfPhotoId).isNotNull()
        assertThat(rows.first { it.path == "/media/kitchen-more.jpg" }.beforeOfPhotoId).isNull()
    }

    // AC-F24
    @Test fun shutterSavesPhotoWithJobRoomPhaseAndFix() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.SelectRoom("r1"))
        vm.onEvent(CaptureEvent.SelectPhase("Finish"))
        vm.onEvent(CaptureEvent.Captured("/media/a.jpg"))
        // The row is written first and the fix lands right after it.
        val row = withTimeout(10_000) { db.photoDao().observeForJob("j1").first { it.singleOrNull()?.lat != null } }.single()
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
        assertThat(photosAfter(2)).hasSize(2)
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

    // AC-F29: granted in Settings while away, the camera opens on return; revoked there, the denied state shows
    @Test fun resumeRereadsTheCameraPermission() {
        var granted = false
        val vm = vm(hasCamera = false, granted = { granted })
        vm.onEvent(CaptureEvent.PermissionResult(false))
        vm.onEvent(CaptureEvent.Resumed)
        assertThat(vm.state.value.access).isEqualTo(CameraAccess.Denied)
        granted = true
        vm.onEvent(CaptureEvent.Resumed)
        assertThat(vm.state.value.access).isEqualTo(CameraAccess.Granted)
        granted = false
        vm.onEvent(CaptureEvent.Resumed)
        assertThat(vm.state.value.access).isEqualTo(CameraAccess.Denied)
    }

    // AC-F29: the resume that follows the first system dialog does not cancel the pending ask
    @Test fun resumeWhileAskingKeepsAsking() {
        val vm = vm(hasCamera = false)
        vm.onEvent(CaptureEvent.Resumed)
        assertThat(vm.state.value.access).isEqualTo(CameraAccess.Unknown)
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

    // AC-F27
    @Test fun typedNoteSavesToTheJobAndClosesTheDialog() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.OpenNote)
        vm.onEvent(CaptureEvent.NoteText("  Move the island  "))
        vm.onEvent(CaptureEvent.SaveNote)
        val rows = withTimeout(10_000) { db.noteDao().observeFor("j1").first { it.isNotEmpty() } }
        assertThat(rows.single().text).isEqualTo("Move the island")
        // The row lands before the view model's continuation updates the state, so wait for the state too.
        val after = withTimeout(10_000) { vm.state.first { it.message == CaptureMessage.NoteSaved } }
        assertThat(after.noteOpen).isFalse()
        assertThat(after.noteText).isEmpty()
    }

    // AC-F27
    @Test fun blankNoteSavesNothingAndKeepsTheDialogOpen() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.OpenNote)
        vm.onEvent(CaptureEvent.NoteText("   "))
        vm.onEvent(CaptureEvent.SaveNote)
        assertThat(db.noteDao().listFor("j1")).isEmpty()
        assertThat(vm.state.value.noteOpen).isTrue()
    }

    // AC-F27
    @Test fun voiceTapRecordsThenSavesTheMemoWithoutATranscript() = runBlocking<Unit> {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.VoiceTap)
        assertThat(vm.state.value.recording).isTrue()
        assertThat(recorder.started).isEqualTo("/media/memo.m4a")
        vm.onEvent(CaptureEvent.VoiceTap)
        val row = withTimeout(10_000) { db.noteDao().observeFor("j1").first { it.isNotEmpty() } }.single()
        assertThat(row.voicePath).isEqualTo("/media/memo.m4a")
        assertThat(row.transcript).isNull()
        val after = withTimeout(10_000) { vm.state.first { it.message == CaptureMessage.MemoSaved } }
        assertThat(after.recording).isFalse()
    }

    // AC-F27
    @Test fun recorderThatCannotStartShowsAMessageAndDoesNotRecord() {
        recorder.canStart = false
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.VoiceTap)
        assertThat(vm.state.value.recording).isFalse()
        assertThat(vm.state.value.message).isEqualTo(CaptureMessage.MemoFailed)
    }

    // AC-F27
    @Test fun emptyRecordingSavesNoNote() = runBlocking<Unit> {
        recorder.length = null
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.VoiceTap)
        vm.onEvent(CaptureEvent.VoiceTap)
        assertThat(db.noteDao().listFor("j1")).isEmpty()
        assertThat(vm.state.value.message).isEqualTo(CaptureMessage.MemoFailed)
    }

    // AC-F27
    @Test fun deniedMicrophoneSaysSo() {
        val vm = vm().also { it.loaded() }
        vm.onEvent(CaptureEvent.MicDenied)
        assertThat(vm.state.value.message).isEqualTo(CaptureMessage.MicDenied)
    }

    // AC-F24: Back right after the shutter used to cancel the save while it waited up to 3 s for a fix.
    @Test fun leavingRightAfterTheShutterKeepsThePhotoAndAddsTheFixLater() = runBlocking<Unit> {
        val gps = CompletableDeferred<Unit>().also { location.slow = it }
        val store = ViewModelStore()
        val vm = vm(store = store).also { it.loaded() }
        vm.onEvent(CaptureEvent.SelectRoom("r1"))
        vm.onEvent(CaptureEvent.Captured("/media/quick.jpg"))
        store.clear()
        val row = photosAfter(1).single()
        assertThat(row.path).isEqualTo("/media/quick.jpg")
        assertThat(row.roomId).isEqualTo("r1")
        assertThat(row.lat).isNull()
        gps.complete(Unit)
        val fixed = withTimeout(10_000) { db.photoDao().observeForJob("j1").first { it.singleOrNull()?.lat != null } }.single()
        assertThat(fixed.lat).isEqualTo(34.05)
        assertThat(fixed.lng).isEqualTo(-118.25)
    }

    // AC-F27: leaving Capture while recording keeps the memo instead of throwing it away.
    @Test fun leavingWhileRecordingSavesTheMemo() = runBlocking<Unit> {
        val store = ViewModelStore()
        val vm = vm(store = store).also { it.loaded() }
        vm.onEvent(CaptureEvent.VoiceTap)
        assertThat(vm.state.value.recording).isTrue()
        store.clear()
        assertThat(recorder.stops).isEqualTo(1)
        val row = withTimeout(10_000) { db.noteDao().observeFor("j1").first { it.isNotEmpty() } }.single()
        assertThat(row.voicePath).isEqualTo("/media/memo.m4a")
    }

    // AC-F25, AC-F55: a Before taken on another phone has no bytes here; the overlay waits with an empty path while it
    // downloads by key once, and shows the photo when the row's path is set (cr:sync-local-columns).
    @Test fun ghostFromAnotherPhoneDownloadsBeforeShowing() = runBlocking<Unit> {
        val asked = mutableListOf<String>()
        val vm = vm(
            isOnPhone = { it.isNotEmpty() },
            downloadPhoto = { id ->
                asked += id
                db.photoDao().setLocalPath(id, "/cache/before.jpg")
                com.kurtz.homerecord.core.photo.OnPhone.Ready
            },
        ).also { it.loaded() }
        db.photoDao().upsert(
            com.kurtz.homerecord.core.data.PhotoEntity(
                id = "theirs", homeId = "h1", jobId = "j1", path = "", r2Key = "c1/photo/theirs/IMG.jpg", pairRole = PhotoEdits.BEFORE,
                takenAt = 1_000, createdAt = 1_000, updatedAt = 1_000,
            ),
        )
        val shown = withTimeout(10_000) { vm.state.first { it.ghost?.path == "/cache/before.jpg" } }
        assertThat(shown.ghost?.photoId).isEqualTo("theirs")
        assertThat(asked).containsExactly("theirs")
    }
}
