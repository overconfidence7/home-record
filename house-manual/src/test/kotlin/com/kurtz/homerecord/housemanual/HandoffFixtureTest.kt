package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.BundleReadResult
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.bundle.HomeBundleExporter
import com.kurtz.homerecord.core.bundle.HomeBundleImporter
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * `qa/fixtures/ortiz-handoff.homerecord`, the file flow H18 imports (AC-H17): it must stay a bundle this version
 * reads and imports with its wall photo and overlays. `WRITE_HANDOFF_FIXTURE=1 ./gradlew --no-daemon
 * :house-manual:testDebugUnitTest --tests '*HandoffFixtureTest*'` writes it again from the house built here.
 */
@AcceptanceCriterion("AC-H17")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HandoffFixtureTest {
    @get:Rule val tmp = TemporaryFolder()
    private val fixture = File("../qa/fixtures/ortiz-handoff.homerecord")
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun db() = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()

    @Test fun fixtureImportsWithWallPhotosAndOverlays() = runBlocking<Unit> {
        if (System.getenv("WRITE_HANDOFF_FIXTURE") != null) writeFixture()
        val db = db()
        try {
            val read = BundleReader.read(fixture.inputStream(), tmp.newFolder("unpacked")) as BundleReadResult.Ok
            val store = RoomRowStore(db)
            val importer = HomeBundleImporter({ t, c, v -> store.readWhere(t, c, v) }, store, db.importLedgerDao(), tmp.newFolder("media"))
            val preview = importer.preview(read.bundle)
            assertThat(preview.incoming("room")).isEqualTo(2)
            assertThat(preview.incoming("wall_photo")).isEqualTo(1)
            assertThat(preview.incoming("overlay")).isEqualTo(2)
            val outcome = importer.apply(preview, tmp.root.resolve("unpacked"))
            assertThat(outcome.mediaCopied).isEqualTo(1)
            assertThat(db.wallPhotoDao().get("ortiz-wp1")?.originalPath).endsWith("ortiz-wp1.jpg")
            // The crew's marks keep their kinds, so they land on the Electrical and Drain/vent layers
            assertThat(OverlayKind.fromWire(db.overlayDao().get("ortiz-o1")?.kind)).isEqualTo(OverlayKind.ELECTRICAL_WIRE)
            assertThat(OverlayKind.fromWire(db.overlayDao().get("ortiz-o2")?.kind)).isEqualTo(OverlayKind.DRAIN)
        } finally {
            db.close()
        }
    }

    private suspend fun writeFixture() {
        val db = db()
        try {
            val strings: (Int) -> String = context.resources::getString
            val repo = HomeRepository(db, strings)
            val homeId = repo.createHome(NewHome("Ortiz house", "41 Alder Court", HouseType.RANCHER, Foundation.SLAB, attachedGarage = false))
            val floor = db.floorDao().listFor(homeId).first()
            val ft = 12
            repo.savePlan(
                floor.id,
                listOf(
                    PlanRoom.rectangle("ortiz-kitchen", "Kitchen", "KITCHEN", 0, 0, 14 * ft, 12 * ft),
                    PlanRoom.rectangle("ortiz-bath", "Hall bath", "BATHROOM", 14 * ft, 0, 8 * ft, 10 * ft),
                ),
            )
            val wall = db.wallDao().listFor("ortiz-kitchen").first()
            val photo = tmp.newFile("ortiz-wp1.jpg")
            File("../qa/fixtures/wall-rough-in.jpg").copyTo(photo, overwrite = true)
            db.wallPhotoDao().upsert(
                WallPhotoEntity(
                    id = "ortiz-wp1", wallId = wall.id, roomId = "ortiz-kitchen", stage = "ROUGH_IN", originalPath = photo.absolutePath,
                    takenAt = 1_762_300_000_000, createdAt = 1_762_300_000_000, updatedAt = 1_762_300_000_000, source = Sources.FIELD_LOG,
                ),
            )
            db.overlayDao().upsert(
                OverlayEntity(
                    id = "ortiz-o1", homeId = homeId, roomId = "ortiz-kitchen", wallId = wall.id, kind = OverlayKind.ELECTRICAL_WIRE.wire, label = "Range circuit",
                    stage = "ROUGH_IN", pointsJson = """[{"x":0.1,"y":0.3},{"x":0.8,"y":0.3}]""", createdAt = 1_762_300_000_000, updatedAt = 1_762_300_000_000, source = Sources.FIELD_LOG,
                ),
            )
            db.overlayDao().upsert(
                OverlayEntity(
                    id = "ortiz-o2", homeId = homeId, roomId = "ortiz-kitchen", wallId = wall.id, kind = OverlayKind.DRAIN.wire, label = "Sink drain",
                    stage = "ROUGH_IN", pointsJson = """[{"x":0.5,"y":0.9},{"x":0.5,"y":0.6}]""", createdAt = 1_762_300_000_000, updatedAt = 1_762_300_000_000, source = Sources.FIELD_LOG,
                ),
            )
            val store = RoomRowStore(db)
            val out = checkNotNull(HomeBundleExporter({ t, c, v -> store.readWhere(t, c, v) }).export(homeId, Sources.FIELD_LOG))
            fixture.outputStream().use { BundleWriter.write(out.bundle, it, out::mediaFile) }
        } finally {
            db.close()
        }
    }
}
