package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.TaskDue
import com.kurtz.homerecord.core.model.TaskFilter
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.fieldlog.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Tasks tab of FL-Job (FL-Tasks, AC-F34): All / Punch list chips, open tasks in the crew's order, then Done. The
 * check box ticks a task done and the snackbar offers Undo. Owner, Office and the job's Lead get the Add task FAB and
 * open a task to edit it; Crew see their own tasks and tick them (AC-F3).
 */
@Composable
fun TasksScreen(
    state: TasksUiState,
    onEvent: (TasksEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    if (state.loading) { LoadingState(modifier.fillMaxSize()); return }
    val undoMessage = state.undo?.let { stringResource(R.string.tasks_done_snackbar, it.title) }
    val undoLabel = stringResource(R.string.tasks_undo)
    LaunchedEffect(state.undo) {
        if (undoMessage == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(undoMessage, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
        onEvent(if (result == SnackbarResult.ActionPerformed) TasksEvent.Undo else TasksEvent.DismissUndo)
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.total > 0) FilterRow(state.filter, onEvent)
            when {
                state.total == 0 -> EmptyState(
                    icon = Icons.Rounded.Checklist,
                    headline = stringResource(if (state.canManage) R.string.tasks_empty_title else R.string.tasks_empty_crew_title),
                    body = stringResource(if (state.canManage) R.string.tasks_empty else R.string.tasks_empty_crew),
                    actionLabel = if (state.canManage) stringResource(R.string.tasks_add) else null,
                    onAction = { onEvent(TasksEvent.Add) },
                    modifier = Modifier.padding(bottom = 88.dp).testTag("fl_tasks_empty"),
                    actionModifier = Modifier.heightIn(min = 56.dp).testTag("fl_tasks_empty_add"),
                )
                state.isEmpty -> EmptyState(
                    icon = Icons.Rounded.FilterAltOff,
                    headline = stringResource(R.string.tasks_no_punch_title),
                    body = stringResource(R.string.tasks_no_punch),
                    actionLabel = stringResource(R.string.tasks_show_all),
                    onAction = { onEvent(TasksEvent.SetFilter(TaskFilter.ALL)) },
                    modifier = Modifier.padding(bottom = 88.dp).testTag("fl_tasks_no_punch"),
                    actionModifier = Modifier.heightIn(min = 48.dp).testTag("fl_tasks_show_all"),
                )
                else -> LazyColumn(Modifier.fillMaxSize().testTag("fl_tasks_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.open, key = { it.id }) { row -> TaskListRow(row, state.canManage, onEvent, Modifier.animateItem()) }
                    if (state.done.isNotEmpty()) {
                        item(key = "done-header") {
                            SectionHeader(pluralStringResource(R.plurals.tasks_done_header, state.done.size, state.done.size), Modifier.animateItem())
                        }
                        items(state.done, key = { it.id }) { row -> TaskListRow(row, state.canManage, onEvent, Modifier.animateItem()) }
                    }
                }
            }
        }
        if (state.canManage) {
            val label = stringResource(R.string.tasks_add)
            ExtendedFloatingActionButton(
                onClick = { onEvent(TasksEvent.Add) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(label) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 56.dp)
                    .semantics { contentDescription = label }.testTag("fl_tasks_add"),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.canManage) 80.dp else 0.dp).testTag("fl_tasks_snackbar"))
    }
}

@Composable
private fun FilterRow(filter: TaskFilter, onEvent: (TasksEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskFilter.entries.forEach { f ->
            FilterChip(
                selected = filter == f,
                onClick = { onEvent(TasksEvent.SetFilter(f)) },
                label = { Text(stringResource(if (f == TaskFilter.ALL) R.string.tasks_filter_all else R.string.tasks_filter_punch)) },
                modifier = Modifier.testTag("fl_tasks_filter_${f.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun TaskListRow(row: TaskRow, canManage: Boolean, onEvent: (TasksEvent) -> Unit, modifier: Modifier = Modifier) {
    val checkLabel = stringResource(if (row.done) R.string.tasks_mark_open else R.string.tasks_mark_done, row.title)
    RecordListItem(
        headline = row.title,
        supporting = supporting(row),
        supportingMaxLines = 8,
        leading = {
            Checkbox(
                checked = row.done,
                onCheckedChange = { onEvent(TasksEvent.SetDone(row.id, it)) },
                enabled = row.canTick,
                modifier = Modifier.semantics { contentDescription = checkLabel }.testTag("fl_tasks_check_${row.id}"),
            )
        },
        trailing = if (canManage) { { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) } } else null,
        onClick = if (canManage) { { onEvent(TasksEvent.Open(row.id)) } } else null,
        modifier = modifier.testTag("fl_tasks_row_${row.id}"),
    )
}

/** "Ana · Overdue, Oct 28 · Kitchen · Punch list", then the note on its own line. */
@Composable
private fun supporting(row: TaskRow): String? {
    val parts = buildList {
        row.assignee?.let(::add)
        row.due?.let { due ->
            val day = due.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            add(
                when (row.dueKind) {
                    TaskDue.OVERDUE -> stringResource(R.string.tasks_due_overdue, day)
                    TaskDue.TODAY -> stringResource(R.string.tasks_due_today)
                    else -> stringResource(R.string.tasks_due_on, day)
                },
            )
        }
        row.room?.let(::add)
        if (row.punch) add(stringResource(R.string.tasks_punch))
        if (row.hasPhoto) add(stringResource(R.string.tasks_has_photo))
    }
    val meta = parts.joinToString(" · ").takeIf { it.isNotEmpty() }
    return listOfNotNull(meta, row.note).joinToString("\n").takeIf { it.isNotEmpty() }
}
