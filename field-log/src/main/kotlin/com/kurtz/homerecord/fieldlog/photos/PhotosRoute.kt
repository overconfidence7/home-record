package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun PhotosRoute(deps: PhotosDeps, onCapture: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm = viewModel<PhotosViewModel>(key = "photos-${deps.jobId}", factory = viewModelFactory { initializer { PhotosViewModel(deps) } })
    PhotosScreen(
        vm.state.collectAsState().value,
        { if (it is PhotosEvent.Open) onOpen(it.photoId) else onCapture() },
        modifier,
    )
}
