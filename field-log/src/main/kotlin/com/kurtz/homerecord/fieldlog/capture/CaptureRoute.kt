package com.kurtz.homerecord.fieldlog.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.photo.LensChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun CaptureRoute(jobId: String, deps: (Context) -> CaptureDeps, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel<CaptureViewModel>(
        key = jobId,
        factory = viewModelFactory { initializer { CaptureViewModel(deps(context.applicationContext), createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onEvent(CaptureEvent.PermissionResult(it)) }
    LaunchedEffect(state.access) { if (state.access == CameraAccess.Unknown) permission.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onEvent(CaptureEvent.Resumed) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onEvent(if (it) CaptureEvent.VoiceTap else CaptureEvent.MicDenied)
    }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val paths = withContext(Dispatchers.IO) { uris.mapNotNull { copyIn(context, it) } }
            if (paths.isEmpty()) vm.onEvent(CaptureEvent.CaptureFailed) else vm.onEvent(CaptureEvent.Imported(paths))
        }
    }
    val pick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(state.flash) {
        imageCapture.flashMode = when (state.flash) {
            FlashMode.Off -> ImageCapture.FLASH_MODE_OFF
            FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.On -> ImageCapture.FLASH_MODE_ON
        }
    }
    val onEvent: (CaptureEvent) -> Unit = { event ->
        when (event) {
            CaptureEvent.Shutter -> takePicture(context, imageCapture, vm)
            CaptureEvent.VoiceTap ->
                if (state.recording || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.onEvent(event)
                else mic.launch(Manifest.permission.RECORD_AUDIO)
            CaptureEvent.OpenSettings -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            else -> vm.onEvent(event)
        }
    }
    CaptureScreen(state, onEvent, onBack, onPickGallery = pick) { modifier ->
        val owner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }
        LaunchedEffect(state.access, state.lens) {
            if (state.access != CameraAccess.Granted) return@LaunchedEffect
            val provider = ProcessCameraProvider.awaitInstance(context)
            vm.onEvent(
                CaptureEvent.CameraInfo(
                    hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA),
                    hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA),
                ),
            )
            val selector = when (state.lens) {
                LensChoice.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
                LensChoice.Back -> CameraSelector.DEFAULT_BACK_CAMERA
                LensChoice.None -> return@LaunchedEffect
            }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            runCatching { provider.bindToLifecycle(owner, selector, preview, imageCapture) }
        }
        AndroidView({ previewView }, modifier)
    }
}

private fun takePicture(context: Context, imageCapture: ImageCapture, vm: CaptureViewModel) {
    val file = File(mediaDir(context), "${UUID.randomUUID()}.jpg")
    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = vm.onEvent(CaptureEvent.Captured(file.absolutePath))
            override fun onError(exception: ImageCaptureException) = vm.onEvent(CaptureEvent.CaptureFailed)
        },
    )
}

private fun mediaDir(context: Context): File = File(context.filesDir, "media").also { it.mkdirs() }

private fun copyIn(context: Context, uri: Uri): String? = runCatching {
    val file = File(mediaDir(context), "${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
    file.absolutePath
}.getOrNull()
