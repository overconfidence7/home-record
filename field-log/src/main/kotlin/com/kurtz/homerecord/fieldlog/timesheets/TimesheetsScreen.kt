package com.kurtz.homerecord.fieldlog.timesheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.coreui.components.LoadingState
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { WeekPicker(state, onEvent) }
                if (state.people.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.ts_empty), Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("fl_timesheets_empty"),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.people, key = { it.memberId }) { person ->
                    PersonCard(person, expanded = state.expandedId == person.memberId, onEvent = onEvent, zone = zone)
                }
            }
        }
    }
    TimesheetsDialogs(state, onEvent, zone)
}

@Composable
private fun WeekPicker(state: TimesheetsUiState, onEvent: (TimesheetsEvent) -> Unit) {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun PersonCard(person: PersonWeek, expanded: Boolean, onEvent: (TimesheetsEvent) -> Unit, zone: ZoneId) {
    Card(Modifier.fillMaxWidth().testTag("fl_timesheets_person_${person.memberId}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable { onEvent(TimesheetsEvent.Toggle(person.memberId)) }
                    .testTag("fl_timesheets_toggle_${person.memberId}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ts_hours, WeekSheetBuilder.hm(person.sheet.totalMillis)), style = MaterialTheme.typography.bodyMedium)
                    if (person.sheet.overtimeMillis + person.sheet.doubleMillis > 0) {
                        Text(
                            stringResource(R.string.ts_overtime, WeekSheetBuilder.hm(person.sheet.overtimeMillis + person.sheet.doubleMillis)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (person.approved) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.ts_approved), Modifier.padding(start = 4.dp, end = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
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
                }
            }
            if (!person.approved && person.punches.isNotEmpty()) {
                OutlinedButton(
                    { onEvent(TimesheetsEvent.Approve(person.memberId)) },
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fl_timesheets_approve_${person.memberId}"),
                ) { Text(stringResource(R.string.ts_approve_person, person.name)) }
            }
        }
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
private fun TimesheetsDialogs(state: TimesheetsUiState, onEvent: (TimesheetsEvent) -> Unit, zone: ZoneId) {
    if (state.confirmApproveAll) {
        AlertDialog(
            onDismissRequest = { onEvent(TimesheetsEvent.CancelApproveAll) },
            title = { Text(stringResource(R.string.ts_approve_all_title)) },
            text = { Text(stringResource(R.string.ts_approve_all_body)) },
            confirmButton = { TextButton({ onEvent(TimesheetsEvent.ApproveAll) }, Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_approve_all_confirm")) { Text(stringResource(R.string.ts_approve_all)) } },
            dismissButton = { TextButton({ onEvent(TimesheetsEvent.CancelApproveAll) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.edit?.let { sheet ->
        AlertDialog(
            onDismissRequest = { onEvent(TimesheetsEvent.CancelEdit) },
            title = { Text(stringResource(R.string.ts_edit_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.ts_edit_subtitle, sheet.personName, stringResource(kindLabel(sheet.kind)), sheet.dateLabel),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        sheet.timeText, { onEvent(TimesheetsEvent.EditTime(it)) },
                        Modifier.fillMaxWidth().testTag("fl_timesheets_edit_time"),
                        label = { Text(stringResource(R.string.ts_edit_time)) },
                        supportingText = { Text(if (sheet.error == EditError.BadTime) stringResource(R.string.ts_edit_bad_time) else stringResource(R.string.ts_edit_time_hint)) },
                        isError = sheet.error == EditError.BadTime, singleLine = true,
                    )
                    if (sheet.jobs.isNotEmpty()) {
                        Text(stringResource(R.string.ts_edit_job), style = MaterialTheme.typography.labelLarge)
                        sheet.jobs.forEach { job ->
                            FilterChip(
                                selected = sheet.jobId == job.id, onClick = { onEvent(TimesheetsEvent.EditJob(job.id)) },
                                label = { Text(job.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_edit_job_${job.id}"),
                            )
                        }
                    }
                    OutlinedTextField(
                        sheet.reason, { onEvent(TimesheetsEvent.EditReason(it)) },
                        Modifier.fillMaxWidth().testTag("fl_timesheets_edit_reason"),
                        label = { Text(stringResource(R.string.ts_edit_reason)) },
                        supportingText = { if (sheet.error == EditError.ReasonRequired) Text(stringResource(R.string.ts_edit_reason_required)) },
                        isError = sheet.error == EditError.ReasonRequired,
                    )
                    if (sheet.error == EditError.Locked) Text(stringResource(R.string.ts_edit_locked), color = MaterialTheme.colorScheme.error)
                    if (sheet.audit.isNotEmpty()) {
                        Text(stringResource(R.string.ts_audit_title), style = MaterialTheme.typography.labelLarge)
                        AuditLines(sheet.audit, zone)
                    }
                }
            },
            confirmButton = { Button({ onEvent(TimesheetsEvent.SaveEdit) }, Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_edit_save")) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ onEvent(TimesheetsEvent.CancelEdit) }, Modifier.heightIn(min = 48.dp).testTag("fl_timesheets_edit_cancel")) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun kindLabel(kind: String): Int = when (kind) {
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
