package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.scene.FloorSceneView
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository

/** HM-3D for [homeId], opening on [floorId] (the floor the House tab shows) when given. */
@Composable
fun House3DRoute(
    repo: HomeRepository,
    wallPhotos: WallPhotoRepository,
    homeId: String,
    homeName: String,
    floorId: String?,
    onBack: () -> Unit,
    onEditPlan: (floorId: String?) -> Unit,
) {
    val vm = viewModel<House3DViewModel>(
        factory = viewModelFactory { initializer { House3DViewModel(repo, wallPhotos, homeId, homeName, floorId, createSavedStateHandle()) } },
    )
    House3DScreen(vm.state.collectAsState().value, vm::onEvent, onBack, onEditPlan) { scene, stage, resetCount, modifier ->
        FloorSceneView(scene, stage, resetCount, modifier)
    }
}
