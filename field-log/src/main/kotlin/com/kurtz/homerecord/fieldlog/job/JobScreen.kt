package com.kurtz.homerecord.fieldlog.job

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun JobRoute(viewModel: JobViewModel, onEdit: () -> Unit, onCapture: () -> Unit) {
    val state by viewModel.state.collectAsState()
    JobScreen(state, onEdit, onCapture)
}

/** FL-Job shell: header and edit link. Entries, capture and report actions arrive in T20+. */
@Composable
fun JobScreen(state: JobUiState, onEdit: () -> Unit, onCapture: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val job = state.job
        if (job == null) {
            if (state.loaded) Text(stringResource(R.string.job_missing))
            return@Column
        }
        Text(job.name, style = MaterialTheme.typography.headlineMedium)
        job.address?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.job_no_entries), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onCapture, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.job_capture))
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.job_edit))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JobScreenPreview() {
    HomeRecordTheme {
        JobScreen(JobUiState(true, Job("1", "h", "Elm St", "1 Elm", null, null, false, 0, 0, Source.FIELD_LOG)), onEdit = {}, onCapture = {})
    }
}
