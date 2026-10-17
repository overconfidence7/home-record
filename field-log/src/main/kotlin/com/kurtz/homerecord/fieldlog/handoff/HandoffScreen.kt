package com.kurtz.homerecord.fieldlog.handoff

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.logic.HandoffCounts
import com.kurtz.homerecord.fieldlog.R
import java.io.File

@Composable
fun HandoffRoute(viewModel: HandoffViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.ready) {
        state.ready?.let { share(context, it) }
        if (state.ready != null) viewModel.onEvent(HandoffEvent.Shared)
    }
    HandoffScreen(state, viewModel::onEvent)
}

private fun share(context: Context, ready: HandoffShare) {
    val file = File(context.cacheDir, ready.path)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = ready.format.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.handoff_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** FL-Handoff: checklist of what the close-out export includes (AC-71) and the two export actions. */
@Composable
fun HandoffScreen(state: HandoffUiState, onEvent: (HandoffEvent) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.handoff_title), style = MaterialTheme.typography.headlineMedium)
        Text(state.jobName, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.handoff_includes), style = MaterialTheme.typography.bodyLarge)
        val counts = state.counts
        if (counts != null) {
            CountRow(R.string.handoff_rooms, counts.rooms, "handoff_rooms")
            CountRow(R.string.handoff_walls, counts.walls, "handoff_walls")
            CountRow(R.string.handoff_behind_wall, counts.behindWallPhotos, "handoff_behind_wall")
            CountRow(R.string.handoff_equipment, counts.equipment, "handoff_equipment")
            CountRow(R.string.handoff_finishes, counts.finishes, "handoff_finishes")
            CountRow(R.string.handoff_shutoffs, counts.shutoffs, "handoff_shutoffs")
            CountRow(R.string.handoff_panels, counts.panels, "handoff_panels")
        } else if (state.loaded) {
            Text(stringResource(R.string.handoff_missing))
        }
        Button(
            onClick = { onEvent(HandoffEvent.ExportBundle) },
            enabled = counts != null && !state.exporting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("handoff_export_bundle"),
        ) { Text(stringResource(if (state.exporting) R.string.handoff_exporting else R.string.handoff_export_bundle)) }
        OutlinedButton(
            onClick = { onEvent(HandoffEvent.ExportPdf) },
            enabled = counts != null && !state.exporting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("handoff_export_pdf"),
        ) { Text(stringResource(R.string.handoff_export_pdf)) }
        if (state.failed) {
            Text(stringResource(R.string.handoff_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CountRow(label: Int, count: Int, tag: String) {
    Text(
        stringResource(R.string.handoff_row, stringResource(label), count),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
    )
}
