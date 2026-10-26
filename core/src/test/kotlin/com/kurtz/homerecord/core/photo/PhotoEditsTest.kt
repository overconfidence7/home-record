package com.kurtz.homerecord.core.photo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PhotoEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F25 (mark a photo Before, edits and delete sync)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotoEditsTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private var clock = 9_000L
    private lateinit var edits: PhotoEdits

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        edits = PhotoEdits(db, { clock }, { e, id -> changes += e to id })
        runBlocking { db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "j1", jobId = "j1", path = "/m/a.jpg", takenAt = 1, createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() = db.close()

    @Test fun captionIsTrimmedAndBlankClearsIt() = runBlocking<Unit> {
        assertThat(edits.setCaption("p1", "  Pipe under sink ")).isTrue()
        assertThat(db.photoDao().get("p1")!!.caption).isEqualTo("Pipe under sink")
        edits.setCaption("p1", "   ")
        assertThat(db.photoDao().get("p1")!!.caption).isNull()
        assertThat(db.photoDao().get("p1")!!.updatedAt).isEqualTo(9_000L)
        assertThat(changes).containsExactly("photo" to "p1", "photo" to "p1")
    }

    @Test fun tagsAndBeforeMark() = runBlocking<Unit> {
        edits.setTags("p1", "r1", "Rough-in")
        assertThat(db.photoDao().get("p1")!!.roomId).isEqualTo("r1")
        edits.markBefore("p1")
        assertThat(db.photoDao().get("p1")!!.stage).isEqualTo(PhotoEdits.BEFORE)
    }

    @Test fun deleteIsSoftAndQueued() = runBlocking<Unit> {
        assertThat(edits.delete("p1")).isTrue()
        assertThat(db.photoDao().get("p1")).isNull()
        assertThat(changes).containsExactly("photo" to "p1")
    }

    @Test fun missingPhotoChangesNothing() = runBlocking<Unit> {
        assertThat(edits.setCaption("nope", "x")).isFalse()
        assertThat(edits.delete("nope")).isFalse()
        assertThat(changes).isEmpty()
    }
}
