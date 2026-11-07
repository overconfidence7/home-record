package com.kurtz.homerecord.fieldlog.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import com.kurtz.homerecord.core.model.ScheduleCell
import com.kurtz.homerecord.core.model.ScheduleRow
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.fieldlog.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayName = DateTimeFormatter.ofPattern("EEE")
private val dayShort = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dayLong = DateTimeFormatter.ofPattern("EEEE, MMM d")
private val monthDay = DateTimeFormatter.ofPattern("MMM d")

/**
 * FL-Schedule (AC-F41, AC-F43). Owner, Office and a Lead see the week as a strip of days and, for the chosen day, a row
 * per person with their job chips; Add puts someone on a job that day and a chip moves or comes off. Two jobs on one
 * day carry a warning badge. Crew and Sub see their own week, one row per day.
 */
@Composable
fun ScheduleScreen(state: ScheduleUiState, onEvent: (ScheduleEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.loading) { LoadingState(modifier.fillMaxSize()); return }
    if (state.loadFailed) {
        ErrorState(
            stringResource(R.string.schedule_load_failed), { onEvent(ScheduleEvent.Retry) }, modifier.fillMaxSize().statusBarsPadding(),
            retryModifier = Modifier.testTag("fl_schedule_retry"),
        )
        return
    }
    Column(modifier.fillMaxSize().statusBarsPadding().testTag("fl_schedule_week")) {
        WeekBar(state, onEvent)
        if (state.ownOnly) OwnWeek(state) else TeamDay(state, onEvent)
    }
    when (val sheet = state.sheet) {
        is ScheduleSheet.PickJob -> OptionsSheet(
            title = stringResource(R.string.schedule_pick_title, sheet.name, sheet.date.format(dayShort)),
            options = state.jobs.map { SheetOption("job_${it.id}", it.name, Icons.Rounded.Work) },
            onDismiss = { onEvent(ScheduleEvent.Dismiss) },
            onPick = { onEvent(ScheduleEvent.PickJob(it.id.removePrefix("job_"))) },
        )
        is ScheduleSheet.Day -> OptionsSheet(
            title = stringResource(R.string.schedule_day_title, sheet.name, sheet.job.jobName, sheet.date.format(dayShort)),
            options = state.days.filter { it != sheet.date }.map {
                SheetOption("move_$it", stringResource(R.string.schedule_move_to, it.format(dayShort)), Icons.Rounded.CalendarMonth)
            } + SheetOption("remove", stringResource(R.string.schedule_remove), Icons.Rounded.RemoveCircleOutline),
            onDismiss = { onEvent(ScheduleEvent.Dismiss) },
            onPick = { o ->
                if (o.id == "remove") onEvent(ScheduleEvent.Remove)
                else onEvent(ScheduleEvent.MoveTo(LocalDate.parse(o.id.removePrefix("move_"))))
            },
        )
        null -> Unit
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.WeekBar(state: ScheduleUiState, onEvent: (ScheduleEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onEvent(ScheduleEvent.PreviousWeek) }, modifier = Modifier.testTag("fl_schedule_prev")) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.schedule_week_previous))
        }
        Text(
            stringResource(R.string.schedule_week_of, state.weekStart.format(monthDay)),
            Modifier.weight(1f).testTag("fl_schedule_title"),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onEvent(ScheduleEvent.NextWeek) }, modifier = Modifier.testTag("fl_schedule_next")) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.schedule_week_next))
        }
    }
    if (!state.isThisWeek) {
        TextButton(
            onClick = { onEvent(ScheduleEvent.ThisWeek) },
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("fl_schedule_this_week"),
        ) {
            Icon(Icons.Rounded.Today, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.schedule_this_week))
        }
    }
}

