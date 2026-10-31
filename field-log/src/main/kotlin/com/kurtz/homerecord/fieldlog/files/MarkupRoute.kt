package com.kurtz.homerecord.fieldlog.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** [onSaved] gets the new version's id, so the viewer can show it in place of the one marked up. */
@Composable
fun MarkupRoute(deps: MarkupDeps, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val vm = viewModel<MarkupViewModel>(key = "markup-${deps.fileId}", factory = viewModelFactory { initializer { MarkupViewModel(deps) } })
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    MarkupScreen(state) { e -> if (e == MarkupEvent.Close) onClose() else vm.onEvent(e) }
}
