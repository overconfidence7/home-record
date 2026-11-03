package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.scene.MeshPartKey
import com.kurtz.homerecord.housemodel.scene.DefaultMeshMaker
import com.kurtz.homerecord.housemodel.scene.MeshMaker
import com.kurtz.homerecord.housemodel.walls.PhotoPoint
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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

// AC-C20: HM-3D builds the chosen floor's walls at its ceiling height and paints the chosen stage's wall photos.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class House3DViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private lateinit var wallPhotos: WallPhotoRepository
    private val created = mutableListOf<House3DViewModel>()
    private val outline = WallOutline(PhotoPoint(0.1f, 0.1f), PhotoPoint(0.9f, 0.1f), PhotoPoint(0.9f, 0.9f), PhotoPoint(0.1f, 0.9f))

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeRepository(db, context.resources::getString)
        wallPhotos = WallPhotoRepository(db)
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(
        homeId: String,
        floorId: String? = null,
        saved: SavedStateHandle = SavedStateHandle(),
        buildMesh: MeshMaker = DefaultMeshMaker,
        defaultStage: WallStage = WallStage.HouseManualDefault,
        roomId: String? = null,
    ) = House3DViewModel(repo, wallPhotos, homeId, "Maple St", floorId, saved, defaultStage, buildMesh, initialRoomId = roomId).also { created += it }

    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val living = PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 180, 144)

    private suspend fun home(): Pair<String, Map<String, String>> {
        val homeId = repo.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floors = repo.observeFloors(homeId).first { it.isNotEmpty() }.associate { it.name to it.id }
        repo.savePlan(floors.getValue("Main"), listOf(kitchen, living))
        return homeId to floors
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun buildsTheFloorWithRoomsAtItsCeilingHeight() = test {
        val (homeId, floors) = home()
        val model = vm(homeId)
        val state = withTimeout(10_000) { model.state.first { it.scene != null } }
        assertThat(state.loading).isFalse()
        assertThat(state.floorId).isEqualTo(floors.getValue("Main"))
        assertThat(state.stage).isEqualTo(WallStage.FINISHED)
        val scene = checkNotNull(state.scene)
        assertThat(scene.roomCount).isEqualTo(2)
        assertThat(scene.wallCount).isEqualTo(8)
        val ceilingM = state.floor!!.ceilingHeightIn * 0.0254f
        assertThat(scene.mesh.bounds.maxY).isWithin(0.01f).of(ceilingM)
        assertThat(state.photographedWalls).isEqualTo(0)
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun aNewWallPhotoShowsAtItsStageAndTheStageSurvivesProcessDeath() = test {
        val (homeId, floors) = home()
        val saved = SavedStateHandle()
        val model = vm(homeId, saved = saved)
        withTimeout(10_000) { model.state.first { it.scene != null } }
        val north = db.wallDao().listFor("k").single { it.edgeIndex == 0 }
        wallPhotos.save(north.id, WallStage.FINISHED, "/m/north.jpg", "/m/north-wall.jpg", outline)
        val painted = withTimeout(10_000) { model.state.first { it.photographedWalls == 1 } }
        assertThat(painted.scene!!.picturesAt(WallStage.FINISHED)).containsExactly(MeshPartKey.WallFace("k", 0), "/m/north-wall.jpg")

        model.onEvent(House3DEvent.SelectStage(WallStage.ROUGH_IN))
        assertThat(model.state.value.stage).isEqualTo(WallStage.ROUGH_IN)
        assertThat(model.state.value.photographedWalls).isEqualTo(0)

        val restored = vm(homeId, saved = saved).state.let { flow -> withTimeout(10_000) { flow.first { it.scene != null } } }
        assertThat(restored.stage).isEqualTo(WallStage.ROUGH_IN)
        assertThat(restored.floorId).isEqualTo(floors.getValue("Main"))
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun fieldLogOpensOnRoughInWithTheCrewsPhotoPaintedOn() = test {
        val (homeId, _) = home()
        val north = db.wallDao().listFor("k").single { it.edgeIndex == 0 }
        wallPhotos.save(north.id, WallStage.ROUGH_IN, "/m/north.jpg", "/m/north-wall.jpg", outline)
        val saved = SavedStateHandle()
        val model = vm(homeId, saved = saved, defaultStage = WallStage.BehindTheWall)
        val state = withTimeout(10_000) { model.state.first { it.photographedWalls == 1 } }
        assertThat(state.stage).isEqualTo(WallStage.ROUGH_IN)
        assertThat(state.scene!!.picturesAt(WallStage.ROUGH_IN)).containsExactly(MeshPartKey.WallFace("k", 0), "/m/north-wall.jpg")

        // A stage the crew picked wins over the default after process death
        model.onEvent(House3DEvent.SelectStage(WallStage.FINISHED))
        val restored = vm(homeId, saved = saved, defaultStage = WallStage.BehindTheWall).state.let { flow -> withTimeout(10_000) { flow.first { it.scene != null } } }
        assertThat(restored.stage).isEqualTo(WallStage.FINISHED)
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun opensOnTheGivenFloorAndSwitchesFloors() = test {
        val (homeId, floors) = home()
        val upper = floors.getValue("Upper")
        val model = vm(homeId, floorId = upper)
        val empty = withTimeout(10_000) { model.state.first { it.scene != null } }
        assertThat(empty.floorId).isEqualTo(upper)
        assertThat(empty.scene!!.isEmpty).isTrue()

        model.onEvent(House3DEvent.SelectFloor(floors.getValue("Main")))
        val main = withTimeout(10_000) { model.state.first { it.scene?.roomCount == 2 } }
        assertThat(main.floor?.name).isEqualTo("Main")
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun resetViewBumpsTheCounterAndAFailedBuildOffersRetry() = test {
        val (homeId, _) = home()
        var fail = true
        val flaky: MeshMaker = { rooms, height -> if (fail) error("bad plan") else DefaultMeshMaker(rooms, height) }
        val model = vm(homeId, buildMesh = flaky)
        val failed = withTimeout(10_000) { model.state.first { it.failed } }
        assertThat(failed.loading).isFalse()
        assertThat(failed.scene).isNull()

        fail = false
        model.onEvent(House3DEvent.Retry)
        val ready = withTimeout(10_000) { model.state.first { it.scene != null } }
        assertThat(ready.failed).isFalse()

        model.onEvent(House3DEvent.ResetView)
        model.onEvent(House3DEvent.ResetView)
        assertThat(model.state.value.resetCount).isEqualTo(2)
    }

    @Test
    @AcceptanceCriterion("AC-C21")
    fun theFloorAboveShowsOverAFadedMainAndAllFloorsSurvivesProcessDeath() = test {
        val (homeId, floors) = home()
        val upper = floors.getValue("Upper")
        repo.savePlan(upper, listOf(PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 0, 168, 144)))
        val saved = SavedStateHandle()
        val model = vm(homeId, floorId = upper, saved = saved)
        val one = withTimeout(10_000) { model.state.first { it.scene?.roomCount == 1 && it.scene?.ghostRooms?.isNotEmpty() == true } }
        // Main is below Upper; the basement has no rooms, so it adds nothing.
        assertThat(one.scene!!.ghostRooms).containsExactly("k", "l")
        assertThat(one.scene!!.wallCount).isEqualTo(4)

        model.onEvent(House3DEvent.ShowAllFloors)
        assertThat(model.state.value.allFloors).isTrue()
        val all = withTimeout(10_000) { model.state.first { it.scene?.roomCount == 3 } }
        assertThat(all.scene!!.ghostRooms).isEmpty()
        assertThat(all.scene!!.solidFloors).isEqualTo(3)

        val restored = vm(homeId, floorId = upper, saved = saved).state.let { flow -> withTimeout(10_000) { flow.first { it.scene?.roomCount == 3 } } }
        assertThat(restored.allFloors).isTrue()

        // Picking a floor leaves All floors.
        model.onEvent(House3DEvent.SelectFloor(floors.getValue("Main")))
        val main = withTimeout(10_000) { model.state.first { !it.allFloors && it.scene?.roomCount == 2 } }
        assertThat(main.scene!!.ghostRooms).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun aTappedRoomPutsTheCameraInsideUntilBackToFloorAndSurvivesProcessDeath() = test {
        val (homeId, floors) = home()
        val saved = SavedStateHandle()
        val model = vm(homeId, saved = saved)
        withTimeout(10_000) { model.state.first { it.scene != null } }
        // A room the scene does not hold (a ghost floor's, or gone) is ignored.
        model.onEvent(House3DEvent.ViewRoom("nope"))
        assertThat(model.state.value.roomId).isNull()

        model.onEvent(House3DEvent.ViewRoom("k"))
        val inside = model.state.value
        assertThat(inside.room?.name).isEqualTo("Kitchen")
        assertThat(inside.openedOnRoom).isFalse()

        val restored = vm(homeId, saved = saved).state.let { flow -> withTimeout(10_000) { flow.first { it.room != null } } }
        assertThat(restored.room?.id).isEqualTo("k")

        model.onEvent(House3DEvent.BackToFloor)
        assertThat(model.state.value.roomId).isNull()
        assertThat(model.state.value.room).isNull()

        // Changing floors leaves the room too.
        model.onEvent(House3DEvent.ViewRoom("l"))
        model.onEvent(House3DEvent.SelectFloor(floors.getValue("Upper")))
        assertThat(model.state.value.roomId).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun viewIn3DFromTheRoomPageOpensInsideTheRoom() = test {
        val (homeId, floors) = home()
        val saved = SavedStateHandle()
        val model = vm(homeId, floorId = floors.getValue("Main"), saved = saved, roomId = "l")
        val state = withTimeout(10_000) { model.state.first { it.room != null } }
        assertThat(state.room?.name).isEqualTo("Living room")
        assertThat(state.openedOnRoom).isTrue()

        // Back to floor after process death stays on the floor.
        model.onEvent(House3DEvent.BackToFloor)
        val restored = vm(homeId, floorId = floors.getValue("Main"), saved = saved, roomId = "l").state.let { flow ->
            withTimeout(10_000) { flow.first { it.scene != null } }
        }
        assertThat(restored.roomId).isNull()
        assertThat(restored.openedOnRoom).isFalse()
    }

    private class MemoryLayers : com.kurtz.homerecord.housemodel.blueprint.LayerStore {
        val stored = mutableMapOf<String, com.kurtz.homerecord.housemodel.blueprint.LayerSet>()
        override fun read(homeId: String) = stored[homeId] ?: com.kurtz.homerecord.housemodel.blueprint.LayerSet()
        override fun write(homeId: String, layers: com.kurtz.homerecord.housemodel.blueprint.LayerSet) { stored[homeId] = layers }
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun overlaysAndPlacedItemsBecomeMarkersShownByTheSharedLayerToggles() = test {
        val (homeId, _) = home()
        val overlays = com.kurtz.homerecord.housemodel.overlays.OverlayRepository(db)
        val north = db.wallDao().listFor("k").single { it.edgeIndex == 0 }
        val pipe = overlays.addOnWall(
            homeId, north.id,
            com.kurtz.homerecord.housemodel.overlays.OverlayDraft(
                com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD,
                listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.2f, 0.5f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.8f, 0.5f)),
                WallStage.ROUGH_IN, label = "Cold to sink",
            ),
        ) as com.kurtz.homerecord.housemodel.overlays.OverlaySave.Saved
        val stud = overlays.addOnWall(
            homeId, north.id,
            com.kurtz.homerecord.housemodel.overlays.OverlayDraft(
                com.kurtz.homerecord.housemodel.overlays.OverlayKind.STUD,
                listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.5f, 0f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.5f, 1f)),
                WallStage.ROUGH_IN,
            ),
        ) as com.kurtz.homerecord.housemodel.overlays.OverlaySave.Saved
        db.itemDao().upsert(
            com.kurtz.homerecord.core.data.ItemEntity(
                id = "fridge", homeId = homeId, roomId = "k", category = "appliances", subtype = "refrigerator", name = "Fridge",
                planX = 40, planY = 30, createdAt = 0L, updatedAt = 0L,
            ),
        )
        // An item with no spot on the plan has no marker.
        db.itemDao().upsert(
            com.kurtz.homerecord.core.data.ItemEntity(id = "toaster", homeId = homeId, roomId = "k", category = "appliances", subtype = "other", name = "Toaster", createdAt = 0L, updatedAt = 0L),
        )
        val layers = MemoryLayers()
        val model = House3DViewModel(
            repo, wallPhotos, homeId, "Maple St", null, SavedStateHandle(),
            overlays = overlays.observeForHome(homeId), itemSpots = overlays.observeItemSpots(homeId), layerStore = layers,
        ).also { created += it }
        val state = withTimeout(10_000) { model.state.first { it.markers.size == 3 } }
        assertThat(state.markers.map { it.target }).containsExactly(
            com.kurtz.homerecord.housemodel.scene.MarkerTarget.OverlayMark(pipe.id),
            com.kurtz.homerecord.housemodel.scene.MarkerTarget.OverlayMark(stud.id),
            com.kurtz.homerecord.housemodel.scene.MarkerTarget.ItemMark("fridge"),
        )
        assertThat(state.markers.single { it.target == com.kurtz.homerecord.housemodel.scene.MarkerTarget.ItemMark("fridge") }.label).isEqualTo("Fridge")
        // Layers off: only the stud (on no layer) shows, as on the blueprint.
        assertThat(state.visibleMarkers.map { it.target }).containsExactly(com.kurtz.homerecord.housemodel.scene.MarkerTarget.OverlayMark(stud.id))

        model.onEvent(House3DEvent.ToggleLayer(com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.PLUMBING))
        model.onEvent(House3DEvent.ToggleLayer(com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.ITEMS))
        assertThat(model.state.value.visibleMarkers).hasSize(3)
        // The toggles are the home's, shared with the House tab's blueprint.
        assertThat(layers.read(homeId).on).containsExactly(
            com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.PLUMBING,
            com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.ITEMS,
        )
        assertThat(model.overlay(pipe.id)?.label).isEqualTo("Cold to sink")

        // A deleted overlay leaves the scene.
        overlays.delete(pipe.id)
        val after = withTimeout(10_000) { model.state.first { it.markers.size == 2 } }
        assertThat(after.markers.map { it.target }).doesNotContain(com.kurtz.homerecord.housemodel.scene.MarkerTarget.OverlayMark(pipe.id))
        assertThat(model.overlay(pipe.id)).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun theLayersOpenAsTheHouseTabLeftThem() = test {
        val (homeId, _) = home()
        val layers = MemoryLayers()
        layers.write(homeId, com.kurtz.homerecord.housemodel.blueprint.LayerSet(setOf(com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.ELECTRICAL)))
        val model = House3DViewModel(repo, wallPhotos, homeId, "Maple St", null, SavedStateHandle(), layerStore = layers).also { created += it }
        assertThat(model.state.value.layers.on).containsExactly(com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.ELECTRICAL)
    }
}
