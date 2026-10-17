package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.logic.HandoffCounts
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HandoffRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase
    private lateinit var jobs: JobRepository
    private lateinit var entries: LogEntryRepository
    private lateinit var walls: BehindWallRepository
    private lateinit var handoff: HandoffRepository
    private var tick = 1_000L

    @Before fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val clock = Clock { tick++ }
        jobs = JobRepository(db, Source.FIELD_LOG, clock)
        entries = LogEntryRepository(db, Source.FIELD_LOG, clock)
        walls = BehindWallRepository(db, Source.FIELD_LOG, entries, clock)
        handoff = HandoffRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun `bundle holds only this home and counts behind-wall photos (AC-71, AC-73)`() = runTest {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen", "Bath"))
        val other = jobs.createJob("Oak", roomNames = listOf("Den"))
        val kitchen = jobs.getRooms(job).first { it.name == "Kitchen" }
        val wall = walls.ensureWall(kitchen.id, "N")!!
        walls.addPhoto(job.id, job.homeId, kitchen.id, wall.id, "media/a.jpg")
        val otherDen = jobs.getRooms(other).single()
        val otherWall = walls.ensureWall(otherDen.id, "S")!!
        walls.addPhoto(other.id, other.homeId, otherDen.id, otherWall.id, "media/b.jpg")

        val bundle = checkNotNull(handoff.bundleFor(job.homeId))
        assertEquals(job.homeId, bundle.home.id)
        assertEquals(Source.FIELD_LOG, bundle.home.source)
        assertEquals(1, bundle.formatVersion)
        assertEquals(HandoffCounts(rooms = 2, walls = 1, behindWallPhotos = 1, equipment = 0, finishes = 0, shutoffs = 0, panels = 0), HandoffCounts.of(bundle))
        assertEquals(listOf("media/a.jpg"), bundle.mediaPaths())
    }

    @Test fun `missing home gives null`() = runTest {
        assertNull(handoff.bundleFor("nope"))
    }

    @Test fun `bundle round-trips through writer and reader (AC-72)`() = runTest {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen"))
        val kitchen = jobs.getRooms(job).single()
        val wall = walls.ensureWall(kitchen.id, "E")!!
        val store = MediaFileStore(tmp.newFolder())
        val path = "media/a.jpg"
        store.resolveForWrite(path)!!.writeBytes(byteArrayOf(9, 8))
        walls.addPhoto(job.id, job.homeId, kitchen.id, wall.id, path)

        val bundle = checkNotNull(handoff.bundleFor(job.homeId))
        val out = ByteArrayOutputStream()
        BundleWriter(store).write(bundle, out)
        val read = BundleReader().read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder())
        assertEquals(bundle, read.record)
        assertEquals(listOf(path), read.extractedMedia)
        assertNotNull(read.record.walls.singleOrNull())
    }
}
