package com.kurtz.homerecord.fieldlog.behindwall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kurtz.homerecord.core.logic.WallRow
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.capture.CameraPreview
import com.kurtz.homerecord.fieldlog.capture.CameraXPhotoCapturer
import com.kurtz.homerecord.fieldlog.capture.CaptureEvent
import com.kurtz.homerecord.fieldlog.capture.CaptureScreen
import com.kurtz.homerecord.fieldlog.capture.CaptureUiState
import com.kurtz.homerecord.fieldlog.capture.PermissionDenied
import com.kurtz.homerecord.fieldlog.capture.ShutterKeyRouter
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun BehindWallRoute(
    viewModel: BehindWallViewModel,
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
    val inCamera = state.step == WallStep.CAMERA
    // Ask only once the user reaches the camera step.
    LaunchedEffect(inCamera) {
        if (inCamera && !granted) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    val shutter = rememberUpdatedState(viewModel::onShutter)
    DisposableEffect(granted, inCamera) {
        if (granted && inCamera) keys.onShutter = { shutter.value() }
        onDispose { keys.onShutter = null }
    }
    BackHandler(enabled = state.step != WallStep.ROOM) { viewModel.onEvent(BehindWallEvent.Back) }
    when {
        inCamera && !granted && asked -> PermissionDenied(
            message = stringResource(R.string.capture_permission_needed),
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onBack = { viewModel.onEvent(BehindWallEvent.Back) },
        )
        inCamera && granted -> WallCameraScreen(state, onEvent = viewModel::onEvent) { CameraPreview(capturer) }
        else -> BehindWallScreen(state, viewModel::onEvent, onClose = onBack)
    }
}

/** Camera step: the shared capture screen without note/voice, with the running wall count on top. */
@Composable
fun WallCameraScreen(state: BehindWallUiState, onEvent: (BehindWallEvent) -> Unit, preview: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CaptureScreen(
            CaptureUiState(busy = state.busy, failed = state.failed, photosThisSession = state.photosThisSession),
            onEvent = { if (it == CaptureEvent.Shutter) onEvent(BehindWallEvent.Shutter) },
            showExtras = false,
            preview = preview,
        )
        val wall = state.wall
        if (wall != null) {
            Text(
                stringResource(
                    R.string.wall_camera_header,
                    state.room?.name.orEmpty(),
                    pluralStringResource(R.plurals.wall_count, wall.count, wall.label, wall.count),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )
        }
    }
}

/** Room and wall pickers (AC-63) with per-wall counts (AC-65). */
@Composable
fun BehindWallScreen(state: BehindWallUiState, onEvent: (BehindWallEvent) -> Unit, onClose: () -> Unit) {
    val room = state.room
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                stringResource(if (room == null) R.string.wall_pick_room else R.string.wall_pick_wall, room?.name.orEmpty()),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (room == null) {
            if (state.loaded && state.rooms.isEmpty()) {
                item { Text(stringResource(R.string.wall_no_rooms), style = MaterialTheme.typography.bodyLarge) }
            }
            items(state.rooms, key = { it.id }) { r ->
                Button(
                    onClick = { onEvent(BehindWallEvent.SelectRoom(r.id)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(r.name) }
            }
            item {
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.capture_back))
                }
            }
        } else {
            items(state.walls, key = { it.label }) { w ->
                Button(
                    onClick = { onEvent(BehindWallEvent.SelectWall(w.label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(pluralStringResource(R.plurals.wall_count, w.count, w.label, w.count)) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.customLabel,
                        onValueChange = { onEvent(BehindWallEvent.CustomChanged(it)) },
                        label = { Text(stringResource(R.string.wall_custom)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onEvent(BehindWallEvent.AddCustom) },
                        enabled = state.customLabel.isNotBlank(),
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.jobedit_add)) }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onEvent(BehindWallEvent.Back) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.wall_change_room)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BehindWallPreview() {
    HomeRecordTheme {
        BehindWallScreen(
            BehindWallUiState(
                loaded = true,
                rooms = listOf(Room("r", "h", "Kitchen", null, 0, 0, Source.FIELD_LOG)),
                roomId = "r",
                walls = listOf(WallRow("n", "North", 3), WallRow(null, "East", 0)),
            ),
            onEvent = {}, onClose = {},
        )
    }
}
