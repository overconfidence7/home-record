package com.kurtz.homerecord.housemanual.qa

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.data.ServiceRecordEntity
import com.kurtz.homerecord.core.data.WarrantyEntity
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.Pt
import java.io.File
import java.time.LocalDate

/**
 * Debug seed for the QA suite: a two-story home with a basement, 12 placed rooms, doors, windows and stairs, and
 * the records a lived-in home has (cr:seed-items): 22 items across rooms, Exterior and Whole house; warranties that
 * are active, expiring and expired; items missing a make or model; the consumables and reminders their types
 * seed, one reminder overdue; two contacts with service visits; and two PDFs (a home document and a manual).
 */
object RegressionSeed {
    private const val FT = 12

    private fun room(id: String, name: String, kind: String, x: Int, y: Int, w: Int, l: Int) =
        PlanRoom.rectangle(id, name, kind, x * FT, y * FT, w * FT, l * FT)

    private fun door(id: String, edge: Int, offsetFt: Int) = PlanOpening(id, edge, OpeningKind.DOOR, offsetFt * FT, PlanRoom.DEFAULT_DOOR_IN, 80)
    private fun window(id: String, edge: Int, offsetFt: Int) = PlanOpening(id, edge, OpeningKind.WINDOW, offsetFt * FT, PlanRoom.DEFAULT_WINDOW_IN, 48)

    val mainFloor: List<PlanRoom> = listOf(
        room("qa-kitchen", "Kitchen", "KITCHEN", 0, 0, 14, 12).copy(openings = listOf(door("qa-d1", 1, 4), window("qa-w1", 0, 5))),
        room("qa-living", "Living room", "LIVING_ROOM", 14, 0, 18, 14).copy(
            openings = listOf(door("qa-d2", 3, 5), window("qa-w2", 0, 6)),
            stairs = listOf(PlanStair("qa-s1", null, listOf(Pt(27 * FT, 8 * FT), Pt(31 * FT, 8 * FT), Pt(31 * FT, 13 * FT), Pt(27 * FT, 13 * FT)))),
        ),
        room("qa-dining", "Dining room", "DINING_ROOM", 0, 12, 14, 12).copy(openings = listOf(window("qa-w3", 2, 4))),
        room("qa-mudroom", "Mudroom", "MUDROOM", 14, 14, 8, 8),
        room("qa-powder", "Powder room", "BATHROOM", 22, 14, 6, 8).copy(openings = listOf(door("qa-d3", 3, 2))),
    )

    val upperFloor: List<PlanRoom> = listOf(
        room("qa-primary", "Primary bedroom", "BEDROOM", 0, 0, 16, 14).copy(openings = listOf(door("qa-d4", 1, 3), window("qa-w4", 0, 6))),
        room("qa-bath", "Primary bath", "BATHROOM", 16, 0, 10, 10),
        room("qa-bed2", "Bedroom 2", "BEDROOM", 0, 14, 12, 12).copy(openings = listOf(window("qa-w5", 2, 4))),
        room("qa-bed3", "Bedroom 3", "BEDROOM", 12, 14, 12, 12),
    )

    val basement: List<PlanRoom> = listOf(
        room("qa-utility", "Utility room", "UTILITY", 0, 0, 12, 12),
        room("qa-laundry", "Laundry", "LAUNDRY", 12, 0, 10, 12),
        room("qa-rec", "Rec room", "OTHER", 0, 12, 22, 14),
    )

    /** One seeded item: [warrantyDays] from today to the warranty end (negative is expired), null for none. */
    private data class SeedItem(
        val subtype: String, val name: String, val roomId: String, val make: String = "", val model: String = "",
        val warrantyDays: Long? = null, val registrationRequired: Boolean = false,
    )

