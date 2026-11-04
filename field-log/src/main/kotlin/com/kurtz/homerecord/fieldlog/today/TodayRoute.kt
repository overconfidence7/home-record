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
    /** FL-Sync: where the pending count, Sync now and the conflict log live (AC-F52). */
    val onOpenSync: () -> Unit,
    val onOpenClock: () -> Unit = {},
    val onOpenSchedule: () -> Unit = {},
    val onOpenJob: (String) -> Unit = {},
    val onAddJob: () -> Unit,
    val onOpenTask: (jobId: String, taskId: String) -> Unit = { _, _ -> },
    /** Opens FL-Capture tagged to the job (AC-F24). */
    val onCapture: (jobId: String) -> Unit = {},
    val onOpenPhoto: (photoId: String) -> Unit = {},
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
    TodayScreen(
        vm.state.collectAsState().value,
        onEvent = { e ->
            when (e) {
                is TodayEvent.CompleteTask -> vm.completeTask(e.taskId)
                TodayEvent.UndoTask -> vm.undoTask()
                TodayEvent.DismissTaskUndo -> vm.dismissTaskUndo()
                TodayEvent.Capture -> vm.startCapture()?.let(navigation.onCapture)
                TodayEvent.DismissCapturePicker -> vm.dismissCapturePicker()
                is TodayEvent.CaptureFor -> {
                    vm.dismissCapturePicker()
                    navigation.onCapture(e.jobId)
                }
                else -> todayEvent(e, navigation, context, vm::refresh)
            }
        },
        modifier,
    )
}

/** Handles a Today event outside the screen; [onRefresh] is the ViewModel's, or nothing before the member is known. */
fun todayEvent(event: TodayEvent, navigation: TodayNavigation, context: Context, onRefresh: () -> Unit = {}) {
    when (event) {
        TodayEvent.InviteCrew -> navigation.onInviteCrew()
        TodayEvent.OpenSync -> navigation.onOpenSync()
        TodayEvent.OpenClock -> navigation.onOpenClock()
        TodayEvent.OpenSchedule -> navigation.onOpenSchedule()
        is TodayEvent.OpenJob -> navigation.onOpenJob(event.jobId)
        TodayEvent.AddJob -> navigation.onAddJob()
        TodayEvent.Refresh -> onRefresh()
        is TodayEvent.OpenTask -> navigation.onOpenTask(event.jobId, event.taskId)
        is TodayEvent.OpenPhoto -> navigation.onOpenPhoto(event.photoId)
        is TodayEvent.CaptureFor -> navigation.onCapture(event.jobId)
        // Ticking a task and the camera button's choice need the ViewModel; TodayRoute handles these before they get here.
        is TodayEvent.CompleteTask, TodayEvent.UndoTask, TodayEvent.DismissTaskUndo,
        TodayEvent.Capture, TodayEvent.DismissCapturePicker -> Unit
        is TodayEvent.OpenPin -> {
            val lat = event.entry.lat ?: return
            val lng = event.entry.lng ?: return
            Intents.openMap(context, lat, lng, event.entry.name)
        }
    }
}
