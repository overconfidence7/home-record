package com.kurtz.homerecord.housemodel

import com.kurtz.homerecord.core.testing.AcceptanceCriterion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanCodec
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<String>()
    private var clock = 1_000L
    private lateinit var repo: HomeRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeRepository(db, context.resources::getString, now = { clock++ }, onChange = { entity, id -> changes += "$entity/$id" })
    }

    @After fun tearDown() = db.close()

    private suspend fun colonial(garage: Boolean = true) =
        repo.createHome(NewHome(" Maple St ", "  ", HouseType.COLONIAL, Foundation.BASEMENT, garage))

    @AcceptanceCriterion("AC-C1")
    @Test fun createHomeSeedsFloorsFromTemplateAndTrimsName() = runTest {
        val id = colonial()
        val home = checkNotNull(repo.observeHome().first())
        assertThat(home.id).isEqualTo(id)
        assertThat(home.name).isEqualTo("Maple St")
        assertThat(home.address).isNull()
        assertThat(home.houseType).isEqualTo("COLONIAL")
        assertThat(repo.observeFloors(id).first().map { it.name }).containsExactly("Basement", "Main", "Upper").inOrder()
    }

    @Test fun attachedGarageSeedsGarageRoomOnMain() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        assertThat(main.roomCount).isEqualTo(1)
        assertThat(db.roomDao().listFor(main.id).single().name).isEqualTo("Garage")
    }

    @AcceptanceCriterion("AC-C2")
    @Test fun seededFloorAndGarageNamesComeFromStringResources() = runTest {
        // Another language's resources name the seeded floors and the Garage; the stored names follow them.
        val translated = mapOf(
            R.string.floor_default_basement to "Sous-sol",
            R.string.floor_default_main to "Rez-de-chaussée",
            R.string.floor_default_upper to "Étage",
            R.string.room_kind_garage to "Garage double",
        )
        val french = HomeRepository(db, { translated.getValue(it) })
        val id = french.createHome(NewHome("Maison", null, HouseType.COLONIAL, Foundation.BASEMENT, attachedGarage = true))
        val floors = french.observeFloors(id).first()
        assertThat(floors.map { it.name }).containsExactly("Sous-sol", "Rez-de-chaussée", "Étage").inOrder()
        assertThat(db.roomDao().listFor(floors[1].id).single().name).isEqualTo("Garage double")
    }

    @Test fun noGarageMeansNoRooms() = runTest {
        val id = colonial(garage = false)
        assertThat(repo.observeFloors(id).first().sumOf { it.roomCount }).isEqualTo(0)
    }

    @Test fun everyWriteIsReportedForSync() = runTest {
        val id = colonial()
        assertThat(changes.filter { it.startsWith("home/") }).containsExactly("home/$id")
        assertThat(changes.count { it.startsWith("floor/") }).isEqualTo(3)
        assertThat(changes.count { it.startsWith("room/") }).isEqualTo(1)
    }

    @Test fun skipForNowKeepsTheTypesFloorsButSeedsNoRooms() = runTest {
        val id = repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, attachedGarage = true, seedFromTemplate = false))
        val floors = repo.observeFloors(id).first()
        assertThat(floors.map { it.name }).containsExactly("Basement", "Main", "Upper").inOrder()
        assertThat(floors.sumOf { it.roomCount }).isEqualTo(0)
    }

    @AcceptanceCriterion("AC-C3")
    @Test fun addFloorGoesOnTopAndUpdateWorks() = runTest {
        val id = colonial()
        val attic = repo.addFloor(id, " Attic ")
        assertThat(repo.observeFloors(id).first().map { it.name }.last()).isEqualTo("Attic")
        repo.updateFloor(attic, " Loft ", 84)
        assertThat(db.floorDao().get(attic)?.name).isEqualTo("Loft")
        assertThat(db.floorDao().get(attic)?.ceilingHeightIn).isEqualTo(84)
        assertThat(checkNotNull(db.floorDao().get(attic)).elevationIn).isGreaterThan(db.floorDao().listFor(id).first { it.name == "Upper" }.elevationIn)
    }

    @AcceptanceCriterion("AC-C3")
    @Test fun moveFloorSwapsNeighboursAndStopsAtTheEnds() = runTest {
        val id = colonial()
        val upper = repo.observeFloors(id).first().single { it.name == "Upper" }
        repo.moveFloor(id, upper.id, -1)
        assertThat(repo.observeFloors(id).first().map { it.name }).containsExactly("Basement", "Upper", "Main").inOrder()
        repo.moveFloor(id, upper.id, -5)
        repo.moveFloor(id, upper.id, -1)
        repo.moveFloor(id, upper.id, -1)
        assertThat(repo.observeFloors(id).first().first().name).isEqualTo("Upper")
    }

    @AcceptanceCriterion("AC-C3")
    @Test fun deleteFloorSoftDeletesItsRooms() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        assertThat(repo.roomCount(main.id)).isEqualTo(1)
        repo.deleteFloor(main.id)
        assertThat(repo.observeFloors(id).first().map { it.name }).containsExactly("Basement", "Upper").inOrder()
        assertThat(db.roomDao().listFor(main.id)).isEmpty()
        assertThat(changes).contains("floor/${main.id}")
    }

    // cr:room-delete-cascade: "Its walls, doors and windows go with it, and its items move to Whole house."
    @AcceptanceCriterion("AC-C3")
    @Test fun deletingARoomTakesItsWallsOpeningsAndStairsAndMovesItsItems() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val upper = repo.observeFloors(id).first().single { it.name == "Upper" }
        val hall = PlanRoom.rectangle("h1", "Hall", "HALL", 0, 0, 144, 120)
            .withOpening(PlanOpening("o1", 0, OpeningKind.DOOR, 20, 32, 80))
            .withStair(PlanStair("s1", upper.id, listOf(Pt(10, 10), Pt(46, 10), Pt(46, 130), Pt(10, 130))))
        val den = PlanRoom.rectangle("d1", "Den", "OFFICE", 144, 0, 120, 120)
        repo.savePlan(main.id, listOf(hall, den))
        val wallId = db.wallDao().listFor("h1").single { it.edgeIndex == 0 }.id
        db.itemDao().upsert(com.kurtz.homerecord.core.data.ItemEntity(id = "i1", homeId = id, roomId = "h1", category = "LIGHTING", subtype = "light", name = "Hall light", wallId = wallId, createdAt = 1, updatedAt = 1))
        db.photoDao().upsert(com.kurtz.homerecord.core.data.PhotoEntity(id = "p1", homeId = id, roomId = "h1", path = "/m/p.jpg", takenAt = 1, createdAt = 1, updatedAt = 1))
        changes.clear()

        repo.savePlan(main.id, listOf(den))

        assertThat(db.wallDao().listFor("h1")).isEmpty()
        assertThat(db.openingDao().listFor(wallId)).isEmpty()
        assertThat(db.stairDao().listFor("h1")).isEmpty()
        val light = checkNotNull(db.itemDao().get("i1"))
        assertThat(light.roomId).isEqualTo(com.kurtz.homerecord.housemodel.items.PseudoRooms.WHOLE_HOUSE)
        assertThat(light.wallId).isNull()
        assertThat(checkNotNull(db.photoDao().get("p1")).roomId).isNull()
        assertThat(changes).containsAtLeast("room/h1", "wall/$wallId", "opening/o1", "stair/s1", "item/i1", "photo/p1")
        assertThat(repo.loadPlan(main.id).placed.map { it.id }).containsExactly("d1")
    }

    @AcceptanceCriterion("AC-C3")
    @Test fun deletingAFloorCascadesToItsRoomsAndUnlinksStairsThatLedThere() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val upper = repo.observeFloors(id).first().single { it.name == "Upper" }
        repo.savePlan(main.id, listOf(PlanRoom.rectangle("h1", "Hall", "HALL", 0, 0, 144, 120).withStair(PlanStair("s1", upper.id, listOf(Pt(10, 10), Pt(46, 10), Pt(46, 130), Pt(10, 130))))))
        val bedroom = PlanRoom.rectangle("b1", "Bedroom", "BEDROOM", 0, 0, 144, 120).withOpening(PlanOpening("o9", 1, OpeningKind.WINDOW, 30, 36, 48))
        repo.savePlan(upper.id, listOf(bedroom))
        val wallId = db.wallDao().listFor("b1").single { it.edgeIndex == 1 }.id
        db.itemDao().upsert(com.kurtz.homerecord.core.data.ItemEntity(id = "i2", homeId = id, roomId = "b1", category = "LIGHTING", subtype = "light", name = "Ceiling fan", createdAt = 1, updatedAt = 1))
        changes.clear()

        repo.deleteFloor(upper.id)

        assertThat(db.roomDao().listFor(upper.id)).isEmpty()
        assertThat(db.wallDao().listFor("b1")).isEmpty()
        assertThat(db.openingDao().listFor(wallId)).isEmpty()
        assertThat(checkNotNull(db.itemDao().get("i2")).roomId).isEqualTo(com.kurtz.homerecord.housemodel.items.PseudoRooms.WHOLE_HOUSE)
        assertThat(checkNotNull(db.stairDao().get("s1")).toFloorId).isNull()
        assertThat(changes).containsAtLeast("floor/${upper.id}", "room/b1", "wall/$wallId", "opening/o9", "item/i2", "stair/s1")
    }

    @Test fun savePlanRoundTripsAndSoftDeletesRemovedRooms() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val before = repo.loadPlan(main.id)
        assertThat(before.placed).isEmpty()
        assertThat(before.unplaced.map { it.name }).containsExactly("Garage")

        val kitchen = PlanRoom.rectangle("k1", "Kitchen", "KITCHEN", 0, 0, 144, 120)
        val den = PlanRoom.rectangle("d1", "Den", "OFFICE", 144, 0, 120, 120)
        repo.savePlan(main.id, listOf(kitchen, den))
        assertThat(repo.loadPlan(main.id).placed).containsExactly(kitchen, den)
        assertThat(repo.loadPlan(main.id).unplaced).hasSize(1)

        repo.savePlan(main.id, listOf(kitchen))
        assertThat(repo.loadPlan(main.id).placed).containsExactly(kitchen)
        assertThat(changes).contains("room/d1")
    }

    @Test fun doorsWindowsAndStairsRoundTripThroughWallRows() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val upper = repo.observeFloors(id).first().single { it.name == "Upper" }
        val hall = PlanRoom.rectangle("h1", "Hall", "HALL", 0, 0, 144, 120)
            .withOpening(PlanOpening("o1", 0, OpeningKind.DOOR, 20, 32, 80))
            .withOpening(PlanOpening("o2", 2, OpeningKind.WINDOW, 40, 36, 48))
            .withStair(PlanStair("s1", upper.id, listOf(Pt(10, 10), Pt(46, 10), Pt(46, 130), Pt(10, 130))))
        repo.savePlan(main.id, listOf(hall))
        val loaded = repo.loadPlan(main.id).placed.single()
        assertThat(loaded.openings).containsExactlyElementsIn(hall.openings)
        assertThat(loaded.stairs).containsExactlyElementsIn(hall.stairs)
        // cr:wall-identity: every edge has a wall row, not only the ones with an opening.
        assertThat(db.wallDao().listFor("h1").map { it.edgeIndex }).containsExactly(0, 1, 2, 3)
        assertThat(changes).containsAtLeast("opening/o1", "opening/o2", "stair/s1")

        changes.clear()
        repo.savePlan(main.id, listOf(hall))
        assertThat(changes).isEmpty()

        repo.savePlan(main.id, listOf(hall.withoutOpening("o1").withoutStair("s1")))
        val after = repo.loadPlan(main.id).placed.single()
        assertThat(after.openings.map { it.id }).containsExactly("o2")
        assertThat(after.stairs).isEmpty()
        assertThat(changes).containsAtLeast("opening/o1", "stair/s1")
    }

    // cr:wall-identity: walls are matched by endpoints, so a reshape keeps their ids, openings and wall photos.
    @AcceptanceCriterion("AC-C17")
    @Test fun reshapingARoomKeepsItsWallsAndTheirPhotos() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val kitchen = PlanRoom.rectangle("k1", "Kitchen", "KITCHEN", 0, 0, 144, 120)
            .withOpening(PlanOpening("o1", 2, OpeningKind.WINDOW, 40, 36, 48))
        repo.savePlan(main.id, listOf(kitchen))
        val before = db.wallDao().listFor("k1").associate { it.edgeIndex to it.id }
        db.wallPhotoDao().upsert(com.kurtz.homerecord.core.data.WallPhotoEntity(id = "wp1", wallId = before.getValue(2), originalPath = "/m/s.jpg", takenAt = 1, createdAt = 1, updatedAt = 1))

        // A corner on the north wall shifts the south wall from edge 2 to edge 3.
        val notched = kitchen.withCornerOnEdge(0, Pt(72, 0))
        repo.savePlan(main.id, listOf(notched))
        val after = db.wallDao().listFor("k1").associate { it.edgeIndex to it.id }
        assertThat(after).hasSize(5)
        assertThat(after[3]).isEqualTo(before[2])
        assertThat(after[2]).isEqualTo(before[1])
        assertThat(after[4]).isEqualTo(before[3])
        assertThat(db.wallPhotoDao().listFor(before.getValue(2)).map { it.id }).containsExactly("wp1")
        assertThat(checkNotNull(db.openingDao().get("o1")).wallId).isEqualTo(before[2])
        assertThat(repo.loadPlan(main.id).placed.single().openings.single().edgeIndex).isEqualTo(notched.openings.single().edgeIndex)

        // Dragging the east side out keeps every wall and updates its length.
        changes.clear()
        repo.savePlan(main.id, listOf(notched.resizedEdge(2, 168)))
        assertThat(db.wallDao().listFor("k1").associate { it.edgeIndex to it.id }).isEqualTo(after)
        assertThat(checkNotNull(db.wallDao().get(after.getValue(3))).lengthIn).isEqualTo(168)
        assertThat(changes.filter { it.startsWith("wall/") }).isNotEmpty()
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun aWallWhoseEdgeIsGoneIsDeletedButItsPhotosStayOnTheRoomAndItemsLeaveIt() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val den = PlanRoom("d1", "Den", "OFFICE", listOf(Pt(0, 0), Pt(144, 0), Pt(144, 60), Pt(96, 60), Pt(96, 120), Pt(0, 120)))
        repo.savePlan(main.id, listOf(den))
        val walls = db.wallDao().listFor("d1").map { it.id }.toSet()
        assertThat(walls).hasSize(6)
        walls.forEach { w ->
            db.wallPhotoDao().upsert(com.kurtz.homerecord.core.data.WallPhotoEntity(id = "p-$w", wallId = w, originalPath = "/m/$w.jpg", takenAt = 1, createdAt = 1, updatedAt = 1))
            db.itemDao().upsert(com.kurtz.homerecord.core.data.ItemEntity(id = "i-$w", homeId = id, roomId = "d1", category = "LIGHTING", subtype = "light", name = "Sconce", wallId = w, createdAt = 1, updatedAt = 1))
        }
        changes.clear()

        repo.savePlan(main.id, listOf(den.withoutCorner(3)))

        // The notch's two sides become one diagonal: four walls keep their ids, the diagonal is a new wall.
        val after = db.wallDao().listFor("d1").map { it.id }.toSet()
        val kept = after intersect walls
        val gone = walls - after
        assertThat(after).hasSize(5)
        assertThat(kept).hasSize(4)
        assertThat(gone).hasSize(2)
        // N001: the removed walls' photos stay on the room with no wall, never deleted.
        assertThat(db.wallPhotoDao().listRemovedForRoom("d1").map { it.id }).containsExactlyElementsIn(gone.map { "p-$it" })
        gone.forEach { lost ->
            assertThat(db.wallPhotoDao().listFor(lost)).isEmpty()
            val kept = checkNotNull(db.wallPhotoDao().get("p-$lost"))
            assertThat(kept.wallId).isNull()
            assertThat(kept.roomId).isEqualTo("d1")
            assertThat(kept.originalPath).isEqualTo("/m/$lost.jpg")
            assertThat(checkNotNull(db.itemDao().get("i-$lost")).wallId).isNull()
            assertThat(checkNotNull(db.itemDao().get("i-$lost")).roomId).isEqualTo("d1")
            assertThat(changes).containsAtLeast("wall/$lost", "wall_photo/p-$lost", "item/i-$lost")
        }
        kept.forEach { w ->
            assertThat(db.wallPhotoDao().listFor(w)).hasSize(1)
            assertThat(checkNotNull(db.itemDao().get("i-$w")).wallId).isEqualTo(w)
        }
    }

    // N001: removed walls' photos are kept, their files are not handed to the sweep, a later wall in the same place does
    // not reclaim them, and they go only with their room.
    @AcceptanceCriterion("AC-C17")
    @Test fun removedWallsPhotosAreKeptUntilTheirRoomGoesAndANewWallThereDoesNotReclaimThem() = runTest {
        val handed = mutableListOf<com.kurtz.homerecord.housemodel.walls.PlanPhotoChanges>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val homes = HomeRepository(db, context.resources::getString, now = { clock++ }, onPlanPhotosChanged = { handed += it })
        val id = homes.createHome(NewHome("Maple St", "", HouseType.COLONIAL, Foundation.BASEMENT, false))
        val main = homes.observeFloors(id).first().single { it.name == "Main" }
        val den = PlanRoom("d1", "Den", "OFFICE", listOf(Pt(0, 0), Pt(144, 0), Pt(144, 60), Pt(96, 60), Pt(96, 120), Pt(0, 120)))
        homes.savePlan(main.id, listOf(den))
        val notch = db.wallDao().listFor("d1").single { it.edgeIndex == 2 }.id
        val photos = com.kurtz.homerecord.housemodel.walls.WallPhotoRepository(db, now = { clock++ })
        val photoId = checkNotNull(
            photos.save(notch, com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN, "/m/notch.jpg", "/m/notch-wall.jpg",
                com.kurtz.homerecord.housemodel.walls.WallOutline.Initial),
        )
        assertThat(checkNotNull(db.wallPhotoDao().get(photoId)).roomId).isEqualTo("d1")

        homes.savePlan(main.id, listOf(den.withoutCorner(3)))
        assertThat(handed.flatMap { it.droppedFiles }).containsNoneOf("/m/notch.jpg", "/m/notch-wall.jpg")
        val removed = photos.observeRemovedWallPhotos("d1").first()
        assertThat(removed.map { it.id }).containsExactly(photoId)
        assertThat(removed.single().stage).isEqualTo(com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN)
        assertThat(removed.single().path).isEqualTo("/m/notch.jpg")

        // The notch drawn back: new walls run where the old one was; the photo stays with the removed walls.
        homes.savePlan(main.id, listOf(den))
        assertThat(checkNotNull(db.wallPhotoDao().get(photoId)).wallId).isNull()
        db.wallDao().listFor("d1").forEach { assertThat(db.wallPhotoDao().listFor(it.id)).isEmpty() }
        assertThat(db.wallPhotoDao().listRemovedForRoom("d1").map { it.id }).containsExactly(photoId)

        // Deleting the room takes them, and hands their files on.
        handed.clear()
        homes.savePlan(main.id, emptyList())
        assertThat(db.wallPhotoDao().get(photoId)).isNull()
        assertThat(handed.single().droppedFiles).containsAtLeast("/m/notch.jpg", "/m/notch-wall.jpg")
    }

    // R10: overlay coordinates after a wall is resized, split or removed in the editor.
    @AcceptanceCriterion("AC-C27")
    @Test fun wallOverlaysFollowTheirWallThroughAResizeASplitAndARemovedCorner() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val kitchen = PlanRoom.rectangle("k1", "Kitchen", "KITCHEN", 0, 0, 144, 120)
        repo.savePlan(main.id, listOf(kitchen))
        val north = db.wallDao().listFor("k1").single { it.edgeIndex == 0 }.id
        fun overlay(oid: String, vararg xy: Float) = com.kurtz.homerecord.core.data.OverlayEntity(
            id = oid, homeId = id, roomId = "k1", wallId = north, kind = "WATER_COLD",
            pointsJson = com.kurtz.homerecord.housemodel.overlays.OverlayPoints.encode(
                xy.toList().chunked(2).map { (x, y) -> com.kurtz.homerecord.housemodel.overlays.OverlayPoint(x, y) },
            ),
            createdAt = 1, updatedAt = 1,
        )
        db.overlayDao().upsert(overlay("west", 0.25f, 0.5f))
        db.overlayDao().upsert(overlay("east", 0.75f, 0.5f))
        suspend fun points(oid: String) = com.kurtz.homerecord.housemodel.overlays.OverlayPoints.decode(checkNotNull(db.overlayDao().get(oid)).pointsJson)

        // Dragging the east side out stretches the north wall; both overlays keep their wall-space points.
        val wider = kitchen.resizedEdge(1, 168)
        repo.savePlan(main.id, listOf(wider))
        assertThat(checkNotNull(db.overlayDao().get("west")).wallId).isEqualTo(north)
        assertThat(points("west").single().x).isEqualTo(0.25f)
        assertThat(points("east").single().x).isEqualTo(0.75f)

        // A corner at 84" splits the 168" north wall: each overlay goes to the part it sat on, at the same place.
        changes.clear()
        repo.savePlan(main.id, listOf(wider.withCornerOnEdge(0, Pt(84, 0))))
        val walls = db.wallDao().listFor("k1").associate { it.edgeIndex to it.id }
        val west = checkNotNull(db.overlayDao().get("west"))
        val east = checkNotNull(db.overlayDao().get("east"))
        assertThat(setOf(west.wallId, east.wallId)).containsExactly(walls[0], walls[1])
        assertThat(west.wallId).isEqualTo(walls[0])
        assertThat(points("west").single().x).isWithin(1e-4f).of(0.5f)
        assertThat(points("east").single().x).isWithin(1e-4f).of(0.5f)
        assertThat(changes).contains("overlay/east")

        // Removing that corner again merges the two parts; nothing is deleted and both sit where they were.
        repo.savePlan(main.id, listOf(wider))
        val merged = db.wallDao().listFor("k1").single { it.edgeIndex == 0 }.id
        assertThat(checkNotNull(db.overlayDao().get("west")).wallId).isEqualTo(merged)
        assertThat(checkNotNull(db.overlayDao().get("east")).wallId).isEqualTo(merged)
        assertThat(points("west").single().x).isWithin(1e-4f).of(0.25f)
        assertThat(points("east").single().x).isWithin(1e-4f).of(0.75f)
    }

    @AcceptanceCriterion("AC-C27")
    @Test fun anOverlayOnAWallThatIsGoneMovesOntoThePlanInsteadOfBeingDeleted() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val den = PlanRoom("d1", "Den", "OFFICE", listOf(Pt(0, 0), Pt(144, 0), Pt(144, 60), Pt(96, 60), Pt(96, 120), Pt(0, 120)))
        repo.savePlan(main.id, listOf(den))
        val notch = db.wallDao().listFor("d1").single { it.edgeIndex == 2 }.id
        db.overlayDao().upsert(
            com.kurtz.homerecord.core.data.OverlayEntity(
                id = "gas", homeId = id, roomId = "d1", wallId = notch, kind = "GAS",
                pointsJson = """[{"x":0.5,"y":0.5}]""", createdAt = 1, updatedAt = 1,
            ),
        )

        repo.savePlan(main.id, listOf(den.withoutCorner(3)))

        val gas = checkNotNull(db.overlayDao().get("gas"))
        assertThat(gas.wallId).isNull()
        assertThat(gas.roomId).isEqualTo("d1")
        val p = com.kurtz.homerecord.housemodel.overlays.OverlayPoints.decode(gas.pointsJson).single()
        assertThat(p.x).isEqualTo(120f)
        assertThat(p.y).isEqualTo(60f)
    }

    @Test fun savePlanOnlyRecordsRowsThatChanged() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        val kitchen = PlanRoom.rectangle("k1", "Kitchen", "KITCHEN", 0, 0, 144, 120)
        repo.savePlan(main.id, listOf(kitchen))
        changes.clear()
        repo.savePlan(main.id, listOf(kitchen))
        assertThat(changes).isEmpty()
    }

    @Test fun planCodecIgnoresDamagedJson() {
        assertThat(PlanCodec.decode("not json")).isEmpty()
        assertThat(PlanCodec.decode("[{\"x\":1,\"y\":2}]")).isEmpty()
        val pts = listOf(Pt(0, 0), Pt(10, 0), Pt(10, 10))
        assertThat(PlanCodec.decode(PlanCodec.encode(pts))).isEqualTo(pts)
    }

    // R10a: a split wall's photo is cut at the new corner, the new part gets a copy, and the textures are made again.
    @AcceptanceCriterion("AC-C19")
    @Test fun aSplitWallsPhotoIsCutAtTheNewCornerAndBothPartsGetTheirOwnTexture() = runTest {
        val recut = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val homes = HomeRepository(db, context.resources::getString, now = { clock++ }, onChange = { entity, id -> changes += "$entity/$id" }, onPlanPhotosChanged = { recut += it.recutIds })
        val id = homes.createHome(NewHome("Maple St", "", HouseType.COLONIAL, Foundation.BASEMENT, false))
        val main = homes.observeFloors(id).first().single { it.name == "Main" }
        val kitchen = PlanRoom.rectangle("k1", "Kitchen", "KITCHEN", 0, 0, 144, 120)
        homes.savePlan(main.id, listOf(kitchen))
        val north = db.wallDao().listFor("k1").single { it.edgeIndex == 0 }.id
        val outline = com.kurtz.homerecord.housemodel.walls.WallOutline(
            com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.1f, 0.2f), com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.9f, 0.2f),
            com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.9f, 0.8f), com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.1f, 0.8f),
        )
        db.wallPhotoDao().upsert(
            com.kurtz.homerecord.core.data.WallPhotoEntity(
                id = "wp1", wallId = north, originalPath = "/m/n.jpg", texturePath = "/m/n-wall.jpg", cornersJson = outline.encode(),
                textureR2Key = "t", takenAt = 1, createdAt = 1, updatedAt = 1,
            ),
        )

        homes.savePlan(main.id, listOf(kitchen.withCornerOnEdge(0, Pt(72, 0))))

        val walls = db.wallDao().listFor("k1").associate { it.edgeIndex to it.id }
        val left = db.wallPhotoDao().listFor(checkNotNull(walls[0])).single()
        val right = db.wallPhotoDao().listFor(checkNotNull(walls[1])).single()
        assertThat(setOf(left.id, right.id)).contains("wp1")
        assertThat(left.originalPath).isEqualTo("/m/n.jpg")
        assertThat(right.originalPath).isEqualTo("/m/n.jpg")
        val l = checkNotNull(com.kurtz.homerecord.housemodel.walls.WallOutline.decode(left.cornersJson))
        val r = checkNotNull(com.kurtz.homerecord.housemodel.walls.WallOutline.decode(right.cornersJson))
        assertThat(l.topLeft.x).isWithin(1e-4f).of(0.1f)
        assertThat(l.topRight.x).isWithin(1e-4f).of(0.5f)
        assertThat(r.topLeft.x).isWithin(1e-4f).of(0.5f)
        assertThat(r.topRight.x).isWithin(1e-4f).of(0.9f)
        assertThat(listOf(left.texturePath, right.texturePath, left.textureR2Key, right.textureR2Key)).containsExactly(null, null, null, null)
        assertThat(recut).containsExactly(left.id, right.id)
        assertThat(changes).containsAtLeast("wall_photo/${left.id}", "wall_photo/${right.id}")

        val maker = com.kurtz.homerecord.housemodel.ui.FakeTextureMaker()
        val uploaded = mutableListOf<String>()
        com.kurtz.homerecord.housemodel.walls.WallPhotoRepository(db, now = { clock++ }, upload = { uploaded += it.id }).remakeTextures(recut, maker)
        val textures = listOf(left.id, right.id).map { checkNotNull(checkNotNull(db.wallPhotoDao().get(it)).texturePath) }
        assertThat(textures.toSet()).hasSize(2)
        assertThat(maker.made.map { it.third }).containsExactly(72 to 96, 72 to 96)
        assertThat(uploaded).containsExactly(left.id, right.id)

        // Removing the corner again: the merged wall keeps its own half, stretched back over the whole wall.
        recut.clear()
        homes.savePlan(main.id, listOf(kitchen))
        val merged = db.wallDao().listFor("k1").single { it.edgeIndex == 0 }.id
        val back = db.wallPhotoDao().listFor(merged).single()
        val o = checkNotNull(com.kurtz.homerecord.housemodel.walls.WallOutline.decode(back.cornersJson))
        assertThat(o.topLeft.x).isWithin(1e-4f).of(0.1f)
        assertThat(o.topRight.x).isWithin(1e-4f).of(0.9f)
        assertThat(recut).containsExactly(back.id)
    }

    // R10d: a plan save hands on the files its rows stopped pointing at; only the ones no live row uses are deleted.
    @AcceptanceCriterion("AC-C19")
    @Test fun aRecutPhotosOldStraightenedCopyIsDeletedAfterTheRemake() = runTest {
        val handed = mutableListOf<com.kurtz.homerecord.housemodel.walls.PlanPhotoChanges>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val homes = HomeRepository(db, context.resources::getString, now = { clock++ }, onPlanPhotosChanged = { handed += it })
        val deleted = mutableListOf<String>()
        val photos = com.kurtz.homerecord.housemodel.walls.WallPhotoRepository(
            db, now = { clock++ }, sweep = com.kurtz.homerecord.housemodel.walls.ReplacedFiles(db) { deleted += it }::sweep,
        )
        val id = homes.createHome(NewHome("Maple St", "", HouseType.COLONIAL, Foundation.BASEMENT, false))
        val main = homes.observeFloors(id).first().single { it.name == "Main" }
        val kitchen = PlanRoom.rectangle("k1", "Kitchen", "KITCHEN", 0, 0, 144, 120)
        homes.savePlan(main.id, listOf(kitchen))
        assertThat(handed).isEmpty()
        val north = db.wallDao().listFor("k1").single { it.edgeIndex == 0 }.id
        db.wallPhotoDao().upsert(
            com.kurtz.homerecord.core.data.WallPhotoEntity(
                id = "wp1", wallId = north, originalPath = "/m/n.jpg", texturePath = "/m/n-wall.jpg",
                cornersJson = com.kurtz.homerecord.housemodel.walls.WallOutline.Initial.encode(), takenAt = 1, createdAt = 1, updatedAt = 1,
            ),
        )

        // Split: the kept part is recut (its old copy dropped), the new part shares the photo as taken.
        homes.savePlan(main.id, listOf(kitchen.withCornerOnEdge(0, Pt(72, 0))))
        val split = handed.single()
        assertThat(split.droppedFiles).containsExactly("/m/n-wall.jpg")
        assertThat(split.recutIds).hasSize(2)
        val maker = com.kurtz.homerecord.housemodel.ui.FakeTextureMaker()
        photos.afterPlanChange(split, maker)
        assertThat(deleted).containsExactly("/m/n-wall.jpg")
        val newCopies = split.recutIds.map { checkNotNull(checkNotNull(db.wallPhotoDao().get(it)).texturePath) }

        // Merge: the removed part's photo row goes; the shared photo stays, both parts' new copies go.
        handed.clear()
        deleted.clear()
        homes.savePlan(main.id, listOf(kitchen))
        val merge = handed.single()
        assertThat(merge.droppedFiles).containsAtLeastElementsIn(newCopies)
        photos.afterPlanChange(merge, maker)
        // The kept row's copy is made again under its own name (photo + row id), so that file is live and stays;
        // the removed part's copy and nothing else is deleted.
        val kept = db.wallPhotoDao().listFor(db.wallDao().listFor("k1").single { it.edgeIndex == 0 }.id).single()
        val remade = checkNotNull(kept.texturePath)
        assertThat(remade).isIn(newCopies)
        assertThat(deleted).containsExactlyElementsIn(newCopies - remade)
        assertThat(deleted).doesNotContain("/m/n.jpg")
    }

    @AcceptanceCriterion("AC-C19")
    @Test fun aFailedRemakeStillDeletesTheDroppedCopy() = runTest {
        val deleted = mutableListOf<String>()
        val photos = com.kurtz.homerecord.housemodel.walls.WallPhotoRepository(
            db, now = { clock++ }, sweep = com.kurtz.homerecord.housemodel.walls.ReplacedFiles(db) { deleted += it }::sweep,
        )
        val changes = com.kurtz.homerecord.housemodel.walls.PlanPhotoChanges(listOf("missing-row"), listOf("/m/old-wall.jpg"))

        photos.afterPlanChange(changes, com.kurtz.homerecord.housemodel.ui.FakeTextureMaker(fails = true))

        assertThat(deleted).containsExactly("/m/old-wall.jpg")
    }

    @AcceptanceCriterion("AC-C17")
    @Test fun removingARoomOrFloorHandsOnItsWallAndOverlayPhotoFiles() = runTest {
        val handed = mutableListOf<com.kurtz.homerecord.housemodel.walls.PlanPhotoChanges>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val homes = HomeRepository(db, context.resources::getString, now = { clock++ }, onPlanPhotosChanged = { handed += it })
        val overlays = com.kurtz.homerecord.housemodel.overlays.OverlayRepository(db, now = { clock++ })
        val id = homes.createHome(NewHome("Maple St", "", HouseType.COLONIAL, Foundation.BASEMENT, false))
        val main = homes.observeFloors(id).first().single { it.name == "Main" }
        val kitchen = PlanRoom.rectangle("k1", "Kitchen", "KITCHEN", 0, 0, 144, 120)
        val pantry = PlanRoom.rectangle("p1", "Pantry", "OTHER", 144, 0, 48, 120)
        homes.savePlan(main.id, listOf(kitchen, pantry))
        val pantryWall = db.wallDao().listFor("p1").first().id
        db.wallPhotoDao().upsert(
            com.kurtz.homerecord.core.data.WallPhotoEntity(
                id = "wp-p", wallId = pantryWall, originalPath = "/m/p.jpg", texturePath = "/m/p-wall.jpg",
                cornersJson = com.kurtz.homerecord.housemodel.walls.WallOutline.Initial.encode(), takenAt = 1, createdAt = 1, updatedAt = 1,
            ),
        )
        overlays.addOnWall(
            id, pantryWall,
            com.kurtz.homerecord.housemodel.overlays.OverlayDraft(
                com.kurtz.homerecord.housemodel.overlays.OverlayKind.GAS, listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.5f, 0.5f)),
                com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN, photoPath = "/m/gas.jpg",
            ),
        )

        homes.savePlan(main.id, listOf(kitchen))
        assertThat(handed.single().droppedFiles).containsExactly("/m/p.jpg", "/m/p-wall.jpg", "/m/gas.jpg")
        assertThat(handed.single().recutIds).isEmpty()

        handed.clear()
        val kitchenWall = db.wallDao().listFor("k1").first().id
        db.wallPhotoDao().upsert(
            com.kurtz.homerecord.core.data.WallPhotoEntity(
                id = "wp-k", wallId = kitchenWall, originalPath = "/m/k.jpg", texturePath = null,
                cornersJson = com.kurtz.homerecord.housemodel.walls.WallOutline.Initial.encode(), takenAt = 1, createdAt = 1, updatedAt = 1,
            ),
        )
        homes.deleteFloor(main.id)
        assertThat(handed.single().droppedFiles).containsExactly("/m/k.jpg")
    }
}
