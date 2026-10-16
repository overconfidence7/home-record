package com.kurtz.homerecord.fieldlog.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.Clock
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.LogEntryRepository
import java.io.File
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakeCapturer(var succeed: Boolean = true) : PhotoCapturer {
    var calls = 0
    override suspend fun capture(file: File): Boolean {
        calls++
        if (succeed) file.writeBytes(byteArrayOf(1, 2, 3))
        return succeed
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var entries: LogEntryRepository
    private lateinit var media: MediaFileStore
    private lateinit var dir: File
    private lateinit var jobId: String

    @Before fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        dir = kotlin.io.path.createTempDirectory("capture").toFile()
        media = MediaFileStore(dir)
        // 2026-03-05T23:30:00Z, which is 2026-03-06 in no UTC zone: day must use UTC here.
        entries = LogEntryRepository(db, Source.FIELD_LOG, Clock { 1_772_753_400_000L }, zone = { ZoneOffset.UTC })
        jobId = JobRepository(db, Source.FIELD_LOG).createJob("Elm St").id
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test fun shutterSavesJpegAndCreatesPhotoEntryForToday() = runTest(dispatcher) {
        val vm = CaptureViewModel(jobId, entries, media, FakeCapturer())
        vm.onShutter()
        advanceUntilIdle()
        val list = entries.observeForJob(jobId).first()
        assertEquals(1, list.size)
        val e = list.single()
        assertEquals(EntryKind.PHOTO, e.kind)
        assertEquals("2026-03-05", e.day)
        assertTrue(e.mediaPath!!.startsWith("media/") && e.mediaPath!!.endsWith(".jpg"))
        assertTrue(media.exists(e.mediaPath!!))
        assertEquals(1, vm.state.value.photosThisSession)
        assertFalse(vm.state.value.busy)
    }

    @Test fun failedCaptureCreatesNoEntryAndShowsError() = runTest(dispatcher) {
        val vm = CaptureViewModel(jobId, entries, media, FakeCapturer(succeed = false))
        vm.onShutter()
        advanceUntilIdle()
        assertTrue(entries.observeForJob(jobId).first().isEmpty())
        assertTrue(vm.state.value.failed)
        assertEquals(0, vm.state.value.photosThisSession)
        assertNotNull(vm.state.value)
    }

    @Test fun secondShutterWhileBusyIsIgnored() = runTest(dispatcher) {
        val capturer = FakeCapturer()
        val vm = CaptureViewModel(jobId, entries, media, capturer)
        vm.onShutter()
        vm.onShutter()
        advanceUntilIdle()
        assertEquals(1, capturer.calls)
        assertEquals(1, entries.observeForJob(jobId).first().size)
    }
}
