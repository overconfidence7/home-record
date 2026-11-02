package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A Crew member's own task, read only (R09g, AC-F3, AC-F35): what, who, when, where on the plan or a file, the photo and
 * the note. Ticking it done stays on the Tasks list and Today; only Owner, Office and the job's Lead change a task.
 */
@Composable
internal fun TaskView(state: TaskFormUiState, onEvent: (TaskFormEvent) -> Unit, modifier: Modifier = Modifier) {
    val d = state.draft
    ScreenScaffold(title = stringResource(R.string.task_view_title), onBack = { onEvent(TaskFormEvent.Close) }) { padding ->
        Column(
            modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .testTag("fl_task_view"),
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    d.title, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }.testTag("fl_task_view_name"),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
                    Icon(
                        if (state.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        Modifier.padding(end = 8.dp),
                        tint = if (state.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(if (state.isDone) R.string.task_view_done else R.string.task_view_open),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            InfoRow(Icons.Rounded.Person, stringResource(R.string.task_form_assignee), state.assigneeName ?: stringResource(R.string.task_form_nobody), "fl_task_view_assignee")
            InfoRow(
                Icons.Rounded.Event, stringResource(R.string.task_form_due),
                d.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    ?: stringResource(R.string.task_form_due_clear),
                "fl_task_view_due",
            )
            InfoRow(Icons.Rounded.MeetingRoom, stringResource(R.string.task_form_room), state.roomName ?: stringResource(R.string.task_form_no_room), "fl_task_view_room")
            state.pinPlace?.let { place ->
                InfoRow(
                    Icons.Rounded.PushPin, stringResource(R.string.task_form_pin),
                    if (place.second == null) place.first else stringResource(R.string.task_form_pin_place, place.first, place.second.orEmpty()),
                    "fl_task_view_pin",
                )
            }
            d.filePin?.let { pin ->
                InfoRow(
                    Icons.Rounded.Description, stringResource(R.string.task_form_file_pin),
                    stringResource(R.string.task_form_file_pin_place, state.fileName ?: stringResource(R.string.file_viewer_title), pin.page + 1),
                    "fl_task_view_file_pin",
                )
            }
            if (d.punch) InfoRow(Icons.Rounded.Checklist, stringResource(R.string.tasks_punch), stringResource(R.string.task_form_punch_help), "fl_task_view_punch")
            if (d.note.isNotBlank()) InfoRow(Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.task_form_note), d.note, "fl_task_view_note")
            state.photo?.takeIf { it.path.isNotEmpty() }?.let { p ->
                AsyncImage(
                    File(p.path), stringResource(R.string.task_form_photo),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 640.dp).fillMaxWidth().aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp)).testTag("fl_task_view_photo"),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(Modifier.padding(16.dp).widthIn(max = 640.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, Modifier.padding(end = 16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.task_view_read_only), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("fl_task_view_read_only"),
                )
            }
        }
    }
}

/** Label over value with a leading icon; one TalkBack stop ("Who does it, Marcus Bell"). */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, tag: String) {
    Row(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, Modifier.padding(end = 16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
