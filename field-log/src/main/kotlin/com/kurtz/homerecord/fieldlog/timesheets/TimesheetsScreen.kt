package com.kurtz.homerecord.fieldlog.timesheets

import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.input.ImeAction
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.TimeField
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.clock.WeekSheetBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** FL-Timesheets (Owner and Office): a week per person with flags, punch edits with a reason, Approve (AC-F14). */
@Composable
fun TimesheetsScreen(state: TimesheetsUiState, onEvent: (TimesheetsEvent) -> Unit, onBack: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    state.edit?.let { PunchEditScreen(it, onEvent, zone); return }
    ScreenScaffold(
        title = stringResource(R.string.ts_title),
        onBack = onBack,
        actions = {
            if (state.approvable > 0) {
                TextButton({ onEvent(TimesheetsEvent.AskApproveAll) }, Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_approve_all")) {
                    Text(stringResource(R.string.ts_approve_all))
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxWidth().testTag("fl_timesheets_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                item { WeekPicker(state, onEvent, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) }
                if (state.people.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Schedule,
                            headline = stringResource(R.string.ts_empty_title),
                            body = stringResource(R.string.ts_empty),
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillParentMaxHeight(0.5f).testTag("fl_timesheets_empty"),
                        )
                    }
                }
                items(state.people, key = { it.memberId }) { person ->
                    PersonSection(person, expanded = state.expandedId == person.memberId, onEvent = onEvent, zone = zone)
                }
            }
        }
    }
    TimesheetsDialogs(state, onEvent)
}

@Composable
private fun WeekPicker(state: TimesheetsUiState, onEvent: (TimesheetsEvent) -> Unit, modifier: Modifier) {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onEvent(TimesheetsEvent.PreviousWeek) }, Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_prev")) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.ts_prev_week))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.ts_week_of, state.weekStart.format(fmt)), style = MaterialTheme.typography.titleMedium)
            if (state.isCurrentWeek) Text(stringResource(R.string.ts_this_week), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({ onEvent(TimesheetsEvent.NextWeek) }, Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_next"), enabled = !state.isCurrentWeek) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.ts_next_week))
        }
    }
}

