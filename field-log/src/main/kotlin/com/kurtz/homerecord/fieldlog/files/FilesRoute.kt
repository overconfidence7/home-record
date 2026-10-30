package com.kurtz.homerecord.fieldlog.files

import android.content.Context
import android.net.Uri
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
import com.kurtz.homerecord.coreui.platform.ContentFiles
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
private fun importFile(context: Context, uri: Uri): ImportedFile? {
    val name = ContentFiles.displayName(context, uri) ?: uri.lastPathSegment ?: "File"
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val ext = name.substringAfterLast('.', "").ifEmpty { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin" }
    val out = File(File(context.filesDir, "media"), "${UUID.randomUUID()}.$ext")
    if (!ContentFiles.copy(context, uri, out)) return null
    return ImportedFile(name, out.absolutePath, mime, out.length())
}
