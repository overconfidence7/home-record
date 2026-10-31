package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.coreui.components.DateField
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R
import java.io.File

/**
 * Full-screen task form (FL-Tasks, AC-F34, rule 15): title, who it is for, due day, room, photo, note and the punch
 * list switch. Close asks before dropping typed changes (rule 3); an existing task can be deleted after a confirm (rule 16).
 */
@Composable
fun TaskFormScreen(state: TaskFormUiState, onEvent: (TaskFormEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.pinning && !state.loading && !state.failed) {
        TaskPinPicker(state, onEvent, modifier)
        return
    }
    FormScaffold(
        title = stringResource(if (state.isNew) R.string.task_form_new else R.string.task_form_edit),
        dirty = state.dirty && !state.done,
        saveEnabled = !state.working && state.draft.titleOk,
        onClose = { onEvent(TaskFormEvent.Close) },
        onSave = { onEvent(TaskFormEvent.Save) },
        modifier = modifier.testTag("fl_task_form"),
        placeholder = when {
            state.loading -> { m -> LoadingState(m.fillMaxSize()) }
            state.failed -> { m -> ErrorState(stringResource(R.string.task_form_failed), { onEvent(TaskFormEvent.Retry) }, m.fillMaxSize()) }
            else -> null
        },
    ) {
        LabeledField(
            label = stringResource(R.string.task_form_title),
            value = state.draft.title,
            onValueChange = { onEvent(TaskFormEvent.Title(it)) },
            modifier = Modifier.testTag("fl_task_form_title"),
            required = true,
            helper = stringResource(R.string.task_form_title_help),
            error = if (state.titleError) stringResource(R.string.task_form_title_error) else null,
        )
        PickerField(
            label = stringResource(R.string.task_form_assignee),
            value = state.assigneeName ?: stringResource(R.string.task_form_nobody),
            icon = { Icon(Icons.Rounded.Person, null) },
            onClick = { onEvent(TaskFormEvent.OpenSheet(TaskFormSheet.ASSIGNEE)) },
            tag = "fl_task_form_assignee",
        )
        DateField(
            label = stringResource(R.string.task_form_due),
            isoDate = state.draft.due,
            onDateChange = { onEvent(TaskFormEvent.Due(it)) },
            modifier = Modifier.testTag("fl_task_form_due"),
        )
        if (state.draft.due != null) {
            TextButton({ onEvent(TaskFormEvent.Due(null)) }, Modifier.heightIn(min = 48.dp).testTag("fl_task_form_due_clear")) {
                Text(stringResource(R.string.task_form_due_clear))
            }
        }
        PickerField(
            label = stringResource(R.string.task_form_room),
            value = state.roomName ?: stringResource(R.string.task_form_no_room),
            icon = { Icon(Icons.Rounded.MeetingRoom, null) },
            onClick = { onEvent(TaskFormEvent.OpenSheet(TaskFormSheet.ROOM)) },
            tag = "fl_task_form_room",
        )
        if (state.plan.isNotEmpty()) {
            val place = state.pinPlace
            PickerField(
                label = stringResource(R.string.task_form_pin),
                value = when {
                    place == null -> stringResource(R.string.task_form_pin_none)
                    place.second == null -> place.first
                    else -> stringResource(R.string.task_form_pin_place, place.first, place.second.orEmpty())
                },
                icon = { Icon(Icons.Rounded.PushPin, null) },
                onClick = { onEvent(TaskFormEvent.OpenPin) },
                tag = "fl_task_form_pin",
            )
        }
        PickerField(
            label = stringResource(R.string.task_form_photo),
            value = stringResource(if (state.photo != null) R.string.task_form_photo_set else R.string.task_form_no_photo),
            icon = {
                val p = state.photo
                if (p != null && p.path.isNotEmpty()) {
                    AsyncImage(File(p.path), null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                } else Icon(Icons.Rounded.PhotoLibrary, null)
            },
            onClick = { onEvent(TaskFormEvent.OpenSheet(TaskFormSheet.PHOTO)) },
            tag = "fl_task_form_photo",
        )
        LabeledField(
            label = stringResource(R.string.task_form_note),
            value = state.draft.note,
            onValueChange = { onEvent(TaskFormEvent.Note(it)) },
            modifier = Modifier.testTag("fl_task_form_note"),
            singleLine = false,
            imeAction = ImeAction.Default,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = state.draft.punch, role = Role.Switch, onValueChange = { onEvent(TaskFormEvent.Punch(it)) })
                .testTag("fl_task_form_punch"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(R.string.tasks_punch), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.task_form_punch_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.draft.punch, onCheckedChange = null)
        }
        if (!state.isNew) {
            OutlinedButton(
                onClick = { onEvent(TaskFormEvent.AskDelete) },
                enabled = !state.working,
                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_task_form_delete"),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.task_form_delete))
            }
        }
    }
    when (state.sheet) {
        TaskFormSheet.ASSIGNEE -> OptionsSheet(
            title = stringResource(R.string.task_form_assignee),
            options = listOf(SheetOption(NONE, stringResource(R.string.task_form_nobody), Icons.Rounded.PersonOff)) +
                state.people.map { SheetOption(it.id, it.name, Icons.Rounded.Person) },
            onDismiss = { onEvent(TaskFormEvent.CloseSheet) },
            onPick = { onEvent(TaskFormEvent.Assignee(it.id.takeIf { id -> id != NONE })) },
        )
        TaskFormSheet.ROOM -> OptionsSheet(
            title = stringResource(R.string.task_form_room),
            options = listOf(SheetOption(NONE, stringResource(R.string.task_form_no_room))) +
                state.rooms.map { SheetOption(it.id, it.name, Icons.Rounded.MeetingRoom) },
            onDismiss = { onEvent(TaskFormEvent.CloseSheet) },
            onPick = { onEvent(TaskFormEvent.Room(it.id.takeIf { id -> id != NONE })) },
        )
        TaskFormSheet.PHOTO -> PhotoSheet(state.photos, state.draft.photoId, onEvent)
        null -> Unit
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(TaskFormEvent.DismissDelete) },
            title = { Text(stringResource(R.string.task_delete_title, state.initial.title)) },
            text = { Text(stringResource(R.string.task_delete_body)) },
            confirmButton = {
                TextButton({ onEvent(TaskFormEvent.Delete) }, Modifier.heightIn(min = 48.dp).testTag("fl_task_form_delete_confirm")) {
                    Text(stringResource(R.string.task_form_delete))
                }
            },
            dismissButton = { TextButton({ onEvent(TaskFormEvent.DismissDelete) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.files_cancel)) } },
            modifier = Modifier.windowTestTags(),
        )
    }
}

