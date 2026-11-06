package com.kurtz.homerecord.housemanual.backup

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.housemanual.R

/** Wires HM-Backup to [files] for [homeId]; [onRestore] gets the backup file picked to restore. */
@Composable
fun BackupRoute(files: BackupFiles, homeId: String, homeName: String, onRestore: (Uri) -> Unit, onBack: () -> Unit, viewOnly: Boolean = false) {
    val vm = viewModel<BackupViewModel>(factory = viewModelFactory { initializer { BackupViewModel(homeName) { files.export(homeId, it) } } })
    val state = vm.state.collectAsState().value.copy(viewOnly = viewOnly)
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.backup_share_title)
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onRestore(uri) }
    LaunchedEffect(state.uri) {
        val uri = state.uri ?: return@LaunchedEffect
        vm.onEvent(BackupEvent.ShareHandled)
        Intents.shareUri(context, Uri.parse(uri), MIME, shareTitle)
    }
    BackupScreen(state) { e ->
        when (e) {
            BackupEvent.Back -> onBack()
            BackupEvent.Restore -> pick.launch(PICK_TYPES)
            else -> vm.onEvent(e)
        }
    }
}

/** No registered type for `.hrbackup`; the picker shows everything and the file's first bytes decide. */
const val MIME = "application/octet-stream"
val PICK_TYPES = arrayOf("application/octet-stream", "*/*")
