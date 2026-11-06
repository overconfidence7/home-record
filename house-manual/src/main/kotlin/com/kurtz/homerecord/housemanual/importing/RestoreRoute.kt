package com.kurtz.homerecord.housemanual.importing

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Restore from a backup at [uri] (AC-C48, AC-H14): HM-Restore asks for the passphrase; once it opens, the backup's
 * import preview (HM-Import worded as a restore) brings the home back. [onClose] after Back or Finish.
 */
@Composable
fun RestoreRoute(files: ImportFiles, uri: Uri, onClose: () -> Unit) {
    val vm = viewModel<RestoreViewModel>(
        key = "restore:$uri",
        factory = viewModelFactory { initializer { RestoreViewModel { files.readBackup(uri, it) } } },
    )
    BackHandler(onBack = onClose)
    val opened = vm.opened.collectAsState().value
    if (opened == null) {
        RestoreScreen(vm.state.collectAsState().value) { e -> if (e == RestoreEvent.Back) onClose() else vm.onEvent(e) }
        return
    }
    val preview = viewModel<ImportViewModel>(
        key = "restore-import:$uri",
        factory = viewModelFactory {
            initializer {
                ImportViewModel(
                    read = { ImportRead.Ok(opened) }, apply = files::apply, saved = createSavedStateHandle(), discard = files::discard, restore = true,
                )
            }
        },
    )
    ImportScreen(preview.state.collectAsState().value) { e ->
        when (e) {
            ImportEvent.Back, ImportEvent.Finish -> onClose()
            else -> preview.onEvent(e)
        }
    }
}
