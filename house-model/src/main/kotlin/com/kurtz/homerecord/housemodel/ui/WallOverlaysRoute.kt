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
 * room's Overlays list or an item's Links. Back follows [WallBack]: it steps out of the form or the drawing first, and
 * unpicks only an overlay picked on this screen. With [media] the form takes a photo from the camera or the gallery (AC-C26).
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
    OverlayPhotoLaunchers(state.photoLaunch, vm::onEvent)
    fun back(fromArrow: Boolean) {
        when (WallBack.step(state.form != null, state.drawing != null, state.selectedId, selectId, fromArrow)) {
            WallBackStep.CloseForm -> vm.onEvent(WallOverlaysEvent.CloseForm)
            WallBackStep.CancelDrawing -> vm.onEvent(WallOverlaysEvent.CancelDrawing)
            WallBackStep.Unpick -> vm.onEvent(WallOverlaysEvent.Select(null))
            WallBackStep.Leave -> onBack()
        }
    }
    // The form's own handler, composed later, asks before discarding changes.
    val backSteps = WallBack.step(state.form != null, state.drawing != null, state.selectedId, selectId, fromArrow = false) != WallBackStep.Leave
    BackHandler(enabled = backSteps) { back(fromArrow = false) }
    WallOverlaysScreen(state, { event -> if (event == WallOverlaysEvent.Back) back(fromArrow = true) else vm.onEvent(event) })
}

/**
 * Opens the camera or the photo picker the overlay form asked for in [launch] and hands the result back through [onEvent].
 * The pending camera file lives in the view model's SavedStateHandle, so a result after rotation or process death still lands.
 */
@Composable
internal fun OverlayPhotoLaunchers(launch: OverlayPhotoLaunch?, onEvent: (WallOverlaysEvent) -> Unit) {
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> onEvent(WallOverlaysEvent.CameraResult(ok)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        onEvent(WallOverlaysEvent.GalleryResult(uri?.toString()))
    }
    LaunchedEffect(launch) {
        launch ?: return@LaunchedEffect
        onEvent(WallOverlaysEvent.PhotoLaunchHandled)
        val target = launch.target
        if (target != null) camera.launch(Uri.parse(target.uri))
        else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
