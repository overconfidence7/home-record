package com.kurtz.homerecord.fieldlog.jobs

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.JobSummary
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun JobsRoute(viewModel: JobsViewModel, onNewJob: () -> Unit, onOpenJob: (String) -> Unit, onSettings: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    JobsScreen(state) { event ->
        when (event) {
            JobsEvent.NewJob -> onNewJob()
            JobsEvent.Settings -> onSettings()
            is JobsEvent.Open -> onOpenJob(event.jobId)
            is JobsEvent.ShowArchived -> viewModel.onEvent(event)
        }
    }
}

@Composable
fun JobsScreen(state: JobsUiState, onEvent: (JobsEvent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.jobs_title), style = MaterialTheme.typography.headlineMedium)
        Button(
            onClick = { onEvent(JobsEvent.NewJob) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("jobs_new"),
        ) { Text(stringResource(R.string.jobs_new)) }
        OutlinedButton(
            onClick = { onEvent(JobsEvent.Settings) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("jobs_settings"),
        ) { Text(stringResource(R.string.jobs_settings)) }
        if (state.archivedCount > 0) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.jobs_show_archived))
                Switch(
                    checked = state.showArchived,
                    onCheckedChange = { onEvent(JobsEvent.ShowArchived(it)) },
                    modifier = Modifier.testTag("jobs_show_archived"),
                )
            }
        }
        if (state.loaded && state.jobs.isEmpty()) {
            Text(
                stringResource(if (state.archivedCount > 0 && !state.showArchived) R.string.jobs_empty_active else R.string.jobs_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.jobs, key = { it.job.id }) { summary ->
                JobRow(summary) { onEvent(JobsEvent.Open(summary.job.id)) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun JobRow(summary: JobSummary, onClick: () -> Unit) {
    val job = summary.job
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 12.dp).testTag("job_row"),
    ) {
        Text(job.name, style = MaterialTheme.typography.titleMedium)
        val detail = listOfNotNull(job.clientName, job.address).joinToString(" · ")
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium)
        if (job.archived) Text(stringResource(R.string.jobs_archived_tag), style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun JobsScreenPreview() {
    val job = Job("1", "h", "Elm St Remodel", "1 Elm St", "Pat", null, false, 0, 0, Source.FIELD_LOG)
    HomeRecordTheme { JobsScreen(JobsUiState(true, listOf(JobSummary(job, 0, 0))), onEvent = {}) }
}
