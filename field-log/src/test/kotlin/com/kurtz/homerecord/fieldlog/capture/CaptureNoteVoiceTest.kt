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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakeRecorder(private val startOk: Boolean = true) : VoiceRecorder {
    var started = 0
    var stopped = 0
    override fun start(file: File): Boolean {
        if (!startOk) return false
        started++
        file.writeBytes(byteArrayOf(1))
        return true
    }
    override fun stop(): Boolean {
        stopped++
        return true
    }
}

private object UnusedCapturer : PhotoCapturer {
    override suspend fun capture(file: File): Boolean = false
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CaptureNoteVoiceTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var entries: LogEntryRepository
    private lateinit var media: MediaFileStore
    private lateinit var dir: File
    private lateinit var jobId: String

    @Before fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        dir = kotlin.io.path.createTempDirectory("voice").toFile()
        media = MediaFileStore(dir)
        entries = LogEntryRepository(db, Source.FIELD_LOG, Clock { 1_772_753_400_000L }, zone = { ZoneOffset.UTC })
        jobId = JobRepository(db, Source.FIELD_LOG).createJob("Elm St").id
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
        Dispatchers.resetMain()
    }

    private fun vm(recorder: VoiceRecorder = FakeRecorder()) = CaptureViewModel(jobId, entries, media, UnusedCapturer, recorder)

    @Test fun savingATypedNoteCreatesNoteEntry() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(CaptureEvent.OpenNote)
        vm.onEvent(CaptureEvent.NoteChanged("  Cracked joist near sink  "))
        vm.onEvent(CaptureEvent.SaveNote)
        advanceUntilIdle()
        val e = entries.observeForJob(jobId).first().single()
        assertEquals(EntryKind.NOTE, e.kind)
        assertEquals("Cracked joist near sink", e.text)
        assertEquals("2026-03-05", e.day)
        assertFalse(vm.state.value.noteOpen)
    }

    @Test fun emptyNoteIsNotSaved() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(CaptureEvent.OpenNote)
        vm.onEvent(CaptureEvent.NoteChanged("   "))
        assertFalse(vm.state.value.canSaveNote)
        vm.onEvent(CaptureEvent.SaveNote)
        advanceUntilIdle()
        assertTrue(entries.observeForJob(jobId).first().isEmpty())
        assertNull(entries.addNote(jobId, ""))
    }

    @Test fun tappingStopSavesVoiceEntryWithDuration() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = vm(recorder)
        vm.onEvent(CaptureEvent.ToggleVoice)
        assertTrue(vm.state.value.recording)
        advanceTimeBy(2_100)
        vm.onEvent(CaptureEvent.ToggleVoice)
        advanceUntilIdle()
        val e = entries.observeForJob(jobId).first().single()
        assertEquals(EntryKind.VOICE, e.kind)
        assertEquals(2_000L, e.durationMs)
        assertTrue(e.mediaPath!!.endsWith(".m4a"))
        assertTrue(media.exists(e.mediaPath!!))
        assertEquals(1, recorder.stopped)
        assertFalse(vm.state.value.recording)
    }

    @Test fun recordingStopsAutomaticallyAtThreeMinutes() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = vm(recorder)
        vm.onEvent(CaptureEvent.ToggleVoice)
        advanceTimeBy(VoiceCap.MAX_MS + 5_000)
        advanceUntilIdle()
        // Room writes finish off the test dispatcher; wait for the entry before checking state.
        val saved = entries.observeForJob(jobId).first { it.isNotEmpty() }.single()
        assertEquals(VoiceCap.MAX_MS, saved.durationMs)
        assertEquals(1, recorder.stopped)
        advanceUntilIdle()
        assertFalse(vm.state.value.recording)
    }

    @Test fun recorderThatCannotStartCreatesNoEntry() = runTest(dispatcher) {
        val vm = vm(FakeRecorder(startOk = false))
        vm.onEvent(CaptureEvent.ToggleVoice)
        advanceUntilIdle()
        assertTrue(vm.state.value.voiceFailed)
        assertFalse(vm.state.value.recording)
        assertTrue(entries.observeForJob(jobId).first().isEmpty())
    }

    @Test fun leavingWhileRecordingKeepsTheRecording() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(CaptureEvent.ToggleVoice)
        advanceTimeBy(1_000)
        vm.onLeave()
        advanceUntilIdle()
        assertEquals(1, entries.observeForJob(jobId).first().size)
    }
}
