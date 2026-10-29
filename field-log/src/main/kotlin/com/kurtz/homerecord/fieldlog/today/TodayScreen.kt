package com.kurtz.homerecord.fieldlog.today

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kurtz.homerecord.core.model.OnSiteEntry
import com.kurtz.homerecord.core.punch.ClockStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.sync.RefusedChange
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.SyncStatus
import com.kurtz.homerecord.fieldlog.R

/** My clock status for the card at the top of Today. [now] drives the elapsed time. */
data class TodayClock(val status: ClockStatus, val jobName: String?, val now: Long) {
    val startedAt: Long? get() = when (status) {
        is ClockStatus.Working -> status.shiftStartAt
        is ClockStatus.OnBreak -> status.shiftStartAt
        ClockStatus.Out -> null
    }
}

data class TodayJob(val id: String, val name: String, val address: String?)

data class TodayUiState(
    val companyName: String,
    val role: CompanyRole,
    val syncPhase: SyncPhase = SyncPhase.Idle,
    val pendingChanges: Int = 0,
    /** Changes the server refused, listed from the sync chip (cr:sync-poison-op). */
    val refusedChanges: List<RefusedChange> = emptyList(),
    /** The company has at least one active job; until then Today explains what to do first (AC-F1). */
    val hasJobs: Boolean = false,
    val clock: TodayClock? = null,
    val jobs: List<TodayJob> = emptyList(),
    /** Owner and Office only (AC-F16). */
    val onSite: List<OnSiteEntry> = emptyList(),
    /** A pull to refresh is running (AC-F16). */
    val refreshing: Boolean = false,
)

/**
 * FL-Today: clock status card, my jobs today, and for Owner and Office who is on site now (AC-F16). Tasks and photos
 * arrive with later tasks; before the company has a job it explains what to do first (AC-F1).
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onInviteCrew: () -> Unit,
    modifier: Modifier = Modifier,
    onSyncNow: () -> Unit = {},
    onOpenClock: () -> Unit = {},
    onOpenJob: (String) -> Unit = {},
    onAddJob: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearRefused: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        SyncStatus(state.syncPhase, state.pendingChanges, state.refusedChanges, onSyncNow, onClearRefused, Modifier.padding(horizontal = 16.dp))
        // Pull down syncs and reloads the team, so "On site now" is current (AC-F16).
        PullToRefreshBox(state.refreshing, onRefresh, Modifier.weight(1f).fillMaxWidth().testTag("fl_today_refresh")) {
            if (state.clock != null && state.hasJobs) TodayContent(state, state.clock, onOpenClock, onOpenJob, Modifier.fillMaxSize())
            else TodayBody(state, onInviteCrew, onAddJob, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TodayContent(state: TodayUiState, clock: TodayClock, onOpenClock: () -> Unit, onOpenJob: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(
        modifier.testTag("fl_today_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ClockCard(clock, onOpenClock) }
        item { Text(stringResource(R.string.today_jobs), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        if (state.jobs.isEmpty()) item { Text(stringResource(R.string.today_no_jobs), style = MaterialTheme.typography.bodyMedium) }
        items(state.jobs, key = { "job-${it.id}" }) { job ->
            Card(Modifier.fillMaxWidth().clickable { onOpenJob(job.id) }.testTag("fl_today_job_${job.id}")) {
                Column(Modifier.heightIn(min = 56.dp).padding(16.dp), verticalArrangement = Arrangement.Center) {
                    Text(job.name, style = MaterialTheme.typography.titleSmall)
                    job.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (state.role.isAdmin) {
            item { Text(stringResource(R.string.today_on_site), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
            if (state.onSite.isEmpty()) item { Text(stringResource(R.string.today_on_site_empty), Modifier.testTag("fl_today_on_site_empty"), style = MaterialTheme.typography.bodyMedium) }
            items(state.onSite, key = { "site-${it.memberId}" }) { row -> OnSiteRow(row, clock.now) { context.openPin(row) } }
        }
    }
}

@Composable
private fun ClockCard(clock: TodayClock, onOpenClock: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("fl_today_clock_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val headline = when (clock.status) {
                ClockStatus.Out -> R.string.clock_out_status
                is ClockStatus.Working -> R.string.clock_working_status
                is ClockStatus.OnBreak -> R.string.clock_break_status
            }
            Text(stringResource(headline), style = MaterialTheme.typography.titleLarge)
            clock.jobName?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            clock.startedAt?.let { Text(stringResource(R.string.today_since, timeText(it), elapsedText(clock.now - it)), Modifier.testTag("fl_today_elapsed"), style = MaterialTheme.typography.bodyMedium) }
            val action = if (clock.status == ClockStatus.Out) R.string.clock_in else R.string.today_open_clock
            Button(onClick = onOpenClock, Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("fl_today_clock_action")) { Text(stringResource(action)) }
        }
    }
}

@Composable
private fun OnSiteRow(row: OnSiteEntry, now: Long, onPin: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("fl_today_site_${row.memberId}")) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.name.ifBlank { stringResource(R.string.today_unknown_member) }, style = MaterialTheme.typography.titleSmall)
                val where = row.jobName ?: stringResource(R.string.today_no_job)
                val line = if (row.onBreak) stringResource(R.string.today_on_site_break, where, timeText(row.since)) else stringResource(R.string.today_on_site_line, where, timeText(row.since))
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.hasPin) IconButton(onClick = onPin, Modifier.size(48.dp).testTag("fl_today_pin_${row.memberId}")) {
                Icon(Icons.Rounded.LocationOn, contentDescription = stringResource(R.string.today_pin_description, row.name))
            }
        }
    }
}

private fun Context.openPin(row: OnSiteEntry) {
    val uri = Uri.parse("geo:${row.lat},${row.lng}?q=${row.lat},${row.lng}(${Uri.encode(row.name)})")
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun timeText(ms: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private fun elapsedText(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return "%d:%02d".format(minutes / 60, minutes % 60)
}

@Composable
private fun TodayBody(state: TodayUiState, onInviteCrew: () -> Unit, onAddJob: () -> Unit, modifier: Modifier) {
    if (state.role.isAdmin) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                icon = Icons.Rounded.Work,
                headline = stringResource(R.string.today_empty_title, state.companyName),
                body = stringResource(R.string.today_empty_admin),
                actionLabel = stringResource(R.string.today_add_job),
                onAction = onAddJob,
                modifier = Modifier.weight(1f),
                actionModifier = Modifier.heightIn(min = 56.dp).testTag("fl_today_add_job"),
            )
            TextButton(onClick = onInviteCrew, Modifier.heightIn(min = 56.dp).padding(bottom = 16.dp).testTag("fl_today_invite_crew")) {
                Text(stringResource(R.string.today_invite_crew))
            }
        }
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Work, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.today_empty_title, state.companyName), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.today_empty_crew), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}
