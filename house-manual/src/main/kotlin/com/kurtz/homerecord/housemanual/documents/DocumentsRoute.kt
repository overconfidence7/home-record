package com.kurtz.homerecord.housemanual.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.housemodel.documents.DocumentRepository

@Composable
fun DocumentsRoute(repo: DocumentRepository, homeId: String, onBack: () -> Unit) {
    val vm = viewModel<DocumentsViewModel>(
        factory = viewModelFactory { initializer { DocumentsViewModel(repo, homeId, createSavedStateHandle()) } },
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onEvent(DocumentsEvent.Picked(uri.toString()))
    }
    DocumentsScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            DocumentsEvent.Back -> onBack()
            DocumentsEvent.Upload -> picker.launch(arrayOf("application/pdf", "image/*"))
            else -> vm.onEvent(it)
        }
    })
}
