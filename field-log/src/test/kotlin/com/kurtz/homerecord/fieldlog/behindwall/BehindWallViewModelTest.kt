package com.kurtz.homerecord.fieldlog.behindwall

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.BehindWallRepository
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.LogEntryRepository
import com.kurtz.homerecord.fieldlog.capture.PhotoCapturer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakeCapturer(var succeed: Boolean = true) : PhotoCapturer {
    override suspend fun capture(file: File): Boolean {
        if (succeed) file.writeBytes(byteArrayOf(1))
        return succeed
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BehindWallViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var dir: File
    private lateinit var jobs: JobRepository
    private lateinit var entries: LogEntryRepository
    private lateinit var walls: BehindWallRepository
    private lateinit var media: MediaFileStore
    private val labels = listOf("North", "East", "South", "West")

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        dir = kotlin.io.path.createTempDirectory("wall").toFile()
        media = MediaFileStore(dir)
        jobs = JobRepository(db, Source.FIELD_LOG)
        entries = LogEntryRepository(db, Source.FIELD_LOG)
        walls = BehindWallRepository(db, Source.FIELD_LOG, entries)
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
        Dispatchers.resetMain()
    }

    private fun vm(jobId: String, capturer: PhotoCapturer = FakeCapturer()) =
        BehindWallViewModel(jobId, labels, jobs, walls, media, capturer)

    @Test fun roomThenWallThenCameraWithRunningCount() = runTest(dispatcher) {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen"))
        val vm = vm(job.id)
        val rooms = vm.state.first { it.rooms.isNotEmpty() }
        assertEquals(WallStep.ROOM, rooms.step)
        vm.onEvent(BehindWallEvent.SelectRoom(rooms.rooms.single().id))
        val atWall = vm.state.first { it.step == WallStep.WALL }
        assertEquals(labels, atWall.walls.map { it.label })
        vm.onEvent(BehindWallEvent.SelectWall("North"))
        vm.state.first { it.step == WallStep.CAMERA }
        vm.onShutter()
        vm.state.first { it.photosThisSession == 1 && !it.busy }
        vm.onShutter()
        val done = vm.state.first { it.photosThisSession == 2 && it.wall?.count == 2 }
        assertEquals(2, done.wall?.count)
        val photo = walls.observePhotos(job.homeId).first().first()
        assertEquals(PhotoStage.BEHIND_WALL, photo.stage)
        assertEquals(2, entries.observeForJob(job.id).first().size)
    }

    @Test fun customWallIsSavedAsWallRow() = runTest(dispatcher) {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen"))
        val vm = vm(job.id)
        val rooms = vm.state.first { it.rooms.isNotEmpty() }
        vm.onEvent(BehindWallEvent.SelectRoom(rooms.rooms.single().id))
        vm.state.first { it.step == WallStep.WALL }
        vm.onEvent(BehindWallEvent.CustomChanged("Closet back"))
        vm.onEvent(BehindWallEvent.AddCustom)
        val s = vm.state.first { it.step == WallStep.CAMERA }
        assertEquals("Closet back", s.wall?.label)
        assertTrue(s.walls.any { it.label == "Closet back" })
    }

    @Test fun failedCaptureSavesNothingAndBackReturnsToWalls() = runTest(dispatcher) {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen"))
        val vm = vm(job.id, FakeCapturer(succeed = false))
        val rooms = vm.state.first { it.rooms.isNotEmpty() }
        vm.onEvent(BehindWallEvent.SelectRoom(rooms.rooms.single().id))
        vm.state.first { it.step == WallStep.WALL }
        vm.onEvent(BehindWallEvent.SelectWall("East"))
        vm.state.first { it.step == WallStep.CAMERA }
        vm.onShutter()
        vm.state.first { it.failed }
        assertTrue(walls.observePhotos(job.homeId).first().isEmpty())
        vm.onEvent(BehindWallEvent.Back)
        vm.state.first { it.step == WallStep.WALL }
        vm.onEvent(BehindWallEvent.Back)
        vm.state.first { it.step == WallStep.ROOM }
    }
}
