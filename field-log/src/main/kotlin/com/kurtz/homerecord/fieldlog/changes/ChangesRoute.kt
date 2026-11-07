package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun ChangesRoute(deps: ChangesDeps, onOpen: (String?) -> Unit, modifier: Modifier = Modifier) {
    val vm = viewModel<ChangesViewModel>(key = "changes-${deps.jobId}", factory = viewModelFactory { initializer { ChangesViewModel(deps) } })
    val state by vm.state.collectAsState()
    ChangesScreen(
        state,
        { e ->
            if (e == ChangesEvent.Retry) { vm.retry(); return@ChangesScreen }
            if (!vm.state.value.canManage) return@ChangesScreen
            when (e) {
                is ChangesEvent.Open -> onOpen(e.id)
                ChangesEvent.Add -> onOpen(null)
                ChangesEvent.Retry -> Unit
            }
        },
        modifier,
    )
}
