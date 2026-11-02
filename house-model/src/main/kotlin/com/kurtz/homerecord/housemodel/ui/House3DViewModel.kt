package com.kurtz.homerecord.housemodel.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.defaultFloor
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.scene.DefaultMeshMaker
import com.kurtz.homerecord.housemodel.scene.MeshMaker
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HM-3D (AC-C20): follows the chosen floor's plan, its ceiling height and its wall photos, and rebuilds the floor's
 * mesh off the main thread whenever one of them changes. Floor and stage survive process death.
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
            stage = saved.get<String>(STAGE)?.let(WallStage::fromStored) ?: defaultStage,
        ),
    )
    val state: StateFlow<House3DUiState> = _state.asStateFlow()

    /** The floor in view; only its id and ceiling height change the mesh. */
    private val floor = MutableStateFlow<FloorSummary?>(null)
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
                floor.value = chosen
            }
        }
        viewModelScope.launch {
            combine(floor.map { it?.let { f -> f.id to f.ceilingHeightIn } }.distinctUntilChanged(), retries) { f, _ -> f }
                .flatMapLatest { f ->
                    if (f == null) {
                        emptyFlow()
                    } else {
                        repo.observePlan(f.first).flatMapLatest { plan ->
                            wallPhotos.observeWallPictures(plan.placed.map { it.id }).map { pictures -> Triple(f, plan.placed, pictures) }
                        }
                    }
                }
                .collect { (f, rooms, pictures) ->
                    try {
                        val scene = withContext(work) { FloorScene.build(rooms, f.second, pictures, buildMesh) }
                        if (_state.value.floorId == f.first) _state.update { it.copy(scene = scene, loading = false, failed = false) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not build the 3D floor", e)
                        _state.update { it.copy(scene = null, loading = false, failed = true) }
                    }
                }
        }
    }

    fun onEvent(event: House3DEvent) {
        when (event) {
            is House3DEvent.SelectFloor -> {
                if (event.floorId == _state.value.floorId) return
                val next = _state.value.floors.firstOrNull { it.id == event.floorId } ?: return
                saved[FLOOR] = next.id
                _state.update { it.copy(floorId = next.id, scene = null, loading = true, failed = false) }
                floor.value = next
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
        const val TAG = "House3D"
    }
}
