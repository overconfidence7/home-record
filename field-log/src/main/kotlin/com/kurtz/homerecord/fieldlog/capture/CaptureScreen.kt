package com.kurtz.homerecord.fieldlog.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

/** AC-55 shutter size; also the lower bound asserted by tests. */
val ShutterSize = 80.dp

sealed interface CaptureEvent {
    data object Shutter : CaptureEvent
}

@Composable
fun CaptureRoute(
    viewModel: CaptureViewModel,
    capturer: CameraXPhotoCapturer,
    keys: ShutterKeyRouter,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    val shutter = rememberUpdatedState(viewModel::onShutter)
    DisposableEffect(granted) {
        if (granted) keys.onShutter = { shutter.value() }
        onDispose { keys.onShutter = null }
    }
    when {
        granted -> CaptureScreen(state, onEvent = { viewModel.onShutter() }) {
            CameraPreview(capturer)
        }
        asked -> PermissionDenied(
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onBack = onBack,
        )
        else -> Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun CameraPreview(capturer: CameraXPhotoCapturer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = runCatching { future.get() }.getOrNull() ?: return@Runnable
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                capturer.imageCapture = imageCapture
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            capturer.imageCapture = null
            runCatching { future.get().unbindAll() }
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Full-screen preview slot plus the shutter; [preview] is the camera surface (a stub in tests). */
@Composable
fun CaptureScreen(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit, preview: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        preview()
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.failed) {
                Text(stringResource(R.string.capture_failed), color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            if (state.photosThisSession > 0) {
                Text(
                    stringResource(R.string.capture_count, state.photosThisSession),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val label = stringResource(R.string.capture_shutter)
            Button(
                onClick = { onEvent(CaptureEvent.Shutter) },
                enabled = !state.busy,
                shape = CircleShape,
                modifier = Modifier.size(ShutterSize).semantics { contentDescription = label },
            ) {}
        }
    }
}

@Composable
fun PermissionDenied(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.capture_permission_needed),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.capture_open_settings))
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.capture_back))
        }
    }
}

@ComposePreview(showBackground = true)
@Composable
private fun CaptureScreenPreview() {
    HomeRecordTheme { CaptureScreen(CaptureUiState(photosThisSession = 3), onEvent = {}) {} }
}
