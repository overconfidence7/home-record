package com.kurtz.homerecord.fieldlog.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.sync.SyncPhase

@Composable
fun TodayRoute(
    deps: () -> TodayDeps,
    syncPhase: SyncPhase,
    pending: Int,
    onInviteCrew: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenClock: () -> Unit,
    onOpenJob: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel<TodayViewModel>(factory = viewModelFactory { initializer { TodayViewModel(deps()) } })
    LaunchedEffect(syncPhase, pending) { vm.setSync(syncPhase, pending) }
    TodayScreen(vm.state.collectAsState().value, onInviteCrew, modifier, onSyncNow, onOpenClock, onOpenJob)
}
