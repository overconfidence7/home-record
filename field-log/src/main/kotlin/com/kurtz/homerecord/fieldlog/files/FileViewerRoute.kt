package com.kurtz.homerecord.fieldlog.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.model.TaskFilePin
import com.kurtz.homerecord.coreui.platform.Intents
import java.io.File

@Composable
fun FileViewerRoute(
    deps: FileViewerDeps,
    onBack: () -> Unit,
    onMarkUp: (String) -> Unit,
    onOpenVersion: (String) -> Unit,
    /** "New task here": the task form for the job with the pin filled in (AC-F35). */
    onNewTask: (jobId: String, pin: TaskFilePin) -> Unit = { _, _ -> },
    /** A pin opens its task: the form for those who manage tasks, the task read only for Crew (R09g). */
    onOpenTask: (jobId: String, taskId: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val vm = viewModel<FileViewerViewModel>(key = "file-${deps.fileId}", factory = viewModelFactory { initializer { FileViewerViewModel(deps) } })
    val state = vm.state.collectAsState().value
    FileViewerScreen(state, onEvent = { e ->
        when (e) {
            FileViewerEvent.Back -> onBack()
            FileViewerEvent.Retry -> vm.onRetry()
            FileViewerEvent.Share -> Intents.shareFile(context, File(state.path), state.mime, state.name)
            FileViewerEvent.MarkUp -> onMarkUp(deps.fileId)
            FileViewerEvent.ShowHistory -> vm.onShowHistory(true)
            FileViewerEvent.HideHistory -> vm.onShowHistory(false)
            FileViewerEvent.NewTaskHere -> {
                val pin = vm.spotPin()
                val jobId = state.jobId
                vm.onNewTaskOpened()
                if (pin != null && jobId != null) onNewTask(jobId, pin)
            }
            is FileViewerEvent.OpenPin -> state.jobId?.let { job -> onOpenTask(job, e.taskId) }
            FileViewerEvent.StartPinning, FileViewerEvent.CancelPinning, is FileViewerEvent.TapPage, is FileViewerEvent.ChooseTask,
            FileViewerEvent.DismissChooser, FileViewerEvent.DismissPinFailed -> vm.onPinEvent(e)
            is FileViewerEvent.OpenVersion -> {
                vm.onShowHistory(false)
                if (state.versions.firstOrNull { it.id == e.id }?.shown != true) onOpenVersion(e.id)
            }
        }
    })
}
