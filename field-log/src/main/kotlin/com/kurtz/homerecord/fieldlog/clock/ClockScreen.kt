package com.kurtz.homerecord.fieldlog.clock

import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.core.punch.ClockStatus
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val ButtonHeight = 56.dp

/** FL-Clock: status, job picker, Clock in / Break / Switch / Clock out, then "My timesheet" (AC-F8, AC-F10, AC-F18). */
@Composable
fun ClockScreen(state: ClockUiState, onEvent: (ClockEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.clock_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
        )
        if (state.loading) LoadingState(Modifier.weight(1f)) else ClockBody(state, onEvent, Modifier.weight(1f))
    }
    ClockDialogs(state, onEvent)
}

@Composable
private fun ClockBody(state: ClockUiState, onEvent: (ClockEvent) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier.testTag("fl_clock_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(state) }
        if (state.savedWithoutLocation) item { NoLocationNotice(onEvent) }
        if (state.status !is ClockStatus.OnBreak) {
            item { Text(stringResource(if (state.status is ClockStatus.Working) R.string.clock_switch_to else R.string.clock_job), style = MaterialTheme.typography.titleMedium) }
            if (state.jobs.isEmpty()) item { Text(stringResource(R.string.clock_no_jobs), style = MaterialTheme.typography.bodyMedium) }
            item { Column(Modifier.selectableGroup()) { state.jobs.forEach { JobRadio(it, it.id == state.selectedJobId, onEvent) } } }
        }
        item {
            OutlinedTextField(
                value = state.note,
                onValueChange = { onEvent(ClockEvent.Note(it)) },
                label = { Text(stringResource(R.string.clock_note)) },
                modifier = Modifier.fillMaxWidth().testTag("fl_clock_note"),
            )
        }
        item { Actions(state, onEvent) }
        if (state.canLeadCrew && state.status !is ClockStatus.OnBreak) item { CrewCard(state, onEvent) }
        item { Timesheet(state) }
    }
}

@Composable
private fun StatusCard(state: ClockUiState) {
    Card(Modifier.fillMaxWidth().testTag("fl_clock_status")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val headline = when (state.status) {
                ClockStatus.Out -> stringResource(R.string.clock_out_status)
                is ClockStatus.Working -> stringResource(R.string.clock_working_status)
                is ClockStatus.OnBreak -> stringResource(R.string.clock_break_status)
            }
            Text(headline, style = MaterialTheme.typography.titleLarge)
            state.currentJob?.let { Text(it.name, style = MaterialTheme.typography.bodyLarge) }
            if (state.status !is ClockStatus.Out) {
                Text(elapsed(state.elapsedMillis), style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("fl_clock_elapsed"))
            }
        }
    }
}

private fun elapsed(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

@Composable
private fun NoLocationNotice(onEvent: (ClockEvent) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("fl_clock_no_location")) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.clock_no_location), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onEvent(ClockEvent.DismissNotice) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun JobRadio(job: ClockJobOption, selected: Boolean, onEvent: (ClockEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = { onEvent(ClockEvent.SelectJob(job.id)) })
            .testTag("fl_clock_job_${job.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(job.name, style = MaterialTheme.typography.bodyLarge)
            job.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Actions(state: ClockUiState, onEvent: (ClockEvent) -> Unit) {
    val busy = state.gettingLocation
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val s = state.status) {
            ClockStatus.Out -> Button(
                onClick = { onEvent(ClockEvent.ClockIn) },
                enabled = !busy && (state.jobs.isEmpty() || state.selectedJobId != null),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).testTag("fl_clock_in"),
            ) { Text(stringResource(if (busy) R.string.clock_getting_location else R.string.clock_in)) }
            is ClockStatus.Working -> {
                Button(
                    onClick = { onEvent(ClockEvent.StartBreak) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).testTag("fl_clock_break"),
                ) { Text(stringResource(R.string.clock_break_start)) }
                OutlinedButton(
                    onClick = { onEvent(ClockEvent.SwitchJob) },
                    enabled = !busy && state.selectedJobId != null && state.selectedJobId != s.jobId,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).testTag("fl_clock_switch"),
                ) { Text(stringResource(if (busy) R.string.clock_getting_location else R.string.clock_switch)) }
                OutlinedButton(
                    onClick = { onEvent(ClockEvent.ClockOut) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).testTag("fl_clock_out"),
                ) { Text(stringResource(R.string.clock_out)) }
            }
            is ClockStatus.OnBreak -> {
                Button(
                    onClick = { onEvent(ClockEvent.EndBreak) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).testTag("fl_clock_break_end"),
                ) { Text(stringResource(R.string.clock_break_end)) }
                OutlinedButton(
                    onClick = { onEvent(ClockEvent.ClockOut) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).testTag("fl_clock_out"),
                ) { Text(stringResource(R.string.clock_out)) }
            }
        }
    }
}

