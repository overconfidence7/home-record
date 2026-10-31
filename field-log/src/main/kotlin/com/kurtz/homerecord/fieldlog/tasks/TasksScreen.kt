package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
 * check box or a swipe ticks a task done and the snackbar offers Undo; managers drag the handle to reorder. Owner, Office and the job's Lead get the Add task FAB and
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
                else -> TaskLists(state, onEvent)
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

/**
 * Open tasks then Done. Managers drag an open task by its handle to reorder it (or use Move up / Move down from
 * TalkBack); the new order is saved when the drag ends. Swiping an open task either way ticks it done (AC-F34).
 */
@Composable
private fun TaskLists(state: TasksUiState, onEvent: (TasksEvent) -> Unit) {
    // The open list while a drag is under way; the saved order replaces it whenever the tasks change.
    var open by remember(state.open) { mutableStateOf(state.open) }
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = open.indexOfFirst { it.id == from.key }
        val toIndex = open.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            open = open.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("fl_tasks_list"), state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
        itemsIndexed(open, key = { _, row -> row.id }) { index, row ->
            ReorderableItem(reorder, key = row.id, enabled = state.canReorder) { dragging ->
                val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, label = "drag")
                val moves = if (state.canReorder) moveActions(row.id, index, open.lastIndex, onEvent) else emptyList()
                SwipeToDone(row, onEvent) {
                    TaskListRow(
                        row, state.canManage, onEvent,
                        handle = if (state.canReorder) {
                            {
                                DragHandle(
                                    row.title,
                                    Modifier.draggableHandle(
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                        onDragStopped = {
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            val to = open.indexOfFirst { it.id == row.id }
                                            if (to >= 0) onEvent(TasksEvent.Move(row.id, to))
                                        },
                                    ),
                                )
                            }
                        } else null,
                        modifier = Modifier.shadow(elevation).semantics { if (moves.isNotEmpty()) customActions = moves },
                    )
                }
            }
        }
        if (state.done.isNotEmpty()) {
            item(key = "done-header") {
                SectionHeader(pluralStringResource(R.plurals.tasks_done_header, state.done.size, state.done.size), Modifier.animateItem())
            }
            items(state.done, key = { it.id }) { row -> TaskListRow(row, state.canManage, onEvent, modifier = Modifier.animateItem()) }
        }
    }
}

/** Move up / Move down for TalkBack and switch access, since a drag needs a finger (rule: every gesture has a button). */
@Composable
private fun moveActions(id: String, index: Int, last: Int, onEvent: (TasksEvent) -> Unit): List<CustomAccessibilityAction> {
    val up = stringResource(R.string.tasks_move_up)
    val down = stringResource(R.string.tasks_move_down)
    return buildList {
        if (index > 0) add(CustomAccessibilityAction(up) { onEvent(TasksEvent.Move(id, index - 1)); true })
        if (index < last) add(CustomAccessibilityAction(down) { onEvent(TasksEvent.Move(id, index + 1)); true })
    }
}

/** An open task the person may tick swipes either way to done; the check box does the same (AC-F34). */
@Composable
private fun SwipeToDone(row: TaskRow, onEvent: (TasksEvent) -> Unit, content: @Composable () -> Unit) {
    if (!row.canTick || row.done) { content(); return }
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {
            val toEnd = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 24.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Done, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        onDismiss = {
            onEvent(TasksEvent.SetDone(row.id, true))
            scope.launch { swipe.reset() }
        },
        modifier = Modifier.testTag("fl_tasks_swipe_${row.id}"),
    ) { content() }
}

@Composable
private fun DragHandle(title: String, modifier: Modifier) {
    val label = stringResource(R.string.tasks_reorder, title)
    Box(modifier.size(48.dp).semantics { contentDescription = label }.testTag("fl_tasks_handle"), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.DragHandle, contentDescription = null)
    }
}

@Composable
private fun TaskListRow(
    row: TaskRow,
    canManage: Boolean,
    onEvent: (TasksEvent) -> Unit,
    handle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
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
        trailing = handle ?: if (canManage) { { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) } } else null,
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
