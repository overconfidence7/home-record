package com.kurtz.homerecord.housemodel.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.HomeRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HM-3D (AC-C20, AC-C21): follows the chosen floor's plan, its ceiling height and its wall photos, plus the floors
 * below it (drawn faded) or, for All floors, every floor pulled apart ([FloorStack]); rebuilds the scene off the main
 * thread whenever one of them changes. Floor, All floors and stage survive process death.
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
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(
        House3DUiState(
            homeName = homeName,
            floorId = saved[FLOOR] ?: initialFloorId,
            allFloors = saved[ALL] ?: false,
            stage = saved.get<String>(STAGE)?.let(WallStage::fromStored) ?: defaultStage,
        ),
    )
    val state: StateFlow<House3DUiState> = _state.asStateFlow()

    /** The floors in view and where they sit; only ids, heights and placement change the mesh. */
    private val stack = MutableStateFlow<List<StackedFloor>>(emptyList())
    private val retries = MutableStateFlow(0)

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
                                wallPhotos.observeWallPictures(solid).map { pictures -> Triple(layers, planned, pictures) }
                            }
                    }
                }
                .collect { (layers, planned, pictures) ->
                    try {
                        val scene = withContext(work) { FloorScene.stack(planned, pictures, buildMesh) }
                        if (stack.value == layers) _state.update { it.copy(scene = scene, loading = false, failed = false) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not build the 3D floor", e)
                        _state.update { it.copy(scene = null, loading = false, failed = true) }
                    }
                }
        }
    }

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
                _state.update { it.copy(floorId = next.id, allFloors = false, scene = null, loading = true, failed = false) }
                restack()
            }
            House3DEvent.ShowAllFloors -> {
                if (_state.value.allFloors) return
                saved[ALL] = true
                _state.update { it.copy(allFloors = true, scene = null, loading = true, failed = false) }
                restack()
            }
            is House3DEvent.SelectStage -> {
                saved[STAGE] = event.stage.name
                _state.update { it.copy(stage = event.stage) }
            }
            House3DEvent.ResetView -> _state.update { it.copy(resetCount = it.resetCount + 1) }
            House3DEvent.Retry -> {
                _state.update { it.copy(loading = true, failed = false) }
                retries.update { it + 1 }
            }
        }
    }

    private companion object {
        const val FLOOR = "floor"
        const val STAGE = "stage"
        const val ALL = "allFloors"
        const val TAG = "House3D"
    }
}
