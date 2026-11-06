package com.kurtz.homerecord.core.bundle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

// AC-C48 (passphrase backup of the whole home, restored on a fresh install) at the AC-H14 size: 3 floors, 12 rooms,
// 25 items, 10 wall photos and 15 overlays come back with nothing missing and every photo byte-for-byte.
@AcceptanceCriterion("AC-C48")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeBackupRoundTripTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var phone: HomeRecordDatabase
    private lateinit var fresh: HomeRecordDatabase

    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
        .allowMainThreadQueries().build()

    @Before fun setUp() {
        phone = db()
        fresh = db()
    }

    @After fun tearDown() {
        phone.close()
        fresh.close()
    }

    private fun seed(): List<File> = runBlocking {
        phone.homeDao().upsert(HomeEntity(id = "h1", name = "Maple Street house", createdAt = 1, updatedAt = 1, source = Sources.HOUSE_MANUAL))
        val floors = listOf("Basement", "Main floor", "Upper floor")
        floors.forEachIndexed { i, name -> phone.floorDao().upsert(FloorEntity(id = "f$i", homeId = "h1", sortOrder = i, name = name, createdAt = 1, updatedAt = 1)) }
        val photos = mutableListOf<File>()
        repeat(12) { r ->
            phone.roomDao().upsert(RoomEntity(id = "r$r", floorId = "f${r % 3}", name = "Room ${r + 1}", createdAt = 1, updatedAt = 1))
            phone.wallDao().upsert(WallEntity(id = "w$r", roomId = "r$r", edgeIndex = 0, orientation = "N", lengthIn = 144, heightIn = 96, createdAt = 1, updatedAt = 1))
            if (r < 10) {
                val photo = tmp.newFile("wp$r.jpg").apply { writeBytes(ByteArray(2_000) { (it * (r + 3)).toByte() }) }
                photos += photo
                phone.wallPhotoDao().upsert(
                    WallPhotoEntity(id = "wp$r", wallId = "w$r", roomId = "r$r", stage = "FINISHED", originalPath = photo.absolutePath, takenAt = 5, createdAt = 5, updatedAt = 5),
                )
            }
        }
        repeat(15) { o -> phone.overlayDao().upsert(OverlayEntity(id = "o$o", homeId = "h1", wallId = "w${o % 12}", kind = "PIPE", label = "Cold supply $o", createdAt = 6, updatedAt = 6)) }
        repeat(25) { i -> phone.itemDao().upsert(ItemEntity(id = "i$i", homeId = "h1", category = "APPLIANCES", subtype = "refrigerator", name = "Item ${i + 1}", createdAt = 7, updatedAt = 7)) }
        photos
    }

    private fun export(db: HomeRecordDatabase): ExportedHome = runBlocking {
        val store = RoomRowStore(db)
        checkNotNull(HomeBundleExporter({ t, c, v -> store.readWhere(t, c, v) }).export("h1", Sources.HOUSE_MANUAL))
    }

    @Test fun `a backup restores the whole home on a fresh install with nothing missing`() = runBlocking {
        // AC-C48
        val photos = seed()
        val before = export(phone)
        val sealed = ByteArrayOutputStream().also {
            HomeBackup.write(before, it, "maple-street-1987".toCharArray(), File(tmp.root, "cache/out.zip"))
        }.toByteArray()

        val unpacked = File(tmp.root, "unpacked")
        val read = HomeBackup.read(ByteArrayInputStream(sealed), "maple-street-1987".toCharArray(), File(tmp.root, "cache/in.zip"), unpacked)
        val bundle = (read as BackupReadResult.Ok).bundle
        val store = RoomRowStore(fresh)
        val mediaDir = File(tmp.root, "fresh-media")
        val importer = HomeBundleImporter({ t, c, v -> store.readWhere(t, c, v) }, store, fresh.importLedgerDao(), mediaDir)
        importer.apply(importer.preview(bundle), unpacked)

        val after = export(fresh)
        val counts = after.bundle.records.mapValues { it.value.size }
        assertThat(counts).containsAtLeastEntriesIn(
            mapOf("home" to 1, "floor" to 3, "room" to 12, "wall" to 12, "wall_photo" to 10, "overlay" to 15, "item" to 25),
        )
        assertThat(counts).isEqualTo(before.bundle.records.mapValues { it.value.size })
        // Every wall photo points at its own copy on the new phone, byte-for-byte the original.
        val restored = after.bundle.records.getValue("wall_photo").associate { it.id to it.fields }
        photos.forEachIndexed { r, original ->
            val name = ((restored.getValue("wp$r")[HomeBundleExporter.MEDIA_FIELD] as kotlinx.serialization.json.JsonObject)["originalPath"] as JsonPrimitive).content
            assertThat(File(mediaDir, name).readBytes()).isEqualTo(original.readBytes())
        }
    }
}
