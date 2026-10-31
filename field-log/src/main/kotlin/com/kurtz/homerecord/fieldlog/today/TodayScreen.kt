package com.kurtz.homerecord.fieldlog.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import com.kurtz.homerecord.core.model.TaskDue
import com.kurtz.homerecord.fieldlog.tasks.SwipeToComplete
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.model.OnSiteEntry
import com.kurtz.homerecord.core.punch.ClockStatus
import com.kurtz.homerecord.core.sync.RefusedChange
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SyncStatus
import com.kurtz.homerecord.fieldlog.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** My clock status for the card at the top of Today. [now] drives the elapsed time. */
data class TodayClock(val status: ClockStatus, val jobName: String?, val now: Long) {
    val startedAt: Long? get() = when (status) {
        is ClockStatus.Working -> status.shiftStartAt
        is ClockStatus.OnBreak -> status.shiftStartAt
        ClockStatus.Out -> null
    }
}

data class TodayJob(val id: String, val name: String, val address: String?)

/** One of my open tasks due today or overdue, with the job it belongs to (AC-F34). */
data class TodayTask(
    val id: String,
    val jobId: String,
    val title: String,
    val jobName: String?,
    val due: java.time.LocalDate?,
    val dueKind: com.kurtz.homerecord.core.model.TaskDue,
)

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
    /** My tasks: overdue first, then due today (AC-F34). The section is left out when nothing is due. */
    val tasks: List<TodayTask> = emptyList(),
    /** A task just ticked done from Today; the snackbar offers Undo (AC-F34). */
    val taskUndo: com.kurtz.homerecord.fieldlog.tasks.TaskUndo? = null,
)

/**
 * FL-Today: clock status card, my jobs today, and for Owner and Office who is on site now (AC-F16). Tasks and photos
 * arrive with later tasks; before the company has a job it explains what to do first (AC-F1).
 */
