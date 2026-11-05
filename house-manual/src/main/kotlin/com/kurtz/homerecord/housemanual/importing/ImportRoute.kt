package com.kurtz.homerecord.housemanual.importing

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Wires HM-Import to the file at [uri] (a pick from More, a share or Open with) (AC-H17). [onClose] after Back or Finish. */
@Composable
fun ImportRoute(files: ImportFiles, uri: Uri, onClose: () -> Unit) {
    val vm = viewModel<ImportViewModel>(
        key = "import:$uri",
        factory = viewModelFactory {
            initializer { ImportViewModel(read = { files.read(uri) }, apply = files::apply, saved = createSavedStateHandle()) }
        },
    )
    BackHandler(onBack = onClose)
    ImportScreen(vm.state.collectAsState().value) { e ->
        when (e) {
            ImportEvent.Back, ImportEvent.Finish -> onClose()
            else -> vm.onEvent(e)
        }
    }
}
