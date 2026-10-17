package com.kurtz.homerecord.core.imports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.bundle.RecordBundle
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.Wall
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun home(id: String, source: Source) = Home(id, "Elm", createdAt = 1, updatedAt = 1, source = source)
private fun room(id: String, homeId: String, name: String, source: Source = Source.FIELD_LOG) =
    Room(id, homeId, name, createdAt = 1, updatedAt = 1, source = source)

class ImportPlannerTest {
    private val bundle = RecordBundle(
        home = home("fl-home", Source.FIELD_LOG),
        rooms = listOf(room("r1", "fl-home", "Kitchen"), room("r2", "fl-home", "Bath")),
        walls = listOf(Wall("w1", "r1", "N", 1, 1, Source.FIELD_LOG)),
    )

    @Test fun `new records are remapped to the local home and marked import (AC-77)`() {
        val plan = ImportPlanner.plan(bundle, "local", emptyMap())
        assertEquals(listOf("local", "local"), plan.toInsert.rooms.map { it.homeId })
        assertTrue(plan.toInsert.rooms.all { it.source == Source.IMPORT })
        assertEquals(2, plan.newCounts[ImportEntityType.ROOM])
        assertEquals(1, plan.newCounts[ImportEntityType.WALL])
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test fun `same id with house-manual source is a conflict (AC-76)`() {
        val plan = ImportPlanner.plan(bundle, "local", mapOf("r1" to Source.HOUSE_MANUAL))
        assertEquals(listOf(ImportConflict(ImportEntityType.ROOM, "r1", "Kitchen")), plan.conflicts)
        assertEquals(listOf("r2"), plan.toInsert.rooms.map { it.id })
        assertEquals(listOf("r1"), plan.conflicting.rooms.map { it.id })
    }

    @Test fun `ids already present from an earlier import are skipped (AC-79)`() {
        val plan = ImportPlanner.plan(bundle, "local", mapOf("r1" to Source.IMPORT, "r2" to Source.IMPORT, "w1" to Source.IMPORT))
        assertTrue(plan.isEmpty)
        assertEquals(3, plan.alreadyPresent)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImportApplierTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase
    private lateinit var applier: ImportApplier
    private lateinit var filesDir: File
    private lateinit var staging: File

    private val bundle = RecordBundle(
        home = home("fl-home", Source.FIELD_LOG),
        rooms = listOf(room("r1", "fl-home", "Kitchen (imported)"), room("r2", "fl-home", "Bath")),
        walls = listOf(Wall("w1", "r1", "N", 1, 1, Source.FIELD_LOG)),
        photos = listOf(
            Photo("p1", "fl-home", "r1", "w1", PhotoStage.BEHIND_WALL, 5, null, "media/p1.jpg", 1, 1, Source.FIELD_LOG),
        ),
    )

    @Before fun setUp() = runTest {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        filesDir = tmp.newFolder("files")
        staging = tmp.newFolder("staging")
        File(staging, "media").mkdirs()
        File(staging, "media/p1.jpg").writeText("jpeg")
        applier = ImportApplier(db, MediaFileStore(filesDir))
        db.homeDao().upsert(home("local", Source.HOUSE_MANUAL).toEntity())
        db.roomDao().upsert(room("r1", "local", "Kitchen", Source.HOUSE_MANUAL).toEntity())
    }

    @After fun tearDown() = db.close()

    @Test fun `no local home means no plan`() = runTest {
        db.homeDao().delete(db.homeDao().get("local")!!)
        assertNull(applier.plan(bundle))
    }

    @Test fun `keep mine leaves the local room and inserts the rest with media (AC-77, AC-78)`() = runTest {
        val plan = checkNotNull(applier.plan(bundle))
        val result = applier.apply(plan, mapOf("r1" to ConflictChoice.KEEP_MINE), staging)
        assertEquals(1, result.keptMine)
        assertEquals("Kitchen", db.roomDao().get("r1")!!.name)
        assertEquals(Source.HOUSE_MANUAL, db.roomDao().get("r1")!!.source)
        val bath = db.roomDao().get("r2")!!.toDomain()
        assertEquals("local", bath.homeId)
        assertEquals(Source.IMPORT, bath.source)
        val photo = db.photoDao().get("p1")!!.toDomain()
        assertEquals("local", photo.homeId)
        assertEquals(PhotoStage.BEHIND_WALL, photo.stage)
        assertEquals("jpeg", File(filesDir, "media/p1.jpg").readText())
    }

    @Test fun `use imported replaces the local room exactly (AC-78)`() = runTest {
        val plan = checkNotNull(applier.plan(bundle))
        val result = applier.apply(plan, mapOf("r1" to ConflictChoice.USE_IMPORTED), staging)
        assertEquals(1, result.replaced)
        val r1 = db.roomDao().get("r1")!!.toDomain()
        assertEquals("Kitchen (imported)", r1.name)
        assertEquals("local", r1.homeId)
        assertEquals(Source.IMPORT, r1.source)
    }

    @Test fun `missing choice defaults to keep mine`() = runTest {
        applier.apply(checkNotNull(applier.plan(bundle)), emptyMap(), staging)
        assertEquals("Kitchen", db.roomDao().get("r1")!!.name)
    }

    @Test fun `importing twice duplicates nothing (AC-79)`() = runTest {
        applier.apply(checkNotNull(applier.plan(bundle)), emptyMap(), staging)
        val second = checkNotNull(applier.plan(bundle))
        assertEquals(0, second.newTotal)
        assertEquals(listOf("r1"), second.conflicts.map { it.id })
        applier.apply(second, emptyMap(), staging)
        assertEquals(2, db.roomDao().getAll().size)
        assertEquals(1, db.photoDao().getAll().size)
        assertEquals(1, db.wallDao().getAll().size)
    }
}
