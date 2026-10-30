package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.coreui.platform.Intents
import java.io.File

@Composable
fun PhotoDetailRoute(deps: PhotoDetailDeps, onAnnotate: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel<PhotoDetailViewModel>(key = "photo-${deps.photoId}", factory = viewModelFactory { initializer { PhotoDetailViewModel(deps) } })
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    PhotoDetailScreen(state, onEvent = { event ->
        when (event) {
            PhotoDetailEvent.Share -> Intents.shareFile(context, File(state.imagePath), "image/jpeg")
            PhotoDetailEvent.Annotate -> onAnnotate()
            PhotoDetailEvent.Back -> onBack()
            else -> vm.onEvent(event)
        }
    })
}
