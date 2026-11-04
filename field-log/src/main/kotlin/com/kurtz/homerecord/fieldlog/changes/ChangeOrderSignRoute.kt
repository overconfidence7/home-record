package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Sign on phone; [onSigned] opens the signed, read-only order. */
@Composable
fun ChangeOrderSignRoute(deps: ChangeOrderSignDeps, onBack: () -> Unit, onSigned: (String) -> Unit) {
    val vm = viewModel<ChangeOrderSignViewModel>(
        key = "co-sign-${deps.changeOrderId}",
        factory = viewModelFactory { initializer { ChangeOrderSignViewModel(deps, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedId) { state.signedId?.let(onSigned) }
    ChangeOrderSignScreen(state, { e -> if (e == ChangeOrderSignEvent.Back) onBack() else vm.onEvent(e) })
}