@Composable
private fun DayStrip(state: ScheduleUiState, onEvent: (ScheduleEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.days.forEach { day ->
            val selected = day == state.selected
            val busy = state.busy(day)
            val conflict = state.conflict(day)
            val label = buildString {
                append(day.format(dayLong))
                if (day == state.today) append(", ").append(stringResource(R.string.schedule_today))
                if (conflict) append(", ").append(stringResource(R.string.schedule_conflict))
                else if (busy) append(", ").append(stringResource(R.string.schedule_day_busy))
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 64.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onEvent(ScheduleEvent.SelectDay(day)) })
                    .semantics(mergeDescendants = true) { contentDescription = label; this.selected = selected }
                    .testTag("fl_schedule_day_$day"),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.format(dayName), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (day == state.today) FontWeight.Bold else null,
                        color = if (day == state.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    when {
                        conflict -> Icon(Icons.Rounded.Warning, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        busy -> Box(Modifier.padding(4.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        else -> Spacer(Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamDay(state: ScheduleUiState, onEvent: (ScheduleEvent) -> Unit) {
    DayStrip(state, onEvent)
    if (state.jobs.isEmpty() && state.rows.none { r -> r.cells.any { it.jobs.isNotEmpty() } }) {
        EmptyState(
            icon = Icons.Rounded.CalendarMonth,
            headline = stringResource(R.string.schedule_no_jobs_title),
            body = stringResource(R.string.schedule_no_jobs_body),
            actionLabel = null, onAction = {},
            modifier = Modifier.fillMaxSize().testTag("fl_schedule_empty"),
        )
        return
    }
    Text(
        state.selected.format(dayLong),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).testTag("fl_schedule_day_title"),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyColumn(Modifier.fillMaxSize().testTag("fl_schedule_list"), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(state.rows, key = { it.memberId }) { row ->
            val cell = row.cells.firstOrNull { it.date == state.selected } ?: ScheduleCell(state.selected, emptyList())
            PersonDay(row, cell, state, onEvent)
            HorizontalDivider()
        }
    }
}

@Composable
private fun PersonDay(row: ScheduleRow, cell: ScheduleCell, state: ScheduleUiState, onEvent: (ScheduleEvent) -> Unit) {
    ListItem(
        headlineContent = { Text(row.name, maxLines = 2) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cell.jobs.isEmpty()) Text(stringResource(R.string.schedule_not_scheduled))
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cell.jobs.forEach { job ->
                        val tag = "fl_schedule_chip_${row.memberId}_${job.jobId}"
                        if (state.canChange(job.jobId)) {
                            AssistChip(
                                onClick = { onEvent(ScheduleEvent.OpenDay(row.memberId, job.assignmentId)) },
                                label = { Text(job.jobName) },
                                leadingIcon = { Icon(Icons.Rounded.Work, contentDescription = null, Modifier.size(18.dp)) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
                            )
                        } else {
                            Text(job.jobName, Modifier.padding(vertical = 4.dp).testTag(tag), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (cell.conflict) ConflictLine(Modifier.testTag("fl_schedule_conflict_${row.memberId}"))
            }
        },
        leadingContent = { Icon(Icons.Rounded.Person, contentDescription = null) },
        trailingContent = if (state.canAssign) ({
            val cd = stringResource(R.string.schedule_assign, row.name, state.selected.format(dayShort))
            FilledTonalIconButton(
                onClick = { onEvent(ScheduleEvent.Assign(row.memberId)) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = cd }.testTag("fl_schedule_assign_${row.memberId}"),
            ) { Icon(Icons.Rounded.Add, contentDescription = null) }
        }) else null,
        modifier = Modifier.testTag("fl_schedule_row_${row.memberId}"),
    )
}

@Composable
private fun ConflictLine(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.schedule_conflict), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
    }
}

/** Crew and Sub: their own seven days, today marked (AC-F41). */
@Composable
private fun OwnWeek(state: ScheduleUiState) {
    val row = state.rows.firstOrNull()
    if (row == null || row.cells.all { it.jobs.isEmpty() }) {
        EmptyState(
            icon = Icons.Rounded.EventBusy,
            headline = stringResource(R.string.schedule_own_empty_title),
            body = stringResource(R.string.schedule_own_empty_body),
            actionLabel = null, onAction = {},
            modifier = Modifier.fillMaxSize().testTag("fl_schedule_empty"),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("fl_schedule_list"), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(row.cells, key = { it.date.toString() }) { cell ->
            val today = cell.date == state.today
            ListItem(
                overlineContent = if (today) ({ Text(stringResource(R.string.schedule_today)) }) else null,
                headlineContent = { Text(cell.date.format(dayLong)) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (cell.jobs.isEmpty()) stringResource(R.string.schedule_not_scheduled) else cell.jobs.joinToString(" · ") { it.jobName },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (cell.jobs.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        if (cell.conflict) ConflictLine()
                    }
                },
                leadingContent = { Icon(if (cell.jobs.isEmpty()) Icons.Rounded.EventBusy else Icons.Rounded.Work, contentDescription = null) },
                modifier = Modifier.testTag("fl_schedule_own_${cell.date}"),
            )
            HorizontalDivider()
        }
    }
}
