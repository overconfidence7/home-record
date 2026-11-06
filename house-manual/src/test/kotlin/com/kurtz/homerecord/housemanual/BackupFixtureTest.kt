package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.BackupReadResult
import com.kurtz.homerecord.core.bundle.HomeBackup
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
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemRepository
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
import java.time.LocalDate

/**
 * `qa/fixtures/maple-street.hrbackup`, the backup flow H16 restores on a fresh install (AC-C48, AC-H14): a home with
 * 3 floors, 12 rooms, 25 items, 10 wall photos and 15 overlays sealed with the passphrase [PASSPHRASE]. It must stay a
 * backup this version opens and restores with nothing missing. `WRITE_BACKUP_FIXTURE=1 ./gradlew --no-daemon
 * :house-manual:testDebugUnitTest --tests '*BackupFixtureTest*'` writes it again from the house built here.
 */
@AcceptanceCriterion("AC-C48", "AC-H14")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackupFixtureTest {
    @get:Rule val tmp = TemporaryFolder()
    private val fixture = File("../qa/fixtures/maple-street.hrbackup")
    private val wallPhoto = File("../qa/fixtures/wall-kitchen.jpg")
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun db() = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()

    @Test fun fixtureRestoresTheWalkthroughHomeWithNothingMissing() = runBlocking<Unit> {
        // AC-C48, AC-H14
        if (System.getenv("WRITE_BACKUP_FIXTURE") != null) writeFixture()
        assertThat(fixture.length()).isLessThan(1_000_000L)
        val unpacked = tmp.newFolder("unpacked")
        val read = fixture.inputStream().use { HomeBackup.read(it, PASSPHRASE.toCharArray(), File(tmp.root, "cache/in.zip"), unpacked) }
        val bundle = (read as BackupReadResult.Ok).bundle
        val db = db()
        try {
            val store = RoomRowStore(db)
            val media = tmp.newFolder("media")
            val importer = HomeBundleImporter({ t, c, v -> store.readWhere(t, c, v) }, store, db.importLedgerDao(), media)
            val preview = importer.preview(bundle)
            assertThat(preview.incoming("floor")).isEqualTo(3)
            assertThat(preview.incoming("room")).isEqualTo(12)
            assertThat(preview.incoming("item")).isEqualTo(25)
            assertThat(preview.incoming("wall_photo")).isEqualTo(10)
            assertThat(preview.incoming("overlay")).isEqualTo(15)
            val outcome = importer.apply(preview, unpacked)
            assertThat(outcome.mediaCopied).isEqualTo(10)
            val homeId = bundle.records.getValue("home").single().id
            assertThat(db.homeDao().get(homeId)?.name).isEqualTo("Maple Street house")
            // Spot-check: the Kitchen's wall photo came back byte-for-byte
            val restored = checkNotNull(db.wallPhotoDao().get("maple-wp-kitchen"))
            assertThat(File(media, File(restored.originalPath).name).readBytes()).isEqualTo(wallPhoto.readBytes())
        } finally {
            db.close()
        }
    }

    private suspend fun writeFixture() {
        val db = db()
        try {
            val strings: (Int) -> String = context.resources::getString
            val repo = HomeRepository(db, strings)
            val homeId = repo.createHome(NewHome("Maple Street house", "1987 Maple Street", HouseType.TWO_STORY, Foundation.BASEMENT, attachedGarage = false, seedFromTemplate = false))
            val floors = db.floorDao().listFor(homeId)
            check(floors.size == 3) { "template gave ${floors.size} floors" }
            plans.forEachIndexed { i, rooms -> repo.savePlan(floors[i].id, rooms) }
            val roomIds = plans.flatten().map { it.id }
            val at = 1_762_300_000_000
            // A finished-wall photo on the first wall of ten rooms
            roomIds.filter { it !in setOf("maple-storage", "maple-halfbath") }.forEach { roomId ->
                val wall = db.wallDao().listFor(roomId).first()
                val id = "maple-wp-${roomId.removePrefix("maple-")}"
                val photo = tmp.newFile("$id.jpg")
                wallPhoto.copyTo(photo, overwrite = true)
                db.wallPhotoDao().upsert(
                    WallPhotoEntity(id = id, wallId = wall.id, roomId = roomId, stage = "FINISHED", originalPath = photo.absolutePath, takenAt = at, createdAt = at, updatedAt = at),
                )
            }
            overlays.forEachIndexed { i, (roomId, kind, label) ->
                val wall = db.wallDao().listFor(roomId).first()
                val points = if (kind.shape == com.kurtz.homerecord.housemodel.overlays.OverlayShape.LINE) """[{"x":0.15,"y":0.4},{"x":0.85,"y":0.4}]""" else """[{"x":0.5,"y":0.7}]"""
                db.overlayDao().upsert(
                    OverlayEntity(id = "maple-o${i + 1}", homeId = homeId, roomId = roomId, wallId = wall.id, kind = kind.wire, label = label, pointsJson = points, createdAt = at, updatedAt = at),
                )
            }
            var clock = at
            val items = ItemRepository(db, strings, now = { clock++ }, today = { LocalDate.of(2026, 11, 6) })
            this.items.forEach { (subtype, name, roomId, makeModel) ->
                val (make, model) = makeModel
                checkNotNull(items.save(homeId, ItemDraft(subtypeId = subtype, roomId = roomId, name = name, make = make, model = model))) { "item $name did not save" }
            }
            val store = RoomRowStore(db)
            val out = checkNotNull(HomeBundleExporter({ t, c, v -> store.readWhere(t, c, v) }).export(homeId, Sources.HOUSE_MANUAL))
            fixture.outputStream().use { HomeBackup.write(out, it, PASSPHRASE.toCharArray(), File(tmp.root, "cache/out.zip")) }
        } finally {
            db.close()
        }
    }

    private fun room(id: String, name: String, kind: String, x: Int, y: Int, w: Int, l: Int) =
        PlanRoom.rectangle("maple-$id", name, kind, x * FT, y * FT, w * FT, l * FT)

    private val plans = listOf(
        listOf(
            room("utility", "Utility room", "UTILITY", 0, 0, 12, 12),
            room("workshop", "Workshop", "OTHER", 12, 0, 14, 12),
            room("storage", "Storage", "OTHER", 0, 12, 10, 10),
            room("rec", "Rec room", "OTHER", 10, 12, 16, 14),
        ),
        listOf(
            room("kitchen", "Kitchen", "KITCHEN", 0, 0, 14, 12),
            room("living", "Living room", "LIVING_ROOM", 14, 0, 18, 14),
            room("dining", "Dining room", "DINING_ROOM", 0, 12, 14, 12),
            room("halfbath", "Half bath", "BATHROOM", 14, 14, 6, 6),
        ),
        listOf(
            room("primary", "Primary bedroom", "BEDROOM", 0, 0, 16, 14),
            room("bath", "Hall bath", "BATHROOM", 16, 0, 10, 10),
            room("bed2", "Bedroom 2", "BEDROOM", 0, 14, 12, 12),
            room("laundry", "Laundry", "LAUNDRY", 12, 14, 10, 12),
        ),
    )

    private val overlays = listOf(
        Triple("maple-kitchen", OverlayKind.WATER_COLD, "Sink cold supply"),
        Triple("maple-kitchen", OverlayKind.WATER_HOT, "Sink hot supply"),
        Triple("maple-kitchen", OverlayKind.DRAIN, "Sink drain"),
        Triple("maple-kitchen", OverlayKind.ELECTRICAL_WIRE, "Range circuit"),
        Triple("maple-kitchen", OverlayKind.OUTLET, "Counter outlet"),
        Triple("maple-living", OverlayKind.LV_DATA, "Ethernet to the TV"),
        Triple("maple-living", OverlayKind.LV_COAX, "Coax"),
        Triple("maple-living", OverlayKind.SWITCH, "Ceiling fan switch"),
        Triple("maple-dining", OverlayKind.LIGHT, "Chandelier box"),
        Triple("maple-bath", OverlayKind.WATER_COLD, "Shower cold supply"),
        Triple("maple-bath", OverlayKind.VENT, "Fan vent"),
        Triple("maple-laundry", OverlayKind.DRAIN, "Washer standpipe"),
        Triple("maple-laundry", OverlayKind.DUCT, "Dryer vent"),
        Triple("maple-utility", OverlayKind.GAS, "Furnace gas line"),
        Triple("maple-utility", OverlayKind.VALVE, "Water main valve"),
    )

    private data class SeedItem(val subtype: String, val name: String, val roomId: String, val makeModel: Pair<String, String> = "" to "")

    private val items = listOf(
        SeedItem("furnace", "Furnace", "maple-utility", "Carrier" to "59SC5A080"),
        SeedItem("water_heater", "Water heater", "maple-utility", "Rheem" to "XE50T10HD50U1"),
        SeedItem("water_softener", "Water softener", "maple-utility"),
        SeedItem("sump_pump", "Sump pump", "maple-utility", "Zoeller" to "M53"),
        SeedItem("breaker_panel", "Main panel", "maple-utility", "Square D" to "QO140M200"),
        SeedItem("water_main", "Water main shutoff", "maple-utility"),
        SeedItem("dehumidifier", "Dehumidifier", "maple-rec"),
        SeedItem("router", "Wi-Fi router", "maple-rec", "eero" to "Pro 6E"),
        SeedItem("refrigerator", "Refrigerator", "maple-kitchen", "LG" to "LRMVS3006S"),
        SeedItem("dishwasher", "Dishwasher", "maple-kitchen", "Bosch" to "SHPM88Z75N"),
        SeedItem("range", "Gas range", "maple-kitchen", "GE" to "JGB735SPSS"),
        SeedItem("range_hood", "Range hood", "maple-kitchen", "Broan" to ""),
        SeedItem("microwave", "Microwave", "maple-kitchen", "Panasonic" to "NN-SN966S"),
        SeedItem("faucet", "Kitchen faucet", "maple-kitchen", "Moen" to "7594ESRS"),
        SeedItem("thermostat", "Thermostat", "maple-living", "ecobee" to "EB-STATE6P-01"),
        SeedItem("toilet", "Half bath toilet", "maple-halfbath", "TOTO" to "Drake CST776CEG"),
        SeedItem("toilet", "Hall bath toilet", "maple-bath", "Kohler" to "K-3999"),
        SeedItem("faucet", "Hall bath faucet", "maple-bath", "Moen" to "6410BN"),
        SeedItem("washer", "Washer", "maple-laundry", "Whirlpool" to "WTW8127LC"),
        SeedItem("dryer", "Dryer", "maple-laundry", "Whirlpool" to "WED8127LW"),
        SeedItem("smoke_detector", "Upstairs smoke alarm", "maple-primary", "First Alert" to "SC9120B"),
        SeedItem("smoke_detector", "Bedroom 2 smoke alarm", "maple-bed2", "First Alert" to "SC9120B"),
        SeedItem("ac_condenser", "AC condenser", "maple-workshop", "Trane" to "4TTR4036L1000A"),
        SeedItem("garage_door_opener", "Workshop door opener", "maple-workshop", "Chamberlain" to "B4505T"),
        SeedItem("dehumidifier", "Storage dehumidifier", "maple-storage"),
    )

    companion object {
        const val PASSPHRASE = "maple-street-1987"
        private const val FT = 12
    }
}
