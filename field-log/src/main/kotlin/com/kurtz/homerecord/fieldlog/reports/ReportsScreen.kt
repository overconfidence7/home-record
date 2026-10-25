package com.kurtz.homerecord.fieldlog.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.HoursLine
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.clock.WeekSheetBuilder
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** FL-Reports (Owner and Office): hours by person and by job for a week range, shift and QuickBooks CSV (AC-F15, AC-F46). */
@Composable
fun ReportsScreen(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.rep_title), onBack = onBack) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxWidth().testTag("fl_reports_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { RangePicker(state, onEvent) }
                if (state.isEmpty) {
                    item {
                        Text(
                            stringResource(R.string.rep_empty), Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("fl_reports_empty"),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item { TotalsCard(state) }
                    item { Text(stringResource(R.string.rep_by_person), style = MaterialTheme.typography.titleMedium) }
                    items(state.byPerson, key = { "p-${it.id}" }) { LineRow(it, "fl_reports_person_${it.id}") }
                    item { Text(stringResource(R.string.rep_by_job), style = MaterialTheme.typography.titleMedium) }
                    items(state.byJob, key = { "j-${it.id}" }) { LineRow(it, "fl_reports_job_${it.id}") }
                }
                item { ExportButtons(state, onEvent) }
            }
        }
    }
}

@Composable
private fun RangePicker(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit) {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportRange.entries.forEach { r ->
                FilterChip(
                    selected = state.range == r, onClick = { onEvent(ReportsEvent.SelectRange(r)) },
                    label = { Text(stringResource(rangeLabel(r))) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("fl_reports_range_${r.name}"),
                )
            }
        }
        Text(
            stringResource(R.string.rep_dates, state.from.format(fmt), state.to.format(fmt)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun rangeLabel(r: ReportRange) = when (r) {
    ReportRange.ThisWeek -> R.string.rep_range_this
    ReportRange.LastWeek -> R.string.rep_range_last
    ReportRange.LastFourWeeks -> R.string.rep_range_four
}

@Composable
private fun TotalsCard(state: ReportsUiState) {
    Card(Modifier.fillMaxWidth().testTag("fl_reports_totals")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.rep_total, WeekSheetBuilder.hm(state.totalMillis)), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.rep_regular, WeekSheetBuilder.hm(state.regularMillis)), style = MaterialTheme.typography.bodyMedium)
            if (state.overtimeMillis > 0) Text(stringResource(R.string.rep_overtime, WeekSheetBuilder.hm(state.overtimeMillis)), style = MaterialTheme.typography.bodyMedium)
            if (state.doubleMillis > 0) Text(stringResource(R.string.rep_double, WeekSheetBuilder.hm(state.doubleMillis)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LineRow(line: HoursLine, tag: String) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.titleSmall)
                if (line.overtimeMillis + line.doubleMillis > 0) {
                    Text(
                        stringResource(R.string.ts_overtime, WeekSheetBuilder.hm(line.overtimeMillis + line.doubleMillis)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(WeekSheetBuilder.hm(line.workedMillis), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ExportButtons(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            { onEvent(ReportsEvent.ExportShifts) }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_reports_export_shifts"),
            enabled = !state.exporting,
        ) { Text(stringResource(R.string.rep_export_shifts)) }
        OutlinedButton(
            { onEvent(ReportsEvent.ExportQuickBooks) }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_reports_export_quickbooks"),
            enabled = !state.exporting,
        ) { Text(stringResource(R.string.rep_export_quickbooks)) }
        state.notice?.let {
            Text(
                stringResource(
                    when (it) {
                        ExportNotice.Shared -> R.string.rep_notice_shared
                        ExportNotice.Failed -> R.string.rep_notice_failed
                        ExportNotice.Empty -> R.string.rep_notice_empty
                    },
                ),
                Modifier.testTag("fl_reports_notice"), style = MaterialTheme.typography.bodyMedium,
                color = if (it == ExportNotice.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
