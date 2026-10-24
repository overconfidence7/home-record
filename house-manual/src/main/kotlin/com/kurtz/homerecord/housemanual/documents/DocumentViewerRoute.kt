package com.kurtz.homerecord.housemanual.documents

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import java.io.File

@Composable
fun DocumentViewerRoute(repo: DocumentRepository, documentId: String, onBack: () -> Unit) {
    val vm = viewModel<DocumentViewerViewModel>(
        factory = viewModelFactory { initializer { DocumentViewerViewModel(repo, documentId) } },
    )
    val context = LocalContext.current
    val state = vm.state.collectAsState().value
    DocumentViewerScreen(state, onEvent = {
        when (it) {
            DocumentViewerEvent.Back -> onBack()
            DocumentViewerEvent.Share -> {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", File(state.path))
                val send = Intent(Intent.ACTION_SEND)
                    .setType(state.mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TITLE, state.name)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, state.name))
            }
        }
    })
}