/** Everything a tap on Today asks for; the route turns navigation into back-stack pushes (cr:fl-screen-signature). */
sealed interface TodayEvent {
    data object InviteCrew : TodayEvent
    data object SyncNow : TodayEvent
    data object OpenClock : TodayEvent
    data class OpenJob(val jobId: String) : TodayEvent
    data object AddJob : TodayEvent
    data object Refresh : TodayEvent
    data object ClearRefused : TodayEvent
    /** Open the member's last punch location in a maps app (AC-F16). */
    data class OpenPin(val entry: OnSiteEntry) : TodayEvent
    /** Tick one of my tasks done (check box or swipe); the snackbar offers Undo (AC-F34). */
    data class CompleteTask(val taskId: String) : TodayEvent
    data object UndoTask : TodayEvent
    data object DismissTaskUndo : TodayEvent
    /** Open a task: its job's Tasks tab, and the task form for those who manage it (AC-F34). */
    data class OpenTask(val jobId: String, val taskId: String) : TodayEvent
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onEvent: (TodayEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val undoMessage = state.taskUndo?.let { stringResource(R.string.tasks_done_snackbar, it.title) }
    val undoLabel = stringResource(R.string.tasks_undo)
    LaunchedEffect(state.taskUndo) {
        if (undoMessage == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(undoMessage, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
        onEvent(if (result == SnackbarResult.ActionPerformed) TodayEvent.UndoTask else TodayEvent.DismissTaskUndo)
    }
    Box(modifier.fillMaxSize()) {
        TodayColumn(state, onEvent)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).testTag("fl_today_snackbar"))
    }
}

@Composable
private fun TodayColumn(state: TodayUiState, onEvent: (TodayEvent) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SyncStatus(
            state.syncPhase, state.pendingChanges, state.refusedChanges,
            { onEvent(TodayEvent.SyncNow) }, { onEvent(TodayEvent.ClearRefused) }, Modifier.padding(horizontal = 16.dp),
        )
        // Pull down syncs and reloads the team, so "On site now" is current (AC-F16).
        PullToRefreshBox(state.refreshing, { onEvent(TodayEvent.Refresh) }, Modifier.weight(1f).fillMaxWidth().testTag("fl_today_refresh")) {
            if (state.clock != null && state.hasJobs) TodayContent(state, state.clock, onEvent, Modifier.fillMaxSize())
            else TodayBody(state, onEvent, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TodayContent(state: TodayUiState, clock: TodayClock, onEvent: (TodayEvent) -> Unit, modifier: Modifier) {
    val inset = Modifier.padding(horizontal = 16.dp)
    // Jobs and people are list rows edge to edge (rules 7 and 8); the clock status is the one card (cr:fl-card-lists).
    LazyColumn(
        modifier.testTag("fl_today_list"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { ClockCard(clock, { onEvent(TodayEvent.OpenClock) }, inset) }
        item { SectionHeading(stringResource(R.string.today_jobs)) }
        if (state.jobs.isEmpty()) item { Text(stringResource(R.string.today_no_jobs), inset.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium) }
        items(state.jobs, key = { "job-${it.id}" }) { job ->
            RecordListItem(
                headline = job.name,
                supporting = job.address,
                leading = { Icon(Icons.Rounded.Work, contentDescription = null) },
                onClick = { onEvent(TodayEvent.OpenJob(job.id)) },
                modifier = Modifier.fillMaxWidth().testTag("fl_today_job_${job.id}"),
            )
        }
        if (state.tasks.isNotEmpty()) {
            item(key = "tasks-heading") { SectionHeading(stringResource(R.string.today_tasks)) }
            items(state.tasks, key = { "task-${it.id}" }) { task ->
                SwipeToComplete(
                    onComplete = { onEvent(TodayEvent.CompleteTask(task.id)) },
                    modifier = Modifier.animateItem().testTag("fl_today_task_swipe_${task.id}"),
                ) { TodayTaskRow(task, onEvent) }
            }
        }
        if (state.role.isAdmin) {
            item { SectionHeading(stringResource(R.string.today_on_site)) }
            if (state.onSite.isEmpty()) item { Text(stringResource(R.string.today_on_site_empty), inset.padding(vertical = 8.dp).testTag("fl_today_on_site_empty"), style = MaterialTheme.typography.bodyMedium) }
            items(state.onSite, key = { "site-${it.memberId}" }) { row -> OnSiteRow(row) { onEvent(TodayEvent.OpenPin(row)) } }
        }
    }
}

@Composable
private fun TodayTaskRow(task: TodayTask, onEvent: (TodayEvent) -> Unit) {
    val checkLabel = stringResource(R.string.tasks_mark_done, task.title)
    val due = when (task.dueKind) {
        TaskDue.OVERDUE -> task.due?.let { stringResource(R.string.tasks_due_overdue, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
        TaskDue.TODAY -> stringResource(R.string.tasks_due_today)
        else -> null
    }
    RecordListItem(
        headline = task.title,
        supporting = listOfNotNull(task.jobName, due).joinToString(" · ").takeIf { it.isNotEmpty() },
        supportingMaxLines = 4,
        headlineMaxLines = 6,
        leading = {
            Checkbox(
                checked = false,
                onCheckedChange = { if (it) onEvent(TodayEvent.CompleteTask(task.id)) },
                modifier = Modifier.semantics { contentDescription = checkLabel }.testTag("fl_today_task_check_${task.id}"),
            )
        },
        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        onClick = { onEvent(TodayEvent.OpenTask(task.jobId, task.id)) },
        modifier = Modifier.fillMaxWidth().testTag("fl_today_task_${task.id}"),
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ClockCard(clock: TodayClock, onOpenClock: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth().testTag("fl_today_clock_card")) {
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
private fun OnSiteRow(row: OnSiteEntry, onPin: () -> Unit) {
    val where = row.jobName ?: stringResource(R.string.today_no_job)
    val line = if (row.onBreak) stringResource(R.string.today_on_site_break, where, timeText(row.since)) else stringResource(R.string.today_on_site_line, where, timeText(row.since))
    RecordListItem(
        headline = row.name.ifBlank { stringResource(R.string.today_unknown_member) },
        supporting = line,
        leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
        trailing = if (row.hasPin) {
            {
                IconButton(onClick = onPin, Modifier.size(48.dp).testTag("fl_today_pin_${row.memberId}")) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = stringResource(R.string.today_pin_description, row.name))
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth().testTag("fl_today_site_${row.memberId}"),
    )
}

private fun timeText(ms: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private fun elapsedText(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return "%d:%02d".format(minutes / 60, minutes % 60)
}

@Composable
private fun TodayBody(state: TodayUiState, onEvent: (TodayEvent) -> Unit, modifier: Modifier) {
    if (state.role.isAdmin) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                icon = Icons.Rounded.Work,
                headline = stringResource(R.string.today_empty_title, state.companyName),
                body = stringResource(R.string.today_empty_admin),
                actionLabel = stringResource(R.string.today_add_job),
                onAction = { onEvent(TodayEvent.AddJob) },
                modifier = Modifier.weight(1f),
                actionModifier = Modifier.heightIn(min = 56.dp).testTag("fl_today_add_job"),
            )
            TextButton(onClick = { onEvent(TodayEvent.InviteCrew) }, Modifier.heightIn(min = 56.dp).padding(bottom = 16.dp).testTag("fl_today_invite_crew")) {
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
