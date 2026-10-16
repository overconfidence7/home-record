package com.kurtz.homerecord.fieldlog.job

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.EntryGrouper
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.PhotoTile
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import java.io.File

@Composable
fun JobRoute(
    viewModel: JobViewModel,
    onEdit: () -> Unit,
    onCapture: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onBehindWall: () -> Unit = {},
    onReport: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    JobScreen(
        state, onEdit, onCapture, onOpenEntry,
        onBehindWall = onBehindWall,
        onReport = onReport,
        onTogglePlay = viewModel::togglePlay,
        fileFor = viewModel::file,
    )
}

/** FL-Job: header, actions and entries grouped by day (AC-61). Report/handoff actions arrive in T24+. */
@Composable
fun JobScreen(
    state: JobUiState,
    onEdit: () -> Unit,
    onCapture: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onTogglePlay: (String, String?) -> Unit = { _, _ -> },
    fileFor: (String?) -> File? = { null },
    onBehindWall: () -> Unit = {},
    onReport: () -> Unit = {},
) {
    val job = state.job
    if (job == null) {
        if (state.loaded) Text(stringResource(R.string.job_missing), Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                Text(job.name, style = MaterialTheme.typography.headlineMedium)
                job.address?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Button(onClick = onCapture, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.job_capture))
                }
                OutlinedButton(onClick = onBehindWall, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.job_behind_wall))
                }
                OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.job_report))
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.job_edit))
                }
                if (state.days.isEmpty()) Text(stringResource(R.string.job_no_entries), style = MaterialTheme.typography.bodyLarge)
            }
        }
        state.days.forEach { group ->
            item(key = "h-${group.day}") {
                Text(group.header, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            items(group.entries, key = { it.id }) { entry ->
                EntryRow(entry, state.playingId == entry.id, onOpenEntry, onTogglePlay, fileFor)
            }
        }
        item { Column(Modifier.padding(bottom = 16.dp)) {} }
    }
}

@Composable
private fun EntryRow(
    entry: LogEntry,
    playing: Boolean,
    onOpen: (String) -> Unit,
    onTogglePlay: (String, String?) -> Unit,
    fileFor: (String?) -> File?,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(entry.id) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (entry.kind) {
            EntryKind.PHOTO -> {
                PhotoTile(fileFor(entry.mediaPath), stringResource(R.string.entry_photo), size = 72.dp)
                Text(entry.text ?: stringResource(R.string.entry_photo), style = MaterialTheme.typography.bodyLarge)
            }
            EntryKind.NOTE -> Text(entry.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 4, modifier = Modifier.weight(1f))
            EntryKind.VOICE -> {
                Text(
                    stringResource(R.string.entry_voice_duration, EntryGrouper.formatDuration(entry.durationMs ?: 0L)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onTogglePlay(entry.id, entry.mediaPath) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(if (playing) R.string.entry_stop else R.string.entry_play))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JobScreenPreview() {
    HomeRecordTheme {
        JobScreen(
            JobUiState(true, Job("1", "h", "Elm St", "1 Elm", null, null, false, 0, 0, Source.FIELD_LOG)),
            onEdit = {}, onCapture = {}, onOpenEntry = {},
        )
    }
}
