package com.kurtz.homerecord.fieldlog.entry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.LogEntryRepository
import com.kurtz.homerecord.fieldlog.playback.AudioPlayer
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakePlayer : AudioPlayer {
    var played: File? = null
    var stops = 0
    override fun play(file: File, onDone: () -> Unit): Boolean { played = file; return true }
    override fun stop() { stops++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var entries: LogEntryRepository
    private lateinit var jobs: JobRepository
    private lateinit var media: MediaFileStore
    private val player = FakePlayer()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = HomeRecordDatabase.inMemory(context)
        entries = LogEntryRepository(db, Source.FIELD_LOG)
        jobs = JobRepository(db, Source.FIELD_LOG)
        media = MediaFileStore(java.nio.file.Files.createTempDirectory("media").toFile())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(id: String): EntryViewModel =
        EntryViewModel(entries, jobs, media, player, id).also { v -> v.state.first { it.loaded } }

    @Test fun `editing a note saves text and room tag (AC-61 edit)`() = runTest(dispatcher) {
        val job = jobs.createJob("J", roomNames = listOf("Kitchen"))
        val room = jobs.getRooms(jobs.getJob(job.id)!!).single()
        val note = entries.addNote(job.id, "old")!!
        val vm = vm(note.id)
        assertEquals(listOf(room.id), vm.state.value.rooms.map { it.id })
        vm.onEvent(EntryEvent.TextChanged("new text"))
        vm.onEvent(EntryEvent.RoomChosen(room.id))
        vm.onEvent(EntryEvent.Save)
        vm.state.first { it.finished }
        val saved = entries.get(note.id)!!
        assertEquals("new text", saved.text)
        assertEquals(room.id, saved.roomId)
        assertTrue(vm.state.value.finished)
    }

    @Test fun `blank note cannot be saved`() = runTest(dispatcher) {
        val job = jobs.createJob("J")
        val note = entries.addNote(job.id, "keep")!!
        val vm = vm(note.id)
        vm.onEvent(EntryEvent.TextChanged("   "))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(EntryEvent.Save)
        assertEquals("keep", entries.get(note.id)!!.text)
        assertFalse(vm.state.value.finished)
    }

    @Test fun `delete removes the row and its media file`() = runTest(dispatcher) {
        val job = jobs.createJob("J")
        val path = media.newPhotoPath()
        media.resolveForWrite(path)!!.writeBytes(byteArrayOf(1))
        val photo = entries.addPhoto(job.id, path)
        val vm = vm(photo.id)
        vm.onEvent(EntryEvent.Delete)
        vm.state.first { it.finished }
        assertNull(entries.get(photo.id))
        assertFalse(media.exists(path))
        assertTrue(vm.state.value.finished)
    }

    @Test fun `voice entry plays its file and toggles off`() = runTest(dispatcher) {
        val job = jobs.createJob("J")
        val path = media.newAudioPath()
        media.resolveForWrite(path)!!.writeBytes(byteArrayOf(1))
        val voice = entries.addVoice(job.id, path, 5_000)
        val vm = vm(voice.id)
        vm.onEvent(EntryEvent.TogglePlay)
        assertTrue(vm.state.value.playing)
        assertEquals(media.resolve(path), player.played)
        vm.onEvent(EntryEvent.TogglePlay)
        assertFalse(vm.state.value.playing)
    }

    @Test fun `missing entry loads as not found`() = runTest(dispatcher) {
        val vm = vm("nope")
        assertTrue(vm.state.value.loaded)
        assertNull(vm.state.value.entry)
    }
}
