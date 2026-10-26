package com.kurtz.homerecord.fieldlog.photos

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File

@Composable
fun PhotoDetailRoute(deps: PhotoDetailDeps, onAnnotate: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel<PhotoDetailViewModel>(key = "photo-${deps.photoId}", factory = viewModelFactory { initializer { PhotoDetailViewModel(deps) } })
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    PhotoDetailScreen(state, vm::onEvent, onShare = { sharePhoto(context, state.imagePath) }, onAnnotate = onAnnotate, onBack = onBack)
}

private fun sharePhoto(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
