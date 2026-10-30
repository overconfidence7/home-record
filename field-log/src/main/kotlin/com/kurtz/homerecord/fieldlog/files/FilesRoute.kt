package com.kurtz.homerecord.fieldlog.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun FilesRoute(deps: FilesDeps, onOpenFile: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm = viewModel<FilesViewModel>(
        key = "files-${deps.jobId}",
        factory = viewModelFactory { initializer { FilesViewModel(deps, createSavedStateHandle()) } },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { vm.added(withContext(Dispatchers.IO) { importFile(context, uri) }) }
    }
    FilesScreen(
        vm.state.collectAsState().value,
        { e ->
            when (e) {
                FilesEvent.AddFile -> if (vm.state.value.canManage) { vm.onEvent(e); picker.launch(arrayOf("*/*")) }
                is FilesEvent.OpenFile -> onOpenFile(e.id)
                else -> vm.onEvent(e)
            }
        },
        modifier,
    )
}

/** Copies a picked document into `filesDir/media/<uuid>.<ext>` so it survives the picker's grant. Null when it can't be read. */
private fun importFile(context: Context, uri: Uri): ImportedFile? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: uri.lastPathSegment ?: "File"
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val ext = name.substringAfterLast('.', "").ifEmpty { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin" }
    val dir = File(context.filesDir, "media").apply { mkdirs() }
    val out = File(dir, "${UUID.randomUUID()}.$ext")
    val input = resolver.openInputStream(uri) ?: return@runCatching null
    input.use { src -> out.outputStream().use { src.copyTo(it) } }
    ImportedFile(name, out.absolutePath, mime, out.length())
}.getOrNull()
