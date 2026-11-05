package com.kurtz.homerecord.core.bundle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
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

// AC-H17 (a Field Log handoff lands in House Manual with the crew's wall photos and overlays), AC-C47 (merge rules,
// conflicts keep mine unless Use imported, a re-import is a no-op)
@AcceptanceCriterion("AC-H17", "AC-C47")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeBundleImporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var crew: HomeRecordDatabase
    private lateinit var owner: HomeRecordDatabase
    private lateinit var importer: HomeBundleImporter
    private lateinit var mediaDir: File

    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
        .allowMainThreadQueries().build()

    @Before fun setUp() {
        crew = db()
        owner = db()
        mediaDir = tmp.newFolder("owner-media")
        val store = RoomRowStore(owner)
        importer = HomeBundleImporter({ t, c, v -> store.readWhere(t, c, v) }, store, owner.importLedgerDao(), mediaDir)
    }

    @After fun tearDown() {
        crew.close()
        owner.close()
    }

    private fun seedCrew() = runBlocking {
        crew.homeDao().upsert(HomeEntity(id = "h1", name = "Ortiz house", createdAt = 1, updatedAt = 1, source = Sources.FIELD_LOG))
        crew.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main floor", createdAt = 1, updatedAt = 1))
        crew.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        crew.wallDao().upsert(WallEntity(id = "w1", roomId = "r1", edgeIndex = 0, orientation = "N", lengthIn = 150, heightIn = 96, createdAt = 1, updatedAt = 1))
        val photo = tmp.newFile("wp1.jpg").apply { writeText("rough-in") }
        crew.wallPhotoDao().upsert(
            WallPhotoEntity(
                id = "wp1", wallId = "w1", roomId = "r1", stage = "ROUGH_IN", originalPath = photo.absolutePath,
                takenAt = 5, createdAt = 5, updatedAt = 5, source = Sources.FIELD_LOG,
            ),
        )
        crew.overlayDao().upsert(OverlayEntity(id = "o1", homeId = "h1", wallId = "w1", kind = "PIPE", label = "Supply line", createdAt = 6, updatedAt = 6))
    }

    /** Exports the crew's home, writes and reads the zip as the owner's phone would receive it. */
    private fun handoff(): Pair<HomeBundle, File> = runBlocking {
        val store = RoomRowStore(crew)
        val out = checkNotNull(HomeBundleExporter({ t, c, v -> store.readWhere(t, c, v) }).export("h1", Sources.FIELD_LOG))
        val bytes = ByteArrayOutputStream().also { BundleWriter.write(out.bundle, it, out::mediaFile) }.toByteArray()
        val unpacked = File(tmp.root, "unpacked-${System.nanoTime()}")
        val read = BundleReader.read(ByteArrayInputStream(bytes), unpacked) as BundleReadResult.Ok
        read.bundle to unpacked
    }

    @Test fun firstImportAddsTheHouseWithWallPhotosAndOverlays() = runBlocking<Unit> {
        seedCrew()
        val (bundle, unpacked) = handoff()
        val preview = importer.preview(bundle)
        assertThat(preview.plan.conflicts).isEmpty()
        assertThat(preview.incoming("room")).isEqualTo(1)
        assertThat(preview.incoming("wall_photo")).isEqualTo(1)
        assertThat(preview.incoming("overlay")).isEqualTo(1)

        val outcome = importer.apply(preview, unpacked)
        assertThat(outcome.mediaCopied).isEqualTo(1)
        assertThat(owner.roomDao().get("r1")?.name).isEqualTo("Kitchen")
        assertThat(owner.overlayDao().get("o1")?.label).isEqualTo("Supply line")
        val photo = checkNotNull(owner.wallPhotoDao().get("wp1"))
        assertThat(photo.originalPath).isEqualTo(File(mediaDir, "wp1.jpg").absolutePath)
        assertThat(File(photo.originalPath).readText()).isEqualTo("rough-in")
        assertThat(photo.source).isEqualTo(Sources.FIELD_LOG)
    }

    @Test fun importingTheSameBundleAgainChangesNothing() = runBlocking<Unit> {
        seedCrew()
        val (bundle, unpacked) = handoff()
        importer.apply(importer.preview(bundle), unpacked)
        val again = importer.preview(bundle)
        assertThat(again.plan.nothingToDo).isTrue()
        assertThat(importer.apply(again, unpacked).written).isEqualTo(0)
    }

    @Test fun crewChangeOnlyUpdatesAndBothChangedIsAConflictThatKeepsMine() = runBlocking<Unit> {
        seedCrew()
        val (first, unpacked) = handoff()
        importer.apply(importer.preview(first), unpacked)
        // The homeowner renames the kitchen; the crew relabels the overlay and also renames the kitchen.
        owner.roomDao().upsert(checkNotNull(owner.roomDao().get("r1")).copy(name = "Our kitchen", updatedAt = 20))
        crew.overlayDao().upsert(checkNotNull(crew.overlayDao().get("o1")).copy(label = "Hot supply", updatedAt = 21))
        crew.roomDao().upsert(checkNotNull(crew.roomDao().get("r1")).copy(name = "Kitchen (new)", updatedAt = 22))
        val (second, unpacked2) = handoff()
        val preview = importer.preview(second)
        assertThat(preview.plan.updated("overlay")).isEqualTo(1)
        assertThat(preview.plan.conflicts.map { it.id }).containsExactly("r1")

        importer.apply(preview, unpacked2)
        assertThat(owner.overlayDao().get("o1")?.label).isEqualTo("Hot supply")
        assertThat(owner.roomDao().get("r1")?.name).isEqualTo("Our kitchen")
    }

    @Test fun useImportedOverwritesTheConflict() = runBlocking<Unit> {
        seedCrew()
        val (first, unpacked) = handoff()
        importer.apply(importer.preview(first), unpacked)
        owner.roomDao().upsert(checkNotNull(owner.roomDao().get("r1")).copy(name = "Our kitchen", updatedAt = 20))
        crew.roomDao().upsert(checkNotNull(crew.roomDao().get("r1")).copy(name = "Kitchen (new)", updatedAt = 22))
        val (second, unpacked2) = handoff()
        val preview = importer.preview(second)
        importer.apply(preview, unpacked2, choices = mapOf(("room" to "r1") to ConflictChoice.UseImported))
        assertThat(owner.roomDao().get("r1")?.name).isEqualTo("Kitchen (new)")
    }

    @Test fun entityTypesThisVersionDoesNotKnowAreSkipped() = runBlocking<Unit> {
        seedCrew()
        val (bundle, unpacked) = handoff()
        val future = BundleRecord(JsonObject(mapOf("id" to JsonPrimitive("x1"), "createdAt" to JsonPrimitive(1), "updatedAt" to JsonPrimitive(1))))
        val newer = bundle.copy(records = bundle.records + ("hologram" to listOf(future)))
        val preview = importer.preview(newer)
        assertThat(preview.plan.added("hologram")).isEqualTo(1)
        val outcome = importer.apply(preview, unpacked)
        assertThat(outcome.written).isEqualTo(preview.plan.writes().size - 1)
        assertThat(owner.roomDao().get("r1")).isNotNull()
    }
}
