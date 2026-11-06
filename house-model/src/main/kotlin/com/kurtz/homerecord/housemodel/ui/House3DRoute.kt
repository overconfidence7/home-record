package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.scene.FloorSceneView
import com.kurtz.homerecord.housemodel.scene.MarkerTarget
import com.kurtz.homerecord.housemodel.scene.SceneMarker
import kotlinx.coroutines.flow.flowOf
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage

/** HM-3D (and FL-House 3D) for [homeId], opening on [floorId] (the floor the House tab shows) when given. */
@Composable
fun House3DRoute(
    repo: HomeRepository,
    wallPhotos: WallPhotoRepository,
    homeId: String,
    homeName: String,
    floorId: String?,
    onBack: () -> Unit,
    /** Null for a viewer of a shared home (AC-H16): the empty floor offers no Edit plan. */
    onEditPlan: ((floorId: String?) -> Unit)?,
    /** Field Log passes [WallStage.BehindTheWall] so a crew sees the rough-in photos first. */
    defaultStage: WallStage = WallStage.HouseManualDefault,
    /** Open standing inside this room ("View in 3D" on a room page, AC-C22). */
    roomId: String? = null,
    /** The home's overlays and placed items, drawn in 3D under the layer toggles (AC-C23). */
    overlays: OverlayRepository? = null,
    layerStore: LayerStore? = null,
    /** A tapped overlay marker: its wall or plan with it picked (AC-C23, as from search: AC-C28). */
    onOpenOverlay: (Overlay) -> Unit = {},
    /** A tapped item marker: the item, and the room it stands in. */
    onOpenItem: (itemId: String, roomId: String) -> Unit = { _, _ -> },
) {
    val vm = viewModel<House3DViewModel>(
        factory = viewModelFactory {
            initializer {
                House3DViewModel(
                    repo, wallPhotos, homeId, homeName, floorId, createSavedStateHandle(), defaultStage, initialRoomId = roomId,
                    overlays = overlays?.observeForHome(homeId) ?: flowOf(emptyList()),
                    itemSpots = overlays?.observeItemSpots(homeId) ?: flowOf(emptyList()),
                    layerStore = layerStore,
                )
            }
        },
    )
    val state = vm.state.collectAsState().value
    val openMarker: (SceneMarker) -> Unit = { marker ->
        when (val target = marker.target) {
            is MarkerTarget.OverlayMark -> vm.overlay(target.overlayId)?.let(onOpenOverlay)
            is MarkerTarget.ItemMark -> onOpenItem(target.itemId, marker.roomId)
        }
    }
    // AC-C24 first render is timed from here, the screen opening, not from when the mesh is ready.
    val openedAtNanos = remember { System.nanoTime() }
    House3DScreen(state, vm::onEvent, onBack, onEditPlan, onOpenMarker = openMarker) { scene, camera, modifier ->
        FloorSceneView(scene, camera, modifier, openedAtNanos)
    }
}
