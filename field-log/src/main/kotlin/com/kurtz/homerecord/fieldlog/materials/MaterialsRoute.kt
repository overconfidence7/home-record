package com.kurtz.homerecord.fieldlog.materials

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.net.Uri
import com.kurtz.homerecord.core.material.ReceiptTotal
import com.kurtz.homerecord.core.ocr.TextRecognizer
import com.kurtz.homerecord.coreui.pages.rememberReceiptScanner
import com.kurtz.homerecord.coreui.platform.ContentFiles
import com.kurtz.homerecord.coreui.platform.Intents
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
fun MaterialsRoute(deps: () -> MaterialsDeps, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val vm = viewModel<MaterialsViewModel>(factory = viewModelFactory { initializer { MaterialsViewModel(deps()) } })
    val state by vm.state.collectAsState()
    MaterialsScreen(state, onEvent = { e ->
        when (e) {
            MaterialsEvent.Add -> onAdd()
            is MaterialsEvent.Open -> onOpen(e.id)
            MaterialsEvent.Export -> vm.export()
            MaterialsEvent.DismissExportFailed -> vm.dismissExportFailed()
        }
    }, onBack = onBack)
}

@Composable
fun MaterialFormRoute(deps: MaterialFormDeps, onClose: () -> Unit) {
    val vm = viewModel<MaterialFormViewModel>(
        key = "material_form_${deps.materialId}",
        factory = viewModelFactory { initializer { MaterialFormViewModel(deps, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    val scan = rememberReceiptScanner(
        onImage = { vm.onEvent(MaterialFormEvent.Scanned(it)) },
        onFailed = { vm.onEvent(MaterialFormEvent.ScanFailed) },
        onCancelled = { vm.onEvent(MaterialFormEvent.ScanCancelled) },
    )
    MaterialFormScreen(state, onEvent = { e ->
        vm.onEvent(e)
        if (e == MaterialFormEvent.Scan && state.canScan) scan()
    }, onClose = onClose)
}

/** Copies the scanner's page into `filesDir/media/<uuid>.jpg` and reads the total off it with on-device OCR (AC-F44). */
class AndroidReceiptReader(private val context: Context, private val ocr: TextRecognizer) : ReceiptReader {
    override suspend fun read(uri: String): ScannedReceipt? {
        val out = File(File(context.filesDir, "media"), "${UUID.randomUUID()}.jpg")
        val copied = withContext(Dispatchers.IO) { ContentFiles.copy(context, Uri.parse(uri), out) }
        if (!copied) return null
        return ScannedReceipt(out.absolutePath, ReceiptTotal.find(ocr.recognizeLines(out.absolutePath)))
    }
}

/** Writes the CSV into cache/exports and opens the share sheet through FileProvider, like the time exports (AC-F44). */
class AndroidMaterialsExporter(private val context: Context) : MaterialsExporter {
    override suspend fun export(fileName: String, csv: String): Boolean {
        val file = try {
            withContext(Dispatchers.IO) { File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName).apply { writeText(csv) } }
        } catch (_: IOException) {
            return false
        }
        return runCatching { Intents.shareFile(context, file, "text/csv") }.isSuccess
    }
}
