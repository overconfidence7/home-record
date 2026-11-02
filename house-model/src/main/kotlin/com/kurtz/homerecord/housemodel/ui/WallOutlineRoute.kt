package com.kurtz.homerecord.housemodel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import com.kurtz.homerecord.housemodel.walls.WallTextureMaker

/** HM-WallOutline for a new photo of [wallId] at [stage]; [onClose] returns to the room after Use or Back. */
@Composable
fun WallOutlineRoute(
    wallPhotos: WallPhotoRepository,
    maker: WallTextureMaker,
    media: MediaFiles,
    roomId: String,
    wallId: String,
    stage: WallStage,
    photoPath: String,
    onClose: () -> Unit,
) {
    val vm = viewModel<WallOutlineViewModel>(
        key = "wall-outline:$photoPath",
        factory = viewModelFactory {
            initializer { WallOutlineViewModel(wallPhotos, maker, media, roomId, wallId, stage, photoPath, createSavedStateHandle()) }
        },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.done) { if (state.done) onClose() }
    val back = {
        vm.onEvent(WallOutlineEvent.Back)
        onClose()
    }
    BackHandler(onBack = back)
    WallOutlineScreen(state, { event -> if (event == WallOutlineEvent.Back) back() else vm.onEvent(event) })
}