/** One person's week: a list row that expands into days and punches, rows divided rather than carded (rules 7 and 8). */
@Composable
private fun PersonSection(person: PersonWeek, expanded: Boolean, onEvent: (TimesheetsEvent) -> Unit, zone: ZoneId) {
    val extra = person.sheet.overtimeMillis + person.sheet.doubleMillis
    val supporting = listOfNotNull(
        stringResource(R.string.ts_hours, WeekSheetBuilder.hm(person.sheet.totalMillis)),
        if (extra > 0) stringResource(R.string.ts_overtime, WeekSheetBuilder.hm(extra)) else null,
    ).joinToString(" · ")
    val stateText = stringResource(if (expanded) R.string.ts_person_expanded else R.string.ts_person_collapsed)
    Column(Modifier.fillMaxWidth().testTag("fl_timesheets_person_${person.memberId}")) {
        RecordListItem(
            headline = person.name,
            supporting = supporting,
            leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (person.approved) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.ts_approved), Modifier.padding(start = 4.dp, end = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                }
            },
            onClick = { onEvent(TimesheetsEvent.Toggle(person.memberId)) },
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = stateText }
                .testTag("fl_timesheets_toggle_${person.memberId}"),
        )
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (person.flags.isNotEmpty()) {
                val labels = person.flags.map { stringResource(flagLabel(it)) }
                Text(labels.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (expanded) {
                person.sheet.days.filter { it.workedMillis > 0 || it.flags.isNotEmpty() }.forEach { day ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(day.date.format(DateTimeFormatter.ofPattern("EEE MMM d")), style = MaterialTheme.typography.bodyMedium)
                        Text(WeekSheetBuilder.hm(day.workedMillis), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                person.punches.forEach { PunchLine(it, locked = person.approved, onEvent = onEvent, zone = zone) }
                if (person.approved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.ts_locked), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (person.punches.isNotEmpty()) {
                    // Approve one person lives inside the opened row; Approve all in the top bar is the screen's action (rules 7, 13).
                    OutlinedButton(
                        { onEvent(TimesheetsEvent.Approve(person.memberId)) },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fl_timesheets_approve_${person.memberId}"),
                    ) { Text(stringResource(R.string.ts_approve_person, person.name)) }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun PunchLine(p: PunchRow, locked: Boolean, onEvent: (TimesheetsEvent) -> Unit, zone: ZoneId) {
    val time = DateTimeFormatter.ofPattern("EEE h:mm a")
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .let { if (locked) it else it.clickable { onEvent(TimesheetsEvent.EditPunch(p.id)) } }
            .testTag("fl_timesheets_punch_${p.id}"),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(kindLabel(p.kind)), style = MaterialTheme.typography.bodyLarge)
            Text(Instant.ofEpochMilli(p.at).atZone(zone).format(time), style = MaterialTheme.typography.bodyLarge)
        }
        val notes = listOfNotNull(
            p.jobName ?: stringResource(R.string.ts_punch_no_job),
            if (p.farFromJob) stringResource(R.string.ts_punch_far) else null,
            if (p.edited) stringResource(R.string.ts_punch_edited) else null,
        )
        Text(notes.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.edited) AuditLines(p.edits, zone)
    }
}

@Composable
private fun AuditLines(edits: List<com.kurtz.homerecord.core.punch.PunchEdit>, zone: ZoneId) {
    val whenFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    edits.forEach { e ->
        Text(
            stringResource(
                R.string.ts_audit_line, e.byName, Instant.ofEpochMilli(e.at).atZone(zone).format(whenFmt),
                Instant.ofEpochMilli(e.beforeAt).atZone(zone).format(timeFmt), Instant.ofEpochMilli(e.afterAt).atZone(zone).format(timeFmt), e.reason,
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimesheetsDialogs(state: TimesheetsUiState, onEvent: (TimesheetsEvent) -> Unit) {
    if (state.confirmApproveAll) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(TimesheetsEvent.CancelApproveAll) },
            title = { Text(stringResource(R.string.ts_approve_all_title)) },
            text = { Text(stringResource(R.string.ts_approve_all_body)) },
            confirmButton = { TextButton({ onEvent(TimesheetsEvent.ApproveAll) }, Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_approve_all_confirm")) { Text(stringResource(R.string.ts_approve_all)) } },
            dismissButton = { TextButton({ onEvent(TimesheetsEvent.CancelApproveAll) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Edit punch as a full-screen form with Close and Save (ui-standards rules 3 and 15): the time comes from the
 * time picker (rule 22), the job from chips, and a reason is required; earlier changes are listed under it (AC-F14).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PunchEditScreen(sheet: PunchEditSheet, onEvent: (TimesheetsEvent) -> Unit, zone: ZoneId) {
    // Back with nothing changed closes the form; with changes, FormScaffold's own handler asks first.
    BackHandler { onEvent(TimesheetsEvent.CancelEdit) }
    CompositionLocalProvider(LocalTagPrefix provides "fl_timesheets_edit") {
        FormScaffold(
            title = stringResource(R.string.ts_edit_title),
            dirty = sheet.dirty,
            saveEnabled = true,
            onClose = { onEvent(TimesheetsEvent.CancelEdit) },
            onSave = { onEvent(TimesheetsEvent.SaveEdit) },
            modifier = Modifier.testTag("fl_timesheets_edit"),
        ) {
            Text(
                stringResource(R.string.ts_edit_subtitle, sheet.personName, stringResource(kindLabel(sheet.kind)), sheet.dateLabel),
                style = MaterialTheme.typography.titleMedium,
            )
            TimeField(stringResource(R.string.ts_edit_time), sheet.time, { onEvent(TimesheetsEvent.EditTime(it)) }, Modifier.testTag("fl_timesheets_edit_time"))
            if (sheet.jobs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ts_edit_job), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sheet.jobs.forEach { job ->
                            FilterChip(
                                selected = sheet.jobId == job.id, onClick = { onEvent(TimesheetsEvent.EditJob(job.id)) },
                                label = { Text(job.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_edit_job_${job.id}"),
                            )
                        }
                    }
                }
            }
            LabeledField(
                stringResource(R.string.ts_edit_reason), sheet.reason, { onEvent(TimesheetsEvent.EditReason(it)) },
                Modifier.testTag("fl_timesheets_edit_reason"), required = true,
                helper = stringResource(R.string.ts_edit_reason_helper),
                error = if (sheet.error == EditError.ReasonRequired) stringResource(R.string.ts_edit_reason_required) else null,
                singleLine = false, imeAction = ImeAction.Default,
            )
            if (sheet.error == EditError.Locked) {
                Text(stringResource(R.string.ts_edit_locked), Modifier.testTag("fl_timesheets_edit_locked"), color = MaterialTheme.colorScheme.error)
            }
            if (sheet.audit.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ts_audit_title), style = MaterialTheme.typography.labelLarge)
                    AuditLines(sheet.audit, zone)
                }
            }
        }
    }
}

internal fun kindLabel(kind: String): Int = when (kind) {
    PunchKind.IN -> R.string.punch_in
    PunchKind.OUT -> R.string.punch_out
    PunchKind.BREAK_START -> R.string.punch_break_start
    PunchKind.BREAK_END -> R.string.punch_break_end
    else -> R.string.punch_switch
}

private fun flagLabel(f: ShiftFlag): Int = when (f) {
    ShiftFlag.MissingClockOut -> R.string.flag_missing_out
    ShiftFlag.FarFromJob -> R.string.flag_far
    ShiftFlag.NoBreak -> R.string.flag_no_break
    ShiftFlag.Overlap -> R.string.flag_overlap
    ShiftFlag.Edited -> R.string.flag_edited
}
