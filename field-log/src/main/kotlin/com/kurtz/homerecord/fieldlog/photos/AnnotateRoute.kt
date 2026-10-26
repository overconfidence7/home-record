package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun AnnotateRoute(deps: AnnotateDeps, onBack: () -> Unit) {
    val vm = viewModel<AnnotateViewModel>(key = "annotate-${deps.photoId}", factory = viewModelFactory { initializer { AnnotateViewModel(deps) } })
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    AnnotateScreen(state, vm::onEvent, onBack)
}
