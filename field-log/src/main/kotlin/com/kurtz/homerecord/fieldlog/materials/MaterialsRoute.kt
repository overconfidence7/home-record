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
import com.kurtz.homerecord.coreui.platform.Intents
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
    MaterialFormScreen(state, onEvent = vm::onEvent, onClose = onClose)
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
