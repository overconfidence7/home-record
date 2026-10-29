package com.kurtz.homerecord.core.photo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

// AC-F27 (typed notes and voice memos attach to the job and day; transcript best effort and editable)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobNotesTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private val uploads = mutableListOf<String>()
    private var heard: String? = "Rough-in done in the kitchen"
    private val clock = 1_760_000_000_000L // 2025-10-09 UTC
    private lateinit var notes: JobNotes

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        notes = JobNotes(
            db, transcriber = { heard }, queueUpload = { _, entity, id, name, _, _ -> uploads += "$entity/$id/$name" },
            companyId = { "c1" }, now = { clock }, zone = { ZoneOffset.UTC }, onChange = { e, id -> changes += e to id },
        )
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Kitchen", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() = db.close()

    @Test fun typedNoteAttachesToJobAndDay() = runBlocking<Unit> {
        val id = checkNotNull(notes.addText("j1", "  Homeowner wants the island moved  "))
        val row = checkNotNull(db.noteDao().get(id))
        assertThat(row.jobId).isEqualTo("j1")
        assertThat(row.day).isEqualTo("2025-10-09")
        assertThat(row.text).isEqualTo("Homeowner wants the island moved")
        assertThat(changes).containsExactly("note" to id)
        assertThat(uploads).isEmpty()
    }

    @Test fun blankNoteSavesNothing() = runBlocking<Unit> {
        assertThat(notes.addText("j1", "   ")).isNull()
        assertThat(db.noteDao().listFor("j1")).isEmpty()
    }

    @Test fun voiceMemoIsTranscribedAndQueuedForUpload() = runBlocking<Unit> {
        val id = checkNotNull(notes.addVoice("j1", "/media/memo.m4a"))
        val row = checkNotNull(db.noteDao().get(id))
        assertThat(row.voicePath).isEqualTo("/media/memo.m4a")
        assertThat(row.transcript).isEqualTo("Rough-in done in the kitchen")
        assertThat(uploads).containsExactly("note/$id/memo.m4a")
    }

    @Test fun voiceMemoKeptWhenDeviceCannotTranscribe() = runBlocking<Unit> {
        heard = null
        val id = checkNotNull(notes.addVoice("j1", "/media/memo2.m4a"))
        val row = checkNotNull(db.noteDao().get(id))
        assertThat(row.voicePath).isEqualTo("/media/memo2.m4a")
        assertThat(row.transcript).isNull()
    }

    @Test fun transcriptCanBeEdited() = runBlocking<Unit> {
        val voice = checkNotNull(notes.addVoice("j1", "/media/memo3.m4a"))
        notes.setTranscript(voice, " Rough-in done in the bath ")
        assertThat(checkNotNull(db.noteDao().get(voice)).transcript).isEqualTo("Rough-in done in the bath")
        val typed = checkNotNull(notes.addText("j1", "old"))
        notes.setTranscript(typed, "new")
        assertThat(checkNotNull(db.noteDao().get(typed)).text).isEqualTo("new")
    }

    @Test fun missingJobSavesNothingAndDeleteHidesNote() = runBlocking<Unit> {
        assertThat(notes.addText("gone", "x")).isNull()
        assertThat(notes.addVoice("gone", "/media/x.m4a")).isNull()
        val id = checkNotNull(notes.addText("j1", "bye"))
        notes.delete(id)
        assertThat(db.noteDao().listFor("j1")).isEmpty()
        assertThat(JobNotes.MAX_VOICE_MS).isEqualTo(180_000L)
    }
}
