package com.kurtz.homerecord.core.bundle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ChangeOrderEntity
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.data.WarrantyEntity
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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

// AC-C46 (every house-model entity in the bundle, media under media/), AC-C47 (ledger makes a re-import a no-op)
@AcceptanceCriterion("AC-C46", "AC-C47")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeBundleExporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase
    private lateinit var exporter: HomeBundleExporter

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        val store = RoomRowStore(db)
        exporter = HomeBundleExporter({ table, column, values -> store.readWhere(table, column, values) }, now = { 99L })
    }

    @After fun tearDown() = db.close()

    private fun seed() = runBlocking {
        db.homeDao().upsert(HomeEntity(id = "h1", name = "Ortiz house", createdAt = 1, updatedAt = 1))
        db.homeDao().upsert(HomeEntity(id = "h2", name = "Someone else", createdAt = 1, updatedAt = 1))
        db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main floor", createdAt = 1, updatedAt = 1))
        db.floorDao().upsert(FloorEntity(id = "f2", homeId = "h2", sortOrder = 0, name = "Other floor", createdAt = 1, updatedAt = 1))
        db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        db.wallDao().upsert(WallEntity(id = "w1", roomId = "r1", edgeIndex = 0, orientation = "N", lengthIn = 150, heightIn = 96, createdAt = 1, updatedAt = 1))
        val photo = tmp.newFile("wp1.jpg").apply { writeText("rough-in") }
        db.wallPhotoDao().upsert(
            WallPhotoEntity(
                id = "wp1", wallId = "w1", roomId = "r1", stage = "ROUGH_IN", originalPath = photo.absolutePath,
                texturePath = tmp.root.resolve("missing.jpg").absolutePath, takenAt = 5, createdAt = 5, updatedAt = 5, source = Sources.FIELD_LOG,
            ),
        )
        db.overlayDao().upsert(OverlayEntity(id = "o1", homeId = "h1", wallId = "w1", kind = "PIPE", label = "Supply line", createdAt = 6, updatedAt = 6))
        db.itemDao().upsert(ItemEntity(id = "i1", homeId = "h1", roomId = "r1", category = "APPLIANCE", subtype = "DISHWASHER", name = "Dishwasher", serial = "SN-42", createdAt = 7, updatedAt = 7))
        db.warrantyDao().upsert(WarrantyEntity(id = "wa1", itemId = "i1", partsMonths = 24, createdAt = 7, updatedAt = 7))
        db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Ortiz kitchen", homeId = "h1", createdAt = 1, updatedAt = 1))
        db.changeOrderDao().upsert(ChangeOrderEntity(id = "co1", jobId = "j1", title = "Move panel", status = "SIGNED", createdAt = 8, updatedAt = 8))
        db.changeOrderDao().upsert(ChangeOrderEntity(id = "co2", jobId = "j1", title = "Draft idea", status = "DRAFT", createdAt = 8, updatedAt = 8))
    }

    @Test fun exportsTheWholeHouseModelOfOneHomeOnly() = runBlocking<Unit> {
        seed()
        val out = checkNotNull(exporter.export("h1", Sources.FIELD_LOG))
        val b = out.bundle
        assertThat(b.homeId).isEqualTo("h1")
        assertThat(b.source).isEqualTo(Sources.FIELD_LOG)
        assertThat(b.exportedAt).isEqualTo(99L)
        assertThat(b.records.keys).containsAtLeast("home", "floor", "room", "wall", "wall_photo", "overlay", "item", "warranty", "change_order")
        assertThat(b.records.getValue("home").map { it.id }).containsExactly("h1")
        assertThat(b.records.getValue("floor").map { it.id }).containsExactly("f1")
        // Only signed change orders go to the homeowner.
        assertThat(b.records.getValue("change_order").map { it.id }).containsExactly("co1")
        // Every record carries id, createdAt, updatedAt and source.
        b.records.values.flatten().forEach { r ->
            assertThat(r.id).isNotEmpty()
            assertThat(r.fields).containsKey("createdAt")
            assertThat(r.fields).containsKey("updatedAt")
            assertThat(r.source).isNotNull()
        }
        assertThat(b.records.getValue("item").single().fields["serial"]?.jsonPrimitive?.content).isEqualTo("SN-42")
    }

    @Test fun localPathsBecomeMediaNamesAndMissingFilesAreLeftOut() = runBlocking<Unit> {
        seed()
        val out = checkNotNull(exporter.export("h1", Sources.FIELD_LOG))
        val photo = out.bundle.records.getValue("wall_photo").single().fields
        assertThat(photo).doesNotContainKey("originalPath")
        assertThat(photo).doesNotContainKey("texturePath")
        val media = photo[HomeBundleExporter.MEDIA_FIELD] as JsonObject
        assertThat(media).containsExactly("originalPath", JsonPrimitive("wp1.jpg"))
        assertThat(out.bundle.media).containsExactly("wp1.jpg")
        assertThat(out.mediaFile("wp1.jpg")?.readText()).isEqualTo("rough-in")
    }

    @Test fun exportWritesAndReadsBackThroughTheCodec() = runBlocking<Unit> {
        seed()
        val out = checkNotNull(exporter.export("h1", Sources.FIELD_LOG))
        val bytes = ByteArrayOutputStream().also { BundleWriter.write(out.bundle, it, out::mediaFile) }.toByteArray()
        val read = BundleReader.read(ByteArrayInputStream(bytes), tmp.newFolder("import"))
        val back = (read as BundleReadResult.Ok).bundle
        assertThat(back.records.mapValues { (_, v) -> v.map { it.id } }).isEqualTo(out.bundle.records.mapValues { (_, v) -> v.map { it.id } })
        assertThat(tmp.root.resolve("import/wp1.jpg").readText()).isEqualTo("rough-in")
    }

    @Test fun unknownOrDeletedHomeExportsNothing() = runBlocking<Unit> {
        seed()
        assertThat(exporter.export("nope", Sources.FIELD_LOG)).isNull()
        db.homeDao().upsert(HomeEntity(id = "h3", name = "Gone", createdAt = 1, updatedAt = 2, deletedAt = 2))
        assertThat(exporter.export("h3", Sources.FIELD_LOG)).isNull()
    }

    @Test fun ledgerFromAnAppliedPlanMakesTheSameImportANoOp() = runBlocking<Unit> {
        seed()
        val bundle = checkNotNull(exporter.export("h1", Sources.FIELD_LOG)).bundle
        // First import into an empty phone: everything is added; the ledger stores what came in.
        val first = ImportPlanner.plan(bundle, local = emptyMap())
        assertThat(first.added("room")).isEqualTo(1)
        db.importLedgerDao().setAll(first.ledgerRows("h1"))
        val base = db.importLedgerDao().forHome("h1").toImportBase()
        assertThat(base.getValue("wall_photo")).containsExactly("wp1", 5L)
        // The phone now holds the same records: planning the same bundle again changes nothing.
        val again = ImportPlanner.plan(bundle, local = bundle.records, base = base)
        assertThat(again.nothingToDo).isTrue()
    }

    @Test fun portableLocalSideMatchesTheExportedShape() {
        val row = JsonObject(
            mapOf("id" to JsonPrimitive("p1"), "path" to JsonPrimitive("/data/media/p1.jpg"), "caption" to JsonPrimitive("Sink"), "updatedAt" to JsonPrimitive(3)),
        )
        val local = HomeBundleExporter.portable("photo", row)
        assertThat(local).doesNotContainKey("path")
        assertThat(local[HomeBundleExporter.MEDIA_FIELD]).isEqualTo(JsonObject(mapOf("path" to JsonPrimitive("p1.jpg"))))
        assertThat(local["caption"]).isEqualTo(JsonPrimitive("Sink"))
    }
}
