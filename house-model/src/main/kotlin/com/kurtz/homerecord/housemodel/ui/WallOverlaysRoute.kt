package com.kurtz.homerecord.housemodel.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import com.kurtz.homerecord.housemodel.walls.WallTextureMaker

/**
 * HM-Wall for [wallId] in [roomId] (AC-C25..C28), opened on [stage] with overlay [selectId] picked when it came from the
 * room's Overlays list or an item's Links. Back steps out of the form, the drawing or the selection before it leaves the
 * wall. With [media] the form takes a photo from the camera or the gallery (AC-C26).
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
    media: MediaFiles? = null,
) {
    val vm = viewModel<WallOverlaysViewModel>(
        key = "wall-overlays:$wallId",
        factory = viewModelFactory {
            initializer { WallOverlaysViewModel(repo, wallPhotos, overlays, maker, roomId, wallId, stage, selectId, createSavedStateHandle(), media) }
        },
    )
    val state = vm.state.collectAsState().value
    // The pending camera file lives in the view model's SavedStateHandle, so a result after rotation or process death still lands.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onEvent(WallOverlaysEvent.CameraResult(ok)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        vm.onEvent(WallOverlaysEvent.GalleryResult(uri?.toString()))
    }
    LaunchedEffect(state.photoLaunch) {
        val launch = state.photoLaunch ?: return@LaunchedEffect
        vm.onEvent(WallOverlaysEvent.PhotoLaunchHandled)
        val target = launch.target
        if (target != null) camera.launch(Uri.parse(target.uri))
        else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
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