private const val NONE = "none"

/** A read-only field that opens a picker sheet (rule 20/22): label, current value, leading icon, drop-down arrow. */
@Composable
private fun PickerField(label: String, value: String, icon: @Composable () -> Unit, onClick: () -> Unit, tag: String) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            // Wraps instead of clipping at large text ("Main floor · Mudroom" at 200%, rule 32).
            value = value, onValueChange = {}, readOnly = true, singleLine = false, maxLines = 3,
            label = { Text(label) },
            leadingIcon = icon,
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The whole field is the target, so the sheet opens wherever it is tapped; TalkBack reads "label, value".
        Box(
            Modifier.matchParentSize().clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label, $value" }.testTag(tag),
        )
    }
}

/** The job's photos, three across (rule 27), with "No photo" first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSheet(photos: List<PhotoOption>, selected: String?, onEvent: (TaskFormEvent) -> Unit) {
    ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(TaskFormEvent.CloseSheet) }) {
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.widthIn(max = 640.dp).navigationBarsPadding().testTag("fl_task_form_photo_sheet"),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                Text(
                    stringResource(R.string.task_form_photo), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = NONE) {
                TextButton({ onEvent(TaskFormEvent.Photo(null)) }, Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp).testTag("fl_task_form_photo_none")) {
                    Icon(Icons.Rounded.HideImage, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.task_form_no_photo))
                }
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Text(stringResource(R.string.task_form_no_photos), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(photos, key = { it.id }) { p ->
                val label = stringResource(if (p.id == selected) R.string.task_form_photo_picked else R.string.task_form_photo_pick)
                val shape = RoundedCornerShape(if (p.id == selected) 16.dp else 0.dp)
                Box(
                    Modifier.aspectRatio(1f).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onEvent(TaskFormEvent.Photo(p.id)) }
                        .semantics { contentDescription = label }.testTag("fl_task_form_photo_${p.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (p.path.isEmpty()) Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else AsyncImage(File(p.path), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}
