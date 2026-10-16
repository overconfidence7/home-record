package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BehindWallRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var jobs: JobRepository
    private lateinit var entries: LogEntryRepository
    private lateinit var walls: BehindWallRepository
    private var tick = 1_000L

    @Before fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val clock = Clock { tick++ }
        jobs = JobRepository(db, Source.FIELD_LOG, clock)
        entries = LogEntryRepository(db, Source.FIELD_LOG, clock)
        walls = BehindWallRepository(db, Source.FIELD_LOG, entries, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun `ensureWall creates once, matches case-insensitively and rejects blank (AC-63)`() = runTest {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen"))
        val room = jobs.getRooms(job).single()
        val a = walls.ensureWall(room.id, "North")!!
        val b = walls.ensureWall(room.id, " north ")!!
        assertEquals(a.id, b.id)
        assertEquals(1, walls.observeWalls(room.id).first().size)
        assertNull(walls.ensureWall(room.id, "  "))
        val custom = walls.ensureWall(room.id, "Closet back")!!
        assertEquals("Closet back", db.wallDao().get(custom.id)?.label)
    }

    @Test fun `addPhoto writes a BEHIND_WALL photo and a PHOTO log entry (AC-64)`() = runTest {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen"))
        val room = jobs.getRooms(job).single()
        val wall = walls.ensureWall(room.id, "East")!!
        val photo = walls.addPhoto(job.id, job.homeId, room.id, wall.id, "media/a.jpg")
        assertEquals(PhotoStage.BEHIND_WALL, photo.stage)
        val stored = walls.observePhotos(job.homeId).first().single()
        assertEquals(room.id, stored.roomId)
        assertEquals(wall.id, stored.wallId)
        val entry = entries.observeForJob(job.id).first().single()
        assertEquals(EntryKind.PHOTO, entry.kind)
        assertEquals("media/a.jpg", entry.mediaPath)
        assertEquals(room.id, entry.roomId)
    }

    @Test fun `deleting the entry removes its wall photo`() = runTest {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen"))
        val room = jobs.getRooms(job).single()
        val wall = walls.ensureWall(room.id, "East")!!
        walls.addPhoto(job.id, job.homeId, room.id, wall.id, "media/a.jpg")
        entries.delete(entries.observeForJob(job.id).first().single().id)
        assertTrue(walls.observePhotos(job.homeId).first().isEmpty())
    }
}
