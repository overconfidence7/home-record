package com.kurtz.homerecord.fieldlog.today

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.sync.RefusedChange
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.platform.Intents

/** Where Today's taps lead; the app wires each to the back stack, the sync engine or another tab. */
class TodayNavigation(
    val onInviteCrew: () -> Unit,
    val onSyncNow: () -> Unit,
    val onClearRefused: () -> Unit,
    val onOpenClock: () -> Unit = {},
    val onOpenJob: (String) -> Unit = {},
    val onAddJob: () -> Unit,
)

@Composable
fun TodayRoute(
    deps: () -> TodayDeps,
    syncPhase: SyncPhase,
    pending: Int,
    refused: List<RefusedChange>,
    navigation: TodayNavigation,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel<TodayViewModel>(factory = viewModelFactory { initializer { TodayViewModel(deps()) } })
    LaunchedEffect(syncPhase, pending, refused) { vm.setSync(syncPhase, pending, refused) }
    val context = LocalContext.current
    TodayScreen(vm.state.collectAsState().value, onEvent = { todayEvent(it, navigation, context, vm::refresh) }, modifier)
}

/** Handles a Today event outside the screen; [onRefresh] is the ViewModel's, or nothing before the member is known. */
fun todayEvent(event: TodayEvent, navigation: TodayNavigation, context: Context, onRefresh: () -> Unit = {}) {
    when (event) {
        TodayEvent.InviteCrew -> navigation.onInviteCrew()
        TodayEvent.SyncNow -> navigation.onSyncNow()
        TodayEvent.ClearRefused -> navigation.onClearRefused()
        TodayEvent.OpenClock -> navigation.onOpenClock()
        is TodayEvent.OpenJob -> navigation.onOpenJob(event.jobId)
        TodayEvent.AddJob -> navigation.onAddJob()
        TodayEvent.Refresh -> onRefresh()
        is TodayEvent.OpenPin -> {
            val lat = event.entry.lat ?: return
            val lng = event.entry.lng ?: return
            Intents.openMap(context, lat, lng, event.entry.name)
        }
    }
}
