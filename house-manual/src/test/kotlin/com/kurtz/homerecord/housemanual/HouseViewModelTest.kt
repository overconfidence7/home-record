package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.ui.HouseDialog
import com.kurtz.homerecord.housemodel.ui.HouseEvent
import com.kurtz.homerecord.housemodel.ui.HouseViewModel
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class MemoryLayerStore : LayerStore {
    val map = mutableMapOf<String, LayerSet>()
    override fun read(homeId: String) = map[homeId] ?: LayerSet()
    override fun write(homeId: String, layers: LayerSet) { map[homeId] = layers }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HouseViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private val store = MemoryLayerStore()

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HomeRepository(db, ApplicationProvider.getApplicationContext<Context>().resources::getString)
    }

    @After fun tearDown() {
        // A write still in flight when the database closes would throw into the next test class.
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private val created = mutableListOf<HouseViewModel>()

    private fun house(
        repo: HomeRepository, homeId: String, store: LayerStore, handle: SavedStateHandle, name: String, type: String,
        exporter: com.kurtz.homerecord.housemodel.ui.BlueprintExporter = com.kurtz.homerecord.housemodel.ui.BlueprintExporter { _, _ -> null },
    ) = HouseViewModel(repo, homeId, store, handle, name, type, exporter = exporter).also { created += it }

    private suspend fun setUpHome(): Pair<String, String> {
        val homeId = repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floorId = repo.observeFloors(homeId).first { it.isNotEmpty() }.first().id
        repo.savePlan(floorId, listOf(PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 144, 120), PlanRoom.rectangle("l", "Living", "LIVING_ROOM", 144, 0, 180, 120)))
        return homeId to floorId
    }

    private suspend fun HouseViewModel.untilRooms(count: Int) = withTimeout(10_000) { state.first { it.rooms.size == count } }

    @Test fun showsTheFloorsRooms() = test {
        val (homeId, _) = setUpHome()
        val vm = house(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        val state = vm.untilRooms(2)
        assertThat(state.rooms.map { it.name }).containsExactly("Kitchen", "Living")
    }

    // AC-C16
    @Test fun exportPdfPassesEveryFloorToTheExporterAndSurfacesTheUri() = test {
        val (homeId, _) = setUpHome()
        var seen: List<com.kurtz.homerecord.housemodel.pdf.PdfFloor> = emptyList()
        val vm = house(
            repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial",
            exporter = com.kurtz.homerecord.housemodel.ui.BlueprintExporter { _, floors -> seen = floors; "content://x/blueprint.pdf" },
        )
        vm.untilRooms(2)
        vm.onEvent(HouseEvent.ExportPdf)
        val state = withTimeout(10_000) { vm.state.first { it.shareUri != null } }
        assertThat(state.shareUri).isEqualTo("content://x/blueprint.pdf")
        assertThat(seen.size).isEqualTo(repo.observeFloors(homeId).first().size)
        assertThat(seen.flatMap { it.rooms }.map { it.name }).containsExactly("Kitchen", "Living")
        vm.onEvent(HouseEvent.ExportHandled)
        assertThat(vm.state.value.shareUri).isNull()
    }

    @Test fun layerTogglesPersistPerHome() = test {
        val (homeId, _) = setUpHome()
        val vm = house(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        vm.onEvent(HouseEvent.ToggleLayer(BlueprintLayer.PLUMBING))
        assertThat(store.read(homeId)).isEqualTo(LayerSet(setOf(BlueprintLayer.PLUMBING)))
        val again = house(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        assertThat(again.state.value.layers).isEqualTo(LayerSet(setOf(BlueprintLayer.PLUMBING)))
    }

    @Test fun renameFromTheLongPressMenuSavesTheRoom() = test {
        val (homeId, floorId) = setUpHome()
        val vm = house(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        val room = vm.untilRooms(2).rooms.first { it.id == "k" }
        vm.onEvent(HouseEvent.LongPressRoom(room))
        assertThat(vm.state.value.dialog).isEqualTo(HouseDialog.Menu(room))
        vm.onEvent(HouseEvent.StartRename(room))
        vm.onEvent(HouseEvent.Rename("k", "  Eat-in kitchen "))
        withTimeout(10_000) { vm.state.first { s -> s.rooms.any { it.name == "Eat-in kitchen" } } }
        assertThat(vm.state.value.dialog).isNull()
        assertThat(repo.loadPlan(floorId).placed.map { it.name }).contains("Eat-in kitchen")
    }

    @Test fun deleteRemovesOnlyThatRoom() = test {
        val (homeId, floorId) = setUpHome()
        val vm = house(repo, homeId, store, SavedStateHandle(), "Maple St", "Colonial")
        val room = vm.untilRooms(2).rooms.first { it.id == "l" }
        vm.onEvent(HouseEvent.StartDelete(room))
        vm.onEvent(HouseEvent.ConfirmDelete("l"))
        vm.untilRooms(1)
        assertThat(repo.loadPlan(floorId).placed.map { it.id }).containsExactly("k")
    }

    @Test @com.kurtz.homerecord.core.testing.AcceptanceCriterion("AC-C25")
    fun theBlueprintGetsTheFloorsPlanOverlaysOnly() = test {
        val (homeId, floorId) = setUpHome()
        val overlays = com.kurtz.homerecord.housemodel.overlays.OverlayRepository(db)
        val draft = { kind: com.kurtz.homerecord.housemodel.overlays.OverlayKind ->
            com.kurtz.homerecord.housemodel.overlays.OverlayDraft(
                kind, listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(20f, 20f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(200f, 20f)),
                com.kurtz.homerecord.housemodel.walls.WallStage.FINISHED,
            )
        }
        overlays.addOnPlan(homeId, "k", draft(com.kurtz.homerecord.housemodel.overlays.OverlayKind.GAS))
        overlays.addOnPlan(homeId, "upstairs-room", draft(com.kurtz.homerecord.housemodel.overlays.OverlayKind.DUCT))
        val wall = com.kurtz.homerecord.housemodel.walls.WallPhotoRepository(db).observeRoomWalls("k").first { it.isNotEmpty() }.first().id
        overlays.addOnWall(homeId, wall, draft(com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD))
        val vm = HouseViewModel(repo, homeId, store, SavedStateHandle(mapOf("floor" to floorId)), "Maple St", "Colonial", homeOverlays = overlays.observeForHome(homeId))
            .also { created += it }
        val state = withTimeout(10_000) { vm.state.first { it.overlays.isNotEmpty() } }
        assertThat(state.overlays.map { it.kind }).containsExactly(com.kurtz.homerecord.housemodel.overlays.OverlayKind.GAS)
    }

    @Test @com.kurtz.homerecord.core.testing.AcceptanceCriterion("AC-C15")
    fun theBlueprintLaysWallOverlaysAlongTheirWallAndPlacesItems() = test {
        val (homeId, floorId) = setUpHome()
        val overlays = com.kurtz.homerecord.housemodel.overlays.OverlayRepository(db)
        val wall = com.kurtz.homerecord.housemodel.walls.WallPhotoRepository(db).observeRoomWalls("k").first { it.isNotEmpty() }.first().id
        overlays.addOnWall(
            homeId, wall,
            com.kurtz.homerecord.housemodel.overlays.OverlayDraft(
                com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD,
                listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.25f, 0.7f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.75f, 0.7f)),
                com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN,
            ),
        )
        db.itemDao().upsert(
            com.kurtz.homerecord.core.data.ItemEntity(
                id = "i1", homeId = homeId, roomId = "k", category = "APPLIANCES", subtype = "dishwasher", name = "Dishwasher",
                planX = 40, planY = 30, createdAt = 1L, updatedAt = 1L,
            ),
        )
        val vm = HouseViewModel(
            repo, homeId, store, SavedStateHandle(mapOf("floor" to floorId)), "Maple St", "Colonial",
            homeOverlays = overlays.observeForHome(homeId),
            wallKeys = overlays::observeWallKeys,
            itemSpots = overlays.observeItemSpots(homeId),
        ).also { created += it }
        val state = withTimeout(10_000) { vm.state.first { it.marks.size == 2 } }
        // The wall run is no plan overlay; it lies on one of the kitchen's walls, 72" long (half of a 144" wall or of a 120" wall).
        assertThat(state.overlays).isEmpty()
        val run = state.marks.first { it.overlayKind == com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD }
        assertThat(run.layer).isEqualTo(BlueprintLayer.PLUMBING)
        assertThat(run.points).hasSize(2)
        val (a, b) = run.points
        assertThat(kotlin.math.hypot(b.x - a.x, b.y - a.y)).isAnyOf(72f, 60f)
        val item = state.marks.first { it.isItem }
        assertThat(item.points.single()).isEqualTo(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(40f, 30f))
        assertThat(item.iconName).isEqualTo("Kitchen")
        // T055a: a tap on the run's mark finds the overlay it draws, to open its wall with it picked.
        val target = run.target as com.kurtz.homerecord.housemodel.scene.MarkerTarget.OverlayMark
        assertThat(vm.overlay(target.overlayId)?.wallId).isEqualTo(wall)
        assertThat(vm.overlay("gone")).isNull()
    }
}
