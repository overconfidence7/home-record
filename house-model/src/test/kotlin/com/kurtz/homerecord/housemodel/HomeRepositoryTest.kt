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
        repo = HomeRepository(db, now = { clock++ }, onChange = { entity, id -> changes += "$entity/$id" })
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
    @Test fun aWallWhoseEdgeIsGoneIsDeletedWithItsPhotosAndItemsLeaveIt() = runTest {
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
        gone.forEach { lost ->
            assertThat(db.wallPhotoDao().listFor(lost)).isEmpty()
            assertThat(checkNotNull(db.itemDao().get("i-$lost")).wallId).isNull()
            assertThat(checkNotNull(db.itemDao().get("i-$lost")).roomId).isEqualTo("d1")
            assertThat(changes).containsAtLeast("wall/$lost", "wall_photo/p-$lost", "item/i-$lost")
        }
        kept.forEach { w ->
            assertThat(db.wallPhotoDao().listFor(w)).hasSize(1)
            assertThat(checkNotNull(db.itemDao().get("i-$w")).wallId).isEqualTo(w)
        }
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
}
