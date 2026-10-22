package com.kurtz.homerecord.fieldlog.jobs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** FL-Job Overview tab: where, when, who, and three totals that each open their tab (AC-F7). */
@Composable
fun JobOverviewScreen(state: JobOverviewUiState, onEvent: (JobOverviewEvent) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> LoadingState(modifier)
        state.notFound -> Text(stringResource(R.string.job_not_found), modifier.padding(24.dp).testTag("fl_job_not_found"))
        else -> Column(
            modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("fl_job_overview"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Facts(state, onEvent)
            Stat(stringResource(R.string.job_ov_hours), stringResource(R.string.job_ov_hours_value, formatHours(state.hoursThisWeek)), "hours") { onEvent(JobOverviewEvent.OpenTab(JobTab.TIME)) }
            Stat(stringResource(R.string.job_ov_photos), state.photoCount.toString(), "photos") { onEvent(JobOverviewEvent.OpenTab(JobTab.PHOTOS)) }
            Stat(stringResource(R.string.job_ov_tasks), state.openTasks.toString(), "tasks") { onEvent(JobOverviewEvent.OpenTab(JobTab.TASKS)) }
            Section(stringResource(R.string.job_ov_next)) {
                Text(state.nextDay?.let(::shortDate) ?: stringResource(R.string.job_ov_none_scheduled), Modifier.testTag("fl_job_next_day"))
            }
            Section(stringResource(R.string.job_ov_crew)) {
                Text(
                    if (state.crew.isEmpty()) stringResource(R.string.job_ov_no_crew) else state.crew.joinToString(", "),
                    Modifier.testTag("fl_job_crew"),
                )
            }
            OutlinedButton(
                onClick = { onEvent(JobOverviewEvent.OpenEdit) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_job_edit"),
            ) { Text(stringResource(R.string.job_edit)) }
        }
    }
}

@Composable
private fun Facts(state: JobOverviewUiState, onEvent: (JobOverviewEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(statusLabel(state.status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("fl_job_status"))
        state.address?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("fl_job_address")) }
        val lat = state.lat
        val lng = state.lng
        if (lat != null && lng != null) {
            val context = LocalContext.current
            OutlinedButton(
                onClick = {
                    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(state.name)})")
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_job_map"),
            ) { Text(stringResource(R.string.job_map_link)) }
        }
        val dates = listOfNotNull(state.startDate, state.endDate).map(::shortDate)
        if (dates.isNotEmpty()) Text(stringResource(R.string.job_ov_dates) + ": " + dates.joinToString(" to "), Modifier.testTag("fl_job_dates"))
        if (state.phases.isNotEmpty()) Text(stringResource(R.string.job_ov_phases) + ": " + state.phases.joinToString(", "), Modifier.testTag("fl_job_phases"))
    }
}

@Composable
private fun Stat(label: String, value: String, key: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("fl_job_stat_$key")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).semantics { role = Role.Button }.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun statusLabel(status: JobStatus): String = stringResource(
    when (status) {
        JobStatus.ACTIVE -> R.string.job_status_active
        JobStatus.UPCOMING -> R.string.job_status_upcoming
        JobStatus.DONE -> R.string.job_status_done
        JobStatus.ARCHIVED -> R.string.job_status_archived
    },
)

internal fun formatHours(hours: Double): String = if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()

private fun shortDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)
