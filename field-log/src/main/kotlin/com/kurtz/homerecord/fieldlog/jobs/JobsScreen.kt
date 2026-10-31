package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.fieldlog.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** FL-Jobs. Jobs grouped Active / Upcoming / Done with client, address and next date (AC-F6). */
@Composable
fun JobsScreen(state: JobsUiState, onEvent: (JobsEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.jobs_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
        )
        when {
            state.loading -> LoadingState(Modifier.weight(1f))
            state.groups.isEmpty && !state.noMatches && state.query.isBlank() -> JobsEmpty(Modifier.weight(1f))
            else -> JobsList(state, onEvent, Modifier.weight(1f))
        }
    }
    if (state.canAddJob) {
        val newJobLabel = stringResource(R.string.jobs_new)
        ExtendedFloatingActionButton(
            onClick = { onEvent(JobsEvent.New) },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(newJobLabel) },
            // The clickable container carries the label itself so TalkBack and uiautomator name it.
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 56.dp)
                .semantics { contentDescription = newJobLabel }.testTag("fl_jobs_new"),
        )
    }
    }
}

@Composable
private fun JobsEmpty(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Work, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.jobs_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(stringResource(R.string.jobs_empty_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun JobsList(state: JobsUiState, onEvent: (JobsEvent) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.testTag("fl_jobs_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(JobsEvent.Query(it)) },
                label = { Text(stringResource(R.string.jobs_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("fl_jobs_search"),
            )
        }
        if (state.noMatches) {
            item {
                Text(
                    stringResource(R.string.jobs_no_matches, state.query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).testTag("fl_jobs_no_matches"),
                )
            }
        }
        section(R.string.jobs_group_active, state.groups.active, "active", onEvent)
        section(R.string.jobs_group_upcoming, state.groups.upcoming, "upcoming", onEvent)
        section(R.string.jobs_group_done, state.groups.done, "done", onEvent)
        item {
            FilterChip(
                selected = state.showArchived,
                onClick = { onEvent(JobsEvent.ToggleArchived) },
                label = { Text(stringResource(R.string.jobs_show_archived)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("fl_jobs_archived"),
            )
        }
        section(R.string.jobs_group_archived, state.groups.archived, "archived", onEvent)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    rows: List<JobRow>,
    key: String,
    onEvent: (JobsEvent) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "h-$key", contentType = "header") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        )
    }
    items(rows, key = { it.summary.id }, contentType = { "job" }) { row ->
        RecordListItem(
            headline = row.summary.name,
            supporting = row.supporting,
            trailing = row.nextDay?.let { day -> { Text(shortDate(day), style = MaterialTheme.typography.labelLarge) } },
            onClick = { onEvent(JobsEvent.Open(row.summary.id)) },
            modifier = Modifier.testTag("fl_jobs_row_${row.summary.id}"),
        )
    }
}

@Composable
private fun shortDate(iso: String): String = remember(iso) {
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)
}
