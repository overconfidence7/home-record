package com.kurtz.homerecord.housemodel

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

    @Test fun createHomeSeedsFloorsFromTemplateAndTrimsName() = runTest {
        val id = colonial()
        val home = repo.observeHome().first()!!
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

    @Test fun skipForNowCreatesOneEmptyMainFloor() = runTest {
        val id = repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, attachedGarage = true, seedFromTemplate = false))
        val floors = repo.observeFloors(id).first()
        assertThat(floors.map { it.name }).containsExactly("Main")
        assertThat(floors.single().roomCount).isEqualTo(0)
    }

    @Test fun addFloorGoesOnTopAndUpdateWorks() = runTest {
        val id = colonial()
        val attic = repo.addFloor(id, " Attic ")
        assertThat(repo.observeFloors(id).first().map { it.name }.last()).isEqualTo("Attic")
        repo.updateFloor(attic, " Loft ", 84)
        assertThat(db.floorDao().get(attic)?.name).isEqualTo("Loft")
        assertThat(db.floorDao().get(attic)?.ceilingHeightIn).isEqualTo(84)
        assertThat(db.floorDao().get(attic)!!.elevationIn).isGreaterThan(db.floorDao().listFor(id).first { it.name == "Upper" }.elevationIn)
    }

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

    @Test fun deleteFloorSoftDeletesItsRooms() = runTest {
        val id = colonial()
        val main = repo.observeFloors(id).first().single { it.name == "Main" }
        assertThat(repo.roomCount(main.id)).isEqualTo(1)
        repo.deleteFloor(main.id)
        assertThat(repo.observeFloors(id).first().map { it.name }).containsExactly("Basement", "Upper").inOrder()
        assertThat(db.roomDao().listFor(main.id)).isEmpty()
        assertThat(changes).contains("floor/${main.id}")
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
        assertThat(db.wallDao().listFor("h1")).hasSize(2)
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
