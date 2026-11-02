package com.kurtz.homerecord.housemodel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import com.kurtz.homerecord.housemodel.walls.WallTextureMaker

/**
 * HM-Wall for [wallId] in [roomId] (AC-C25..C28), opened on [stage] with overlay [selectId] picked when it came from the
 * room's Overlays list. Back steps out of the form, the drawing or the selection before it leaves the wall.
 */
@Composable
fun WallOverlaysRoute(
    repo: HomeRepository,
    wallPhotos: WallPhotoRepository,
    overlays: OverlayRepository,
    maker: WallTextureMaker,
    roomId: String,
    wallId: String,
    stage: WallStage,
    selectId: String?,
    onBack: () -> Unit,
) {
    val vm = viewModel<WallOverlaysViewModel>(
        key = "wall-overlays:$wallId",
        factory = viewModelFactory {
            initializer { WallOverlaysViewModel(repo, wallPhotos, overlays, maker, roomId, wallId, stage, selectId, createSavedStateHandle()) }
        },
    )
    val state = vm.state.collectAsState().value
    val back = {
        when {
            state.form != null -> vm.onEvent(WallOverlaysEvent.CloseForm)
            state.drawing != null -> vm.onEvent(WallOverlaysEvent.CancelDrawing)
            state.selectedId != null -> vm.onEvent(WallOverlaysEvent.Select(null))
            else -> onBack()
        }
    }
    // The form's own handler, composed later, asks before discarding changes.
    BackHandler(enabled = state.form != null || state.drawing != null || state.selectedId != null, onBack = back)
    WallOverlaysScreen(state, { event -> if (event == WallOverlaysEvent.Back) back() else vm.onEvent(event) })
}
