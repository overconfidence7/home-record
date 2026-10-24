package com.kurtz.homerecord.housemanual.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import androidx.activity.compose.LocalActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
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
    val activity = LocalActivity.current
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            if (pdf != null) vm.onEvent(DocumentsEvent.Picked(pdf.toString())) else vm.onEvent(DocumentsEvent.ScanFailed)
        }
    }
    DocumentsScreen(vm.state.collectAsState().value, onEvent = {
        when (it) {
            DocumentsEvent.Back -> onBack()
            DocumentsEvent.Upload -> picker.launch(arrayOf("application/pdf", "image/*"))
            DocumentsEvent.Scan -> if (activity == null) {
                vm.onEvent(DocumentsEvent.ScanFailed)
            } else {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(20)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener { vm.onEvent(DocumentsEvent.ScanFailed) }
            }
            else -> vm.onEvent(it)
        }
    })
}
