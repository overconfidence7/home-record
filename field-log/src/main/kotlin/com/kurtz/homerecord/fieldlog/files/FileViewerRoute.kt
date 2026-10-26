package com.kurtz.homerecord.fieldlog.files

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File

@Composable
fun FileViewerRoute(deps: FileViewerDeps, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel<FileViewerViewModel>(key = "file-${deps.fileId}", factory = viewModelFactory { initializer { FileViewerViewModel(deps) } })
    val state = vm.state.collectAsState().value
    FileViewerScreen(state) { e ->
        when (e) {
            FileViewerEvent.Back -> onBack()
            FileViewerEvent.Share -> shareFile(context, state.path, state.mime, state.name)
        }
    }
}

private fun shareFile(context: Context, path: String, mime: String, name: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
