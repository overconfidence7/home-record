package com.kurtz.homerecord.core.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.data.NoteEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F54, AC-F55 (cr:sync-local-columns): a finished upload leaves its R2 key on the synced row
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UploadedKeysTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private lateinit var keys: UploadedKeys

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        keys = UploadedKeys(db, { 9_000L }) { entity, id -> changes += entity to id }
    }

    @After fun tearDown() = db.close()

    @Test fun photoNoteAndFileGetTheirKeyAndAreQueuedForSync() = runBlocking<Unit> {
        db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "j1", jobId = "j1", path = "/m/p1.jpg", takenAt = 1, createdAt = 1, updatedAt = 1))
        db.noteDao().upsert(NoteEntity(id = "n1", jobId = "j1", day = "2026-10-27", voicePath = "/m/n1.m4a", createdAt = 1, updatedAt = 1))
        db.jobFileDao().upsert(JobFileEntity(id = "f1", jobId = "j1", name = "Plans.pdf", path = "/m/Plans.pdf", mime = "application/pdf", createdAt = 1, updatedAt = 1))

        assertThat(keys.record("photo", "p1", "c1/photo/p1/p1.jpg")).isTrue()
        assertThat(keys.record("note", "n1", "c1/note/n1/n1.m4a")).isTrue()
        assertThat(keys.record("job_file", "f1", "c1/job_file/f1/Plans.pdf")).isTrue()

        assertThat(db.photoDao().get("p1")?.r2Key).isEqualTo("c1/photo/p1/p1.jpg")
        assertThat(db.photoDao().get("p1")?.updatedAt).isEqualTo(9_000L)
        assertThat(db.noteDao().get("n1")?.r2Key).isEqualTo("c1/note/n1/n1.m4a")
        assertThat(db.jobFileDao().get("f1")?.r2Key).isEqualTo("c1/job_file/f1/Plans.pdf")
        assertThat(changes).containsExactly("photo" to "p1", "note" to "n1", "job_file" to "f1").inOrder()
    }

    @Test fun sameKeyAgainMissingRowOrUnknownEntityChangesNothing() = runBlocking<Unit> {
        db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "j1", path = "/m/p1.jpg", r2Key = "k", takenAt = 1, createdAt = 1, updatedAt = 1))
        assertThat(keys.record("photo", "p1", "k")).isFalse()
        assertThat(keys.record("photo", "gone", "k2")).isFalse()
        assertThat(keys.record("document", "d1", "k3")).isFalse()
        assertThat(keys.record("photo", "p1", "")).isFalse()
        assertThat(changes).isEmpty()
    }
}
