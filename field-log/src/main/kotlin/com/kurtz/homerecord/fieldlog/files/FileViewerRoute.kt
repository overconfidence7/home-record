package com.kurtz.homerecord.fieldlog.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.coreui.platform.Intents
import java.io.File

@Composable
fun FileViewerRoute(deps: FileViewerDeps, onBack: () -> Unit, onMarkUp: (String) -> Unit, onOpenVersion: (String) -> Unit) {
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
            is FileViewerEvent.OpenVersion -> {
                vm.onShowHistory(false)
                if (state.versions.firstOrNull { it.id == e.id }?.shown != true) onOpenVersion(e.id)
            }
        }
    })
}
