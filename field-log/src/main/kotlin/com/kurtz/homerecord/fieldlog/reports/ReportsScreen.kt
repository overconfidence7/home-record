package com.kurtz.homerecord.fieldlog.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.HoursLine
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.clock.WeekSheetBuilder
import com.kurtz.homerecord.fieldlog.log.icon
import com.kurtz.homerecord.fieldlog.log.label
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * FL-Reports (Owner and Office): hours by person and by job for a week range, shift and QuickBooks CSV (AC-F15, AC-F46),
 * and the job timeline of delays from the daily logs; a delay opens that job's log on its day (AC-F32).
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onEvent: (ReportsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenLog: (jobId: String, day: LocalDate) -> Unit = { _, _ -> },
) {
    ScreenScaffold(title = stringResource(R.string.rep_title), onBack = onBack) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxWidth().testTag("fl_reports_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { RangePicker(state, onEvent, Modifier.padding(horizontal = 16.dp)) }
                if (state.isEmpty) {
                    item {
                        // Rule 10: icon, headline and a sentence; the exports below are disabled because there is nothing to send.
                        EmptyState(
                            icon = Icons.Rounded.Schedule,
                            headline = stringResource(R.string.rep_empty_title),
                            body = stringResource(R.string.rep_empty),
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillParentMaxHeight(0.5f).testTag("fl_reports_empty"),
                        )
                    }
                } else {
                    item { TotalsCard(state, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
                    item { SectionHeading(stringResource(R.string.rep_by_person)) }
                    items(state.byPerson, key = { "p-${it.id}" }) { LineRow(it, Icons.Rounded.Person, "fl_reports_person_${it.id}") }
                    item { SectionHeading(stringResource(R.string.rep_by_job)) }
                    items(state.byJob, key = { "j-${it.id}" }) { LineRow(it, Icons.Rounded.Work, "fl_reports_job_${it.id}") }
                }
                item { ExportButtons(state, onEvent, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
                delayTimeline(state, onOpenLog)
            }
        }
    }
}

@Composable
private fun RangePicker(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit, modifier: Modifier) {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun TotalsCard(state: ReportsUiState, modifier: Modifier) {
    Card(modifier.fillMaxWidth().testTag("fl_reports_totals")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.rep_total, WeekSheetBuilder.hm(state.totalMillis)), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.rep_regular, WeekSheetBuilder.hm(state.regularMillis)), style = MaterialTheme.typography.bodyMedium)
            if (state.overtimeMillis > 0) Text(stringResource(R.string.rep_overtime, WeekSheetBuilder.hm(state.overtimeMillis)), style = MaterialTheme.typography.bodyMedium)
            if (state.doubleMillis > 0) Text(stringResource(R.string.rep_double, WeekSheetBuilder.hm(state.doubleMillis)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

/** One person or job: a list row with a leading icon and the hours trailing (rules 7 and 8). Totals stay the one card. */
@Composable
private fun LineRow(line: HoursLine, icon: ImageVector, tag: String) {
    val extra = line.overtimeMillis + line.doubleMillis
    RecordListItem(
        headline = line.name,
        supporting = if (extra > 0) stringResource(R.string.ts_overtime, WeekSheetBuilder.hm(extra)) else null,
        leading = { Icon(icon, contentDescription = null) },
        trailing = { Text(WeekSheetBuilder.hm(line.workedMillis), style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun ExportButtons(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            { onEvent(ReportsEvent.ExportShifts) }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_reports_export_shifts"),
            enabled = !state.exporting && !state.isEmpty,
        ) { Text(stringResource(R.string.rep_export_shifts)) }
        OutlinedButton(
            { onEvent(ReportsEvent.ExportQuickBooks) }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("fl_reports_export_quickbooks"),
            enabled = !state.exporting && !state.isEmpty,
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

/**
 * Delays in the range, one block per job: the job and how many days it lost, then each delay oldest first with its
 * reason icon, the day and the note. A row opens the job's Log tab on that day (AC-F32).
 */
private fun LazyListScope.delayTimeline(state: ReportsUiState, onOpenLog: (String, LocalDate) -> Unit) {
    item(key = "delays-h") { SectionHeading(stringResource(R.string.rep_delays)) }
    if (state.delays.isEmpty()) {
        item(key = "delays-none") {
            Text(
                stringResource(R.string.rep_delays_none),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("fl_reports_delays_none"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    state.delays.forEach { job ->
        item(key = "delays-job-${job.jobId}") {
            Text(
                stringResource(R.string.rep_delays_job, job.jobName, pluralStringResource(R.plurals.rep_delay_days, job.days, job.days)),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() }.testTag("fl_reports_delays_${job.jobId}"),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            )
        }
        items(job.entries, key = { "delay-${job.jobId}-${it.day}-${it.index}" }) { entry ->
            val day = entry.day.format(dayFmt)
            RecordListItem(
                headline = stringResource(entry.reason.label()),
                supporting = if (entry.note.isBlank()) day else stringResource(R.string.rep_delay_line, day, entry.note),
                leading = { Icon(entry.reason.icon(), contentDescription = null) },
                onClick = { onOpenLog(job.jobId, entry.day) },
                onClickLabel = stringResource(R.string.rep_delay_open),
                modifier = Modifier.fillMaxWidth().testTag("fl_reports_delay_${job.jobId}_${entry.day}_${entry.index}"),
            )
        }
    }
}