/** Lead crew clock-in: tick the people, one tap punches them all with this phone's location (AC-F11). */
@Composable
private fun CrewCard(state: ClockUiState, onEvent: (ClockEvent) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("fl_clock_crew")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.clock_crew_title), style = MaterialTheme.typography.titleMedium)
            state.crewNotice?.let { n ->
                Row(Modifier.fillMaxWidth().testTag("fl_clock_crew_notice"), verticalAlignment = Alignment.CenterVertically) {
                    val text = if (n.skippedNames.isEmpty()) pluralStringResource(R.plurals.clock_crew_done, n.punched, n.punched)
                    else stringResource(R.string.clock_crew_done_skipped, pluralStringResource(R.plurals.clock_crew_done, n.punched, n.punched), n.skippedNames.joinToString(", "))
                    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onEvent(ClockEvent.DismissCrewNotice) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.dismiss)) }
                }
            }
            if (state.crew.isEmpty()) Text(stringResource(R.string.clock_crew_empty), style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.selectableGroup()) {
                state.crew.forEach { p ->
                    val checked = p.id in state.crewSelected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .toggleable(checked, enabled = !p.onClock, role = Role.Checkbox, onValueChange = { onEvent(ClockEvent.ToggleCrew(p.id)) })
                            .testTag("fl_clock_crew_${p.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null, enabled = !p.onClock)
                        Text(p.name, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (p.onClock) Text(stringResource(R.string.clock_crew_on_clock), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(
                onClick = { onEvent(ClockEvent.ClockInCrew) },
                enabled = !state.gettingLocation && state.crewCount > 0 && (state.jobs.isEmpty() || state.selectedJobId != null),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).testTag("fl_clock_crew_in"),
            ) { Text(if (state.gettingLocation) stringResource(R.string.clock_getting_location) else pluralStringResource(R.plurals.clock_crew_in, state.crewCount, state.crewCount)) }
        }
    }
}

@Composable
private fun Timesheet(state: ClockUiState) {
    val sheet = state.sheet ?: return
    val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Column(Modifier.fillMaxWidth().testTag("fl_clock_timesheet"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.clock_timesheet), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (state.weekApproved) Text(
            stringResource(R.string.clock_week_approved), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("fl_clock_week_approved"),
        )
        sheet.days.forEach { day ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.date.format(dayFormat), style = MaterialTheme.typography.bodyLarge)
                    if (day.flags.isNotEmpty()) {
                        val labels = day.flags.map { stringResource(flagLabel(it)) }
                        Text(
                            labels.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(WeekSheetBuilder.hm(day.workedMillis), style = MaterialTheme.typography.bodyLarge)
            }
        }
        TotalRow(R.string.clock_total_regular, sheet.regularMillis)
        if (sheet.overtimeMillis > 0) TotalRow(R.string.clock_total_overtime, sheet.overtimeMillis)
        if (sheet.doubleMillis > 0) TotalRow(R.string.clock_total_double, sheet.doubleMillis)
        TotalRow(R.string.clock_total, sheet.totalMillis, bold = true)
    }
}

@Composable
private fun TotalRow(label: Int, ms: Long, bold: Boolean = false) {
    val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), Modifier.weight(1f), style = style)
        Text(WeekSheetBuilder.hm(ms), style = style)
    }
}

private fun flagLabel(f: ShiftFlag): Int = when (f) {
    ShiftFlag.MissingClockOut -> R.string.flag_missing_out
    ShiftFlag.FarFromJob -> R.string.flag_far
    ShiftFlag.NoBreak -> R.string.flag_no_break
    ShiftFlag.Overlap -> R.string.flag_overlap
    ShiftFlag.Edited -> R.string.flag_edited
}

@Composable
private fun ClockDialogs(state: ClockUiState, onEvent: (ClockEvent) -> Unit) {
    if (state.explainLocation) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ClockEvent.ExplainSkip) },
            title = { Text(stringResource(R.string.clock_explain_title)) },
            text = { Text(stringResource(R.string.clock_explain_body)) },
            confirmButton = { TextButton({ onEvent(ClockEvent.ExplainContinue) }, Modifier.heightIn(min = 48.dp).testTag("fl_clock_explain_ok")) { Text(stringResource(R.string.clock_explain_continue)) } },
            dismissButton = { TextButton({ onEvent(ClockEvent.ExplainSkip) }, Modifier.heightIn(min = 48.dp).testTag("fl_clock_explain_skip")) { Text(stringResource(R.string.clock_explain_skip)) } },
        )
    }
    state.farReason?.let { sheet ->
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ClockEvent.CancelReason) },
            title = { Text(stringResource(R.string.clock_far_title)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    Text(stringResource(R.string.clock_far_body, sheet.distanceFt), style = MaterialTheme.typography.bodyMedium)
                    FarReasonSheet.choices.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(sheet.choice == choice, role = Role.RadioButton, onClick = { onEvent(ClockEvent.PickReason(choice)) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(sheet.choice == choice, onClick = null)
                            Text(choice, Modifier.padding(start = 12.dp))
                        }
                    }
                    if (sheet.choice == FarReasonSheet.OTHER) {
                        OutlinedTextField(
                            sheet.other, { onEvent(ClockEvent.OtherReason(it)) },
                            label = { Text(stringResource(R.string.clock_far_other)) },
                            modifier = Modifier.fillMaxWidth().testTag("fl_clock_far_other"),
                        )
                    }
                }
            },
            confirmButton = { TextButton({ onEvent(ClockEvent.SaveReason) }, Modifier.heightIn(min = 48.dp).testTag("fl_clock_far_save"), enabled = sheet.canSave) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ onEvent(ClockEvent.CancelReason) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.gpsRequired?.let { permissionMissing ->
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ClockEvent.DismissGps) },
            title = { Text(stringResource(R.string.clock_gps_title)) },
            text = { Text(stringResource(if (permissionMissing) R.string.clock_gps_permission else R.string.clock_gps_signal)) },
            confirmButton = {
                if (permissionMissing) TextButton({ onEvent(ClockEvent.OpenSettings) }, Modifier.heightIn(min = 48.dp).testTag("fl_clock_gps_settings")) { Text(stringResource(R.string.clock_open_settings)) }
                else TextButton({ onEvent(ClockEvent.DismissGps) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.dismiss)) }
            },
            dismissButton = if (permissionMissing) { { TextButton({ onEvent(ClockEvent.DismissGps) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) } } } else null,
        )
    }
}
