package com.kurtz.homerecord.housemodel.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.scene.ItemSpot
import com.kurtz.homerecord.housemodel.scene.SceneMarkers
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.defaultFloor
import com.kurtz.homerecord.housemodel.scene.DefaultMeshMaker
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.scene.FloorStack
import com.kurtz.homerecord.housemodel.scene.MeshMaker
import com.kurtz.homerecord.housemodel.scene.SceneLayer
import com.kurtz.homerecord.housemodel.scene.StackedFloor
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HM-3D (AC-C20, AC-C21): follows the chosen floor's plan, its ceiling height and its wall photos, plus the floors
 * below it (drawn faded) or, for All floors, every floor pulled apart ([FloorStack]); rebuilds the scene off the main
 * thread whenever one of them changes. A tapped room (or the room the screen opened on) puts the camera inside it
 * (AC-C22) until Back to floor. Floor, All floors, stage and room survive process death. The home's overlays and placed
 * items are placed on their wall or floor of the scene ([SceneMarkers], AC-C23) and shown by the layer toggles the House
 * tab's blueprint uses, read from and written to the same [LayerStore].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class House3DViewModel(
    private val repo: HomeRepository,
    private val wallPhotos: WallPhotoRepository,
    private val homeId: String,
    homeName: String,
    initialFloorId: String?,
    private val saved: SavedStateHandle,
    /** The stage the view opens on: Finished in House Manual, Rough-in ("behind the wall") in Field Log. */
    defaultStage: WallStage = WallStage.HouseManualDefault,
    private val buildMesh: MeshMaker = DefaultMeshMaker,
    /** Open standing inside this room ("View in 3D" on a room page, AC-C22). */
    initialRoomId: String? = null,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    /** The home's overlays (AC-C23). */
    overlays: Flow<List<Overlay>> = flowOf(emptyList()),
    /** The home's items with a spot on the plan (AC-C23). */
    itemSpots: Flow<List<ItemSpot>> = flowOf(emptyList()),
    private val layerStore: LayerStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        House3DUiState(
            homeName = homeName,
            floorId = saved[FLOOR] ?: initialFloorId,
            allFloors = saved[ALL] ?: false,
            stage = saved.get<String>(STAGE)?.let(WallStage::fromStored) ?: defaultStage,
            roomId = if (saved.contains(ROOM)) saved.get<String>(ROOM) else initialRoomId,
            openedOnRoom = saved[OPENED_ON_ROOM] ?: (initialRoomId != null),
            layers = layerStore?.read(homeId) ?: LayerSet(),
        ),
    )
    val state: StateFlow<House3DUiState> = _state.asStateFlow()

    /** The floors in view and where they sit; only ids, heights and placement change the mesh. */
    private val stack = MutableStateFlow<List<StackedFloor>>(emptyList())
    private val retries = MutableStateFlow(0)

    /** The scene last built and its walls by id, for placing the markers. */
    private val placed = MutableStateFlow<Pair<FloorScene, Map<String, WallKey>>?>(null)

    /** The newest overlays, so a tapped marker can be opened. */
    private var latestOverlays: Map<String, Overlay> = emptyMap()

    init {
        viewModelScope.launch {
            repo.observeFloors(homeId).collect { floors ->
                val chosen = floors.firstOrNull { it.id == _state.value.floorId } ?: floors.defaultFloor()
                saved[FLOOR] = chosen?.id
                _state.update {
                    it.copy(
                        floors = floors,
                        floorId = chosen?.id,
                        loading = if (chosen == null) false else it.loading,
                        scene = if (chosen == null) null else it.scene,
                    )
                }
                restack()
            }
        }
        viewModelScope.launch {
            combine(stack, retries) { layers, _ -> layers }
                .flatMapLatest { layers ->
                    if (layers.isEmpty()) {
                        emptyFlow()
                    } else {
                        combine(layers.map { f -> repo.observePlan(f.floorId).map { SceneLayer(f, it.placed) } }) { it.toList() }
                            .flatMapLatest { planned ->
                                val solid = planned.filterNot { it.floor.ghost }.flatMap { l -> l.rooms.map { it.id } }
                                combine(wallPhotos.observeWallPictures(solid), wallPhotos.observeWallKeys(solid)) { pictures, walls ->
                                    Built(layers, planned, pictures, walls)
                                }
                            }
                    }
                }
                .collect { (layers, planned, pictures, walls) ->
                    try {
                        val scene = withContext(work) { FloorScene.stack(planned, pictures, buildMesh) }
                        if (stack.value == layers) {
                            _state.update { it.copy(scene = scene, loading = false, failed = false) }
                            placed.value = scene to walls
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not build the 3D floor", e)
                        _state.update { it.copy(scene = null, markers = emptyList(), loading = false, failed = true) }
                    }
                }
        }
        viewModelScope.launch {
            combine(placed.filterNotNull(), overlays, itemSpots) { p, o, i -> Triple(p, o, i) }.collect { (p, o, items) ->
                latestOverlays = o.associateBy { it.id }
                val (scene, walls) = p
                val markers = try {
                    withContext(work) { SceneMarkers.place(scene, o, walls, items) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not place the 3D markers", e)
                    emptyList()
                }
                // Only for the scene on screen: a floor switch may have replaced it meanwhile.
                if (_state.value.scene === scene) _state.update { it.copy(markers = markers) }
            }
        }
    }

    /** The overlay a tapped marker stands for, while it still exists. */
    fun overlay(id: String): Overlay? = latestOverlays[id]

    private fun restack() {
        val s = _state.value
        stack.value = FloorStack.layout(s.floors, s.floorId, s.allFloors)
    }

    fun onEvent(event: House3DEvent) {
        when (event) {
            is House3DEvent.SelectFloor -> {
                if (event.floorId == _state.value.floorId && !_state.value.allFloors) return
                val next = _state.value.floors.firstOrNull { it.id == event.floorId } ?: return
                saved[FLOOR] = next.id
                saved[ALL] = false
                leaveRoom()
                _state.update { it.copy(floorId = next.id, allFloors = false, scene = null, markers = emptyList(), loading = true, failed = false) }
                restack()
            }
            House3DEvent.ShowAllFloors -> {
                if (_state.value.allFloors) return
                saved[ALL] = true
                leaveRoom()
                _state.update { it.copy(allFloors = true, scene = null, markers = emptyList(), loading = true, failed = false) }
                restack()
            }
            is House3DEvent.SelectStage -> {
                saved[STAGE] = event.stage.name
                _state.update { it.copy(stage = event.stage) }
            }
            House3DEvent.ResetView -> _state.update { it.copy(resetCount = it.resetCount + 1) }
            is House3DEvent.ViewRoom -> {
                // Only a room the camera can stand in: a solid floor's room in the current scene.
                if (_state.value.scene?.rooms?.containsKey(event.roomId) != true) return
                saved[ROOM] = event.roomId
                _state.update { it.copy(roomId = event.roomId) }
            }
            House3DEvent.BackToFloor -> leaveRoom()
            is House3DEvent.ToggleLayer -> {
                val next = _state.value.layers.toggled(event.layer)
                layerStore?.write(homeId, next)
                _state.update { it.copy(layers = next) }
            }
            House3DEvent.Retry -> {
                _state.update { it.copy(loading = true, failed = false) }
                retries.update { it + 1 }
            }
        }
    }

    private fun leaveRoom() {
        saved[ROOM] = null
        saved[OPENED_ON_ROOM] = false
        _state.update { it.copy(roomId = null, openedOnRoom = false) }
    }

    private data class Built(
        val layers: List<StackedFloor>,
        val planned: List<SceneLayer>,
        val pictures: Map<WallKey, Map<WallStage, String>>,
        val walls: Map<String, WallKey>,
    )

    private companion object {
        const val FLOOR = "floor"
        const val STAGE = "stage"
        const val ALL = "allFloors"
        const val ROOM = "room"
        const val OPENED_ON_ROOM = "openedOnRoom"
        const val TAG = "House3D"
    }
}
