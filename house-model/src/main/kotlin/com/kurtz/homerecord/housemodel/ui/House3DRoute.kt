package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.scene.FloorSceneView
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
    onEditPlan: (floorId: String?) -> Unit,
    /** Field Log passes [WallStage.BehindTheWall] so a crew sees the rough-in photos first. */
    defaultStage: WallStage = WallStage.HouseManualDefault,
) {
    val vm = viewModel<House3DViewModel>(
        factory = viewModelFactory { initializer { House3DViewModel(repo, wallPhotos, homeId, homeName, floorId, createSavedStateHandle(), defaultStage) } },
    )
    // AC-C24 first render is timed from here, the screen opening, not from when the mesh is ready.
    val openedAtNanos = remember { System.nanoTime() }
    House3DScreen(vm.state.collectAsState().value, vm::onEvent, onBack, onEditPlan) { scene, stage, resetCount, modifier ->
        FloorSceneView(scene, stage, resetCount, modifier, openedAtNanos)
    }
}
