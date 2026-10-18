package com.kurtz.homerecord.housemanual.rooms

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kurtz.homerecord.housemanual.breaker.confidenceLabel
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.RoomKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.PhotoTile
import java.io.File
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun RoomRoute(viewModel: RoomViewModel, mediaStore: MediaFileStore, onDeleted: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onDeleted() } }
    RoomScreen(state, viewModel::onEvent, photoFile = { mediaStore.resolve(it) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomScreen(state: RoomUiState, onEvent: (RoomEvent) -> Unit, photoFile: (String) -> File? = { null }) {
    val room = state.room
    if (room == null) {
        if (state.loaded) Text(stringResource(R.string.room_missing), Modifier.padding(16.dp))
        return
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.renaming) {
            OutlinedTextField(
                value = state.renameInput,
                onValueChange = { onEvent(RoomEvent.RenameChanged(it)) },
                label = { Text(stringResource(R.string.room_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("room_rename_input"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(RoomEvent.SaveRename) },
                    enabled = state.canSaveRename,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("room_rename_save"),
                ) { Text(stringResource(R.string.save)) }
                TextButton(onClick = { onEvent(RoomEvent.CancelRename) }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        } else {
            Text(room.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("room_title"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEvent(RoomEvent.StartRename) }, Modifier.heightIn(min = 48.dp).testTag("room_rename")) {
                    Text(stringResource(R.string.room_rename))
                }
                OutlinedButton(onClick = { onEvent(RoomEvent.AskDelete) }, Modifier.heightIn(min = 48.dp).testTag("room_delete")) {
                    Text(stringResource(R.string.room_delete))
                }
            }
        }

        Text(stringResource(R.string.room_kind), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoomKind.entries.forEach { k ->
                FilterChip(
                    selected = room.kind == k,
                    onClick = { onEvent(RoomEvent.KindChanged(k)) },
                    label = { Text(stringResource(roomKindLabel(k))) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("room_kind_${k.name}"),
                )
            }
        }

        Section(stringResource(R.string.room_breakers), emptyText = stringResource(R.string.room_no_breakers), empty = state.breakers.isEmpty()) {
            state.breakers.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("room_breaker"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ConfidenceDot(b.confidence)
                    Text(b.label)
                }
            }
        }
        Section(stringResource(R.string.room_endpoints), stringResource(R.string.room_no_endpoints), state.endpoints.isEmpty()) {
            state.endpoints.forEach { Text(it.label, Modifier.heightIn(min = 32.dp)) }
        }
        Section(stringResource(R.string.room_equipment), stringResource(R.string.room_no_equipment), state.equipment.isEmpty()) {
            state.equipment.forEach { e ->
                Text(
                    listOfNotNull(e.make, e.model).joinToString(" ").ifBlank { stringResource(e.category.labelRes()) }.trim(),
                    Modifier.heightIn(min = 32.dp).testTag("room_equipment"),
                )
            }
        }
        Section(stringResource(R.string.room_shutoffs), stringResource(R.string.room_no_shutoffs), state.shutoffs.isEmpty()) {
            state.shutoffs.forEach { Text(stringResource(it.kind.labelRes()), Modifier.heightIn(min = 32.dp).testTag("room_shutoff")) }
        }
        Section(stringResource(R.string.room_photos), stringResource(R.string.room_no_photos), state.photos.isEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.photos.forEach { photo ->
                    PhotoTile(
                        photoFile(photo.path),
                        contentDescription = photo.caption ?: stringResource(photo.stage.labelRes()),
                        modifier = Modifier.testTag("room_photo"),
                        size = 96.dp,
                    )
                }
            }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(RoomEvent.CancelDelete) },
            title = { Text(stringResource(R.string.room_delete_title)) },
            text = { Text(stringResource(R.string.room_delete_body, room.name)) },
            confirmButton = {
                TextButton(onClick = { onEvent(RoomEvent.ConfirmDelete) }, Modifier.testTag("room_delete_confirm")) {
                    Text(stringResource(R.string.room_delete))
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(RoomEvent.CancelDelete) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String, emptyText: String, empty: Boolean, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (empty) Text(emptyText, style = MaterialTheme.typography.bodyMedium) else content()
}

@Composable
private fun ConfidenceDot(confidence: Confidence) {
    val color = when (confidence) {
        Confidence.CONFIRMED -> Color(0xFF2E7D32)
        Confidence.LIKELY -> Color(0xFFF9A825)
        Confidence.UNKNOWN -> Color(0xFF9E9E9E)
    }
    val label = stringResource(R.string.confidence_dot_description, stringResource(confidenceLabel(confidence)))
    Box(
        Modifier.size(14.dp).background(color, CircleShape)
            .semantics { contentDescription = label }
            .testTag("confidence_${confidence.name}"),
    )
}

@Preview(showBackground = true)
@Composable
private fun RoomScreenPreview() {
    val breaker = Breaker(id = "b", panelId = "p", slots = listOf(1), label = "Kitchen outlets", confidence = Confidence.LIKELY, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)
    HomeRecordTheme {
        RoomScreen(RoomUiState(loaded = true, room = Room("r", "h", "Kitchen", null, 0, 0, Source.HOUSE_MANUAL), breakers = listOf(breaker)), onEvent = {})
    }
}

fun roomKindLabel(kind: RoomKind): Int = when (kind) {
    RoomKind.KITCHEN -> R.string.room_kind_kitchen
    RoomKind.BATHROOM -> R.string.room_kind_bathroom
    RoomKind.BEDROOM -> R.string.room_kind_bedroom
    RoomKind.GARAGE -> R.string.room_kind_garage
    RoomKind.BASEMENT -> R.string.room_kind_basement
    RoomKind.LAUNDRY -> R.string.room_kind_laundry
    RoomKind.EXTERIOR -> R.string.room_kind_exterior
    RoomKind.HALLWAY -> R.string.room_kind_hallway
    RoomKind.LIVING -> R.string.room_kind_living
    RoomKind.DINING -> R.string.room_kind_dining
    RoomKind.OFFICE -> R.string.room_kind_office
    RoomKind.ATTIC -> R.string.room_kind_attic
    RoomKind.FAMILY -> R.string.room_kind_family
    RoomKind.CLOSET -> R.string.room_kind_closet
    RoomKind.CRAWL_SPACE -> R.string.room_kind_crawl_space
    RoomKind.UTILITY -> R.string.room_kind_utility
    RoomKind.OTHER -> R.string.room_kind_other
}
