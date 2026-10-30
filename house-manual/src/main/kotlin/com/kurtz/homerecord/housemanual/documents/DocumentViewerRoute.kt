package com.kurtz.homerecord.housemanual.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.coreui.platform.Intents
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
            DocumentViewerEvent.Share -> Intents.shareFile(context, File(state.path), state.mime, state.name)
        }
    })
}