    private val items = listOf(
        SeedItem("furnace", "Furnace", "qa-utility", "Carrier", "59SC5A080", warrantyDays = 1460),
        SeedItem("water_heater", "Water heater", "qa-utility", "Rheem", "XE50T10HD50U1", warrantyDays = 30, registrationRequired = true),
        SeedItem("water_softener", "Water softener", "qa-utility"),
        SeedItem("sump_pump", "Sump pump", "qa-utility", "Zoeller", "M53"),
        SeedItem("breaker_panel", "Main panel", "qa-utility", "Square D", "QO140M200"),
        SeedItem("water_main", "Water main shutoff", "qa-utility"),
        SeedItem("gas_main", "Gas main shutoff", PseudoRooms.EXTERIOR),
        SeedItem("ac_condenser", "AC condenser", PseudoRooms.EXTERIOR, "Trane", "4TTR4036L1000A", warrantyDays = -200),
        SeedItem("roof", "Roof", PseudoRooms.EXTERIOR, "GAF", "Timberline HDZ", warrantyDays = 9000),
        SeedItem("refrigerator", "Refrigerator", "qa-kitchen", "LG", "LRMVS3006S", warrantyDays = 45),
        SeedItem("dishwasher", "Dishwasher", "qa-kitchen", "Bosch", "SHPM88Z75N", warrantyDays = 400),
        SeedItem("range", "Gas range", "qa-kitchen", "GE", "JGB735SPSS", warrantyDays = -90),
        SeedItem("range_hood", "Range hood", "qa-kitchen", "Broan"),
        SeedItem("microwave", "Microwave", "qa-kitchen", "Panasonic", "NN-SN966S"),
        SeedItem("washer", "Washer", "qa-laundry", "Whirlpool", "WTW8127LC", warrantyDays = 200),
        SeedItem("dryer", "Dryer", "qa-laundry", "Whirlpool", "WED8127LW", warrantyDays = 200),
        SeedItem("thermostat", "Thermostat", "qa-living", "ecobee", "EB-STATE6P-01"),
        SeedItem("smoke_detector", "Hallway smoke alarm", PseudoRooms.WHOLE_HOUSE, "First Alert", "SC9120B"),
        SeedItem("toilet", "Toilet", "qa-powder", "TOTO", "Drake CST776CEG"),
        SeedItem("faucet", "Bath faucet", "qa-bath", "Moen", "6410BN"),
        SeedItem("dehumidifier", "Dehumidifier", "qa-rec"),
        SeedItem("router", "Wi-Fi router", "qa-rec", "eero", "Pro 6E"),
    )

    /**
     * Replaces whatever is on the device with the regression home. Returns the home id. [documentsDir] and [pdf]
     * give the two seeded documents a real file; without them no documents are seeded.
     */
    suspend fun seed(
        db: HomeRecordDatabase,
        repo: HomeRepository,
        today: LocalDate = LocalDate.now(),
        documentsDir: File? = null,
        pdf: ByteArray? = null,
        now: Long = System.currentTimeMillis(),
    ): String {
        reset(db)
        val homeId = repo.createHome(NewHome("QA House", "12 Test Lane", HouseType.TWO_STORY, Foundation.BASEMENT, attachedGarage = false))
        val floors = db.floorDao().listFor(homeId)
        check(floors.size >= 3) { "template gave ${floors.size} floors" }
        repo.savePlan(floors[0].id, basement)
        repo.savePlan(floors[1].id, mainFloor)
        repo.savePlan(floors[2].id, upperFloor)
        seedRecords(db, homeId, today, documentsDir, pdf, now)
        return homeId
    }

    private suspend fun seedRecords(db: HomeRecordDatabase, homeId: String, today: LocalDate, documentsDir: File?, pdf: ByteArray?, now: Long) {
        // Through the repository so each type gets the consumables and reminders it seeds for a real user
        // One millisecond apart so Recently added has a stable order
        var clock = now
        val itemRepo = ItemRepository(db, now = { clock++ }, today = { today })
        val ids = items.associate { seed ->
            val draft = ItemDraft(subtypeId = seed.subtype, roomId = seed.roomId, name = seed.name, make = seed.make, model = seed.model)
            seed.name to checkNotNull(itemRepo.save(homeId, draft)) { "seed item ${seed.name} did not save" }
        }
        items.filter { it.warrantyDays != null }.forEach { seed ->
            val endsOn = today.plusDays(seed.warrantyDays ?: 0)
            db.warrantyDao().upsert(
                WarrantyEntity(
                    id = Ids.newId(), itemId = ids.getValue(seed.name), partsMonths = 60, laborMonths = 12, endsOn = endsOn.toString(),
                    registrationRequired = seed.registrationRequired, createdAt = now, updatedAt = now,
                ),
            )
        }
        // The furnace's yearly service is 12 days late, so Home and Reminders show an overdue row
        val furnace = ids.getValue("Furnace")
        db.maintenanceTaskDao().listForItem(furnace).forEach {
            db.maintenanceTaskDao().upsert(it.copy(dueOn = today.minusDays(12).toString(), lastDoneOn = today.minusMonths(13).toString(), updatedAt = now))
        }
        db.maintenanceTaskDao().upsert(
            MaintenanceTaskEntity(id = Ids.newId(), homeId = homeId, title = "Clean gutters", intervalMonths = 6, dueOn = today.plusDays(21).toString(), seasonal = true, createdAt = now, updatedAt = now),
        )

        val dale = Ids.newId()
        val maria = Ids.newId()
        db.contactDao().upsert(ContactEntity(id = dale, homeId = homeId, name = "Dale Harlan", company = "Harlan Heating & Air", trade = "HVAC", phone = "555-0142", email = "dale@harlanhvac.example", createdAt = now, updatedAt = now))
        db.contactDao().upsert(ContactEntity(id = maria, homeId = homeId, name = "Maria Soto", company = "Soto Plumbing", trade = "Plumber", phone = "555-0187", createdAt = now, updatedAt = now))
        db.serviceRecordDao().upsert(ServiceRecordEntity(id = Ids.newId(), itemId = furnace, date = today.minusMonths(13).toString(), contactId = dale, description = "Annual tune-up, replaced igniter", costCents = 18900, createdAt = now, updatedAt = now))
        db.serviceRecordDao().upsert(ServiceRecordEntity(id = Ids.newId(), itemId = ids.getValue("Water heater"), date = today.minusMonths(5).toString(), contactId = maria, description = "Flushed tank, new anode rod", costCents = 22500, createdAt = now, updatedAt = now))

        if (documentsDir != null && pdf != null) {
            // The tables were cleared, so any file left from an earlier seed is an orphan
            documentsDir.deleteRecursively()
            documentsDir.mkdirs()
            listOf(
                Triple("Home inspection report", "Purchase", null),
                Triple("Furnace installation manual", "Manuals", furnace),
            ).forEach { (name, folder, itemId) ->
                val id = Ids.newId()
                val file = File(documentsDir, "$id.pdf").apply { writeBytes(pdf) }
                db.documentDao().upsert(
                    DocumentEntity(id = id, homeId = homeId, itemId = itemId, folder = folder, name = name, path = file.absolutePath, mime = "application/pdf", size = file.length(), createdAt = now, updatedAt = now),
                )
            }
        }
    }

