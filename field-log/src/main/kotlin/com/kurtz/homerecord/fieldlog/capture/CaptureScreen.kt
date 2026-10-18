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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
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
    var micDenied by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) viewModel.onEvent(CaptureEvent.ToggleVoice) else micDenied = true
    }
    DisposableEffect(Unit) { onDispose { viewModel.onLeave() } }
    val onEvent: (CaptureEvent) -> Unit = { event ->
        val needsMic = event == CaptureEvent.ToggleVoice && !state.recording &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO) else viewModel.onEvent(event)
    }
    val settings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    val shutter = rememberUpdatedState(viewModel::onShutter)
    DisposableEffect(granted) {
        if (granted) keys.onShutter = { shutter.value() }
        onDispose { keys.onShutter = null }
    }
    when {
        granted && micDenied -> PermissionDenied(
            message = stringResource(R.string.capture_mic_permission_needed),
            onOpenSettings = settings,
            onBack = { micDenied = false },
        )
        granted -> CaptureScreen(state, onEvent) {
            CameraPreview(capturer)
        }
        asked -> PermissionDenied(
            message = stringResource(R.string.capture_permission_needed),
            onOpenSettings = settings,
            onBack = onBack,
        )
        else -> Box(Modifier.fillMaxSize())
    }
}

@Composable
internal fun CameraPreview(capturer: CameraXPhotoCapturer) {
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
                val selector = when (CameraChoice.pick(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA), provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))) {
                    CameraChoice.Lens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    CameraChoice.Lens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraChoice.Lens.NONE -> return@runCatching
                }
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
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
fun CaptureScreen(
    state: CaptureUiState,
    onEvent: (CaptureEvent) -> Unit,
    showExtras: Boolean = true,
    preview: @Composable () -> Unit,
) {
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
                    pluralStringResource(R.plurals.capture_count, state.photosThisSession, state.photosThisSession),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.recording) {
                Text(
                    stringResource(R.string.capture_recording, VoiceCap.format(state.elapsedMs)),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (state.voiceFailed) {
                Text(stringResource(R.string.capture_voice_failed), color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            if (state.notesThisSession > 0 || state.voiceThisSession > 0) {
                Text(
                    stringResource(
                        R.string.capture_extras_count,
                        pluralStringResource(R.plurals.capture_notes_count, state.notesThisSession, state.notesThisSession),
                        pluralStringResource(R.plurals.capture_voice_count, state.voiceThisSession, state.voiceThisSession),
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val label = stringResource(R.string.capture_shutter)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showExtras) {
                    Button(
                        onClick = { onEvent(CaptureEvent.OpenNote) },
                        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 96.dp),
                    ) { Text(stringResource(R.string.capture_note)) }
                }
                Button(
                    onClick = { onEvent(CaptureEvent.Shutter) },
                    enabled = !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.size(ShutterSize).semantics { contentDescription = label },
                ) {}
                if (showExtras) {
                    Button(
                        onClick = { onEvent(CaptureEvent.ToggleVoice) },
                        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 96.dp),
                    ) {
                        Text(stringResource(if (state.recording) R.string.capture_voice_stop else R.string.capture_voice))
                    }
                }
            }
        }
        if (state.noteOpen) NoteDialog(state, onEvent)
    }
}

@Composable
private fun NoteDialog(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(CaptureEvent.DismissNote) },
        title = { Text(stringResource(R.string.capture_note_title)) },
        text = {
            OutlinedTextField(
                value = state.noteText,
                onValueChange = { onEvent(CaptureEvent.NoteChanged(it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(CaptureEvent.SaveNote) },
                enabled = state.canSaveNote,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.capture_note_save)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CaptureEvent.DismissNote) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.capture_note_cancel))
            }
        },
    )
}

@Composable
fun PermissionDenied(message: String, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
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
