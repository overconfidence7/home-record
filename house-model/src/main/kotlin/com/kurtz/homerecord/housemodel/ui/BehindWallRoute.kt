package com.kurtz.homerecord.housemodel.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository

/**
 * FL-BehindWall for the job's home [homeId] (AC-F49). [onOutline] opens the outline screen for the new photo at the stage
 * the wall is at; [onOpenRoom] opens the room page to draw overlays; [onEditPlan] opens the plan when no room is drawn.
 */
@Composable
fun BehindWallRoute(
    repo: HomeRepository,
    wallPhotos: WallPhotoRepository,
    media: MediaFiles,
    homeId: String,
    title: String,
    onBack: () -> Unit,
    onOutline: (roomId: String, request: WallOutlineRequest) -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    onEditPlan: () -> Unit,
    roomId: String? = null,
) {
    val vm = viewModel<BehindWallViewModel>(
        key = "behind-wall:$homeId:${roomId.orEmpty()}",
        factory = viewModelFactory {
            initializer {
                BehindWallViewModel(
                    title, { repo.roomChoices(homeId) }, wallPhotos::observeRoomWalls, media, createSavedStateHandle(), startRoomId = roomId,
                )
            }
        },
    )
    val state = vm.state.collectAsState().value
    BackHandler(enabled = state.roomId != null && state.roomId != roomId) { vm.back() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onEvent(BehindWallEvent.CameraResult(ok)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        vm.onEvent(BehindWallEvent.GalleryResult(uri?.toString()))
    }
    LaunchedEffect(state.photoLaunch) {
        val launch = state.photoLaunch ?: return@LaunchedEffect
        vm.onEvent(BehindWallEvent.PhotoLaunchHandled)
        val target = launch.target
        if (target != null) camera.launch(Uri.parse(target.uri)) else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(state.outlineRequest) {
        val request = state.outlineRequest ?: return@LaunchedEffect
        val room = state.roomId ?: return@LaunchedEffect
        vm.onEvent(BehindWallEvent.OutlineOpened)
        onOutline(room, request)
    }
    BehindWallScreen(state) { event ->
        when (event) {
            BehindWallEvent.Back -> if (!vm.back()) onBack()
            BehindWallEvent.EditPlan -> onEditPlan()
            BehindWallEvent.OpenRoomPage -> state.roomId?.let(onOpenRoom)
            else -> vm.onEvent(event)
        }
    }
}