    /** What every room of a well-documented home records: finishes with their colors, alarms and network gear. */
    private val perRoom = listOf(
        SeedItem("paint", "Wall paint", "", "Sherwin-Williams", "SW 7008 Alabaster"),
        SeedItem("paint", "Ceiling paint", "", "Benjamin Moore", "Ceiling White 508"),
        SeedItem("paint", "Trim paint", "", "Sherwin-Williams", "SW 7006 Extra White"),
        SeedItem("flooring", "Flooring", "", "Shaw", "Repel Oak"),
        SeedItem("trim", "Baseboard", "", "Metrie", "MDF 5-1/4 in"),
        SeedItem("trim", "Door casing", "", "Metrie", "Colonial 2-1/4 in"),
        SeedItem("windows", "Window", "", "Andersen", "400 Series"),
        SeedItem("smoke_detector", "Smoke alarm", "", "First Alert", "SC9120B"),
        SeedItem("co_detector", "CO alarm", "", "Kidde", "KN-COB-DCR-CA"),
        SeedItem("mesh_node", "Mesh node", "", "eero", "Pro 6E"),
        SeedItem("lock", "Door lock", "", "Schlage", "BE469ZP"),
        SeedItem("camera", "Camera", "", "Wyze", "Cam v3"),
        SeedItem("fixture_shutoff", "Light switch", "", "Lutron", "Caseta PD-6WCL"),
        SeedItem("surge_protector", "Surge protector", "", "APC", "P11VNT3"),
        SeedItem("cabinets", "Built-in shelving", ""),
    )

    /**
     * The regression home grown to [total] items for the AC-U11 scroll measurement (S7: "200-row lists scroll
     * without jank"): the regression items, then each room's finishes and devices, room by room.
     */
    suspend fun seedLongList(
        db: HomeRecordDatabase,
        repo: HomeRepository,
        total: Int = LONG_LIST_ITEMS,
        today: LocalDate = LocalDate.now(),
        documentsDir: File? = null,
        pdf: ByteArray? = null,
        now: Long = System.currentTimeMillis(),
    ): String {
        val homeId = seed(db, repo, today, documentsDir, pdf, now)
        var clock = now + items.size
        val itemRepo = ItemRepository(db, now = { clock++ }, today = { today })
        val rooms = mainFloor + upperFloor + basement
        val extra = rooms.flatMap { room -> perRoom.map { it.copy(name = "${it.name}, ${room.name}", roomId = room.id) } }
        extra.take((total - items.size).coerceAtLeast(0)).forEach { seed ->
            val draft = ItemDraft(subtypeId = seed.subtype, roomId = seed.roomId, name = seed.name, make = seed.make, model = seed.model)
            checkNotNull(itemRepo.save(homeId, draft)) { "seed item ${seed.name} did not save" }
        }
        return homeId
    }

    const val LONG_LIST_ITEMS = 200

    suspend fun reset(db: HomeRecordDatabase) = db.clearAllTables()
}
