package com.kurtz.homerecord.fieldlog.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
 * FL-Reports (Owner and Office), three tabs: Hours (by person and by job for a week range, shift and QuickBooks CSV,
 * AC-F15, AC-F46, and the job timeline of delays from the daily logs; a delay opens that job's log on its day, AC-F32),
 * Photos (photo report PDF per job for the range, AC-F28) and Summaries (job summary PDF per job, AC-F47).
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onEvent: (ReportsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenLog: (jobId: String, day: LocalDate) -> Unit = { _, _ -> },
) {
    ScreenScaffold(title = stringResource(R.string.rep_title), onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Hours, photo reports and job summaries are separate tabs, so each report is one tap from Reports (rule 12).
            // Scrollable like the job tabs, so a label is never cut at 200% text.
            PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 8.dp) {
                ReportTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        // Only the selected tab in the primary colour, so the open tab reads at a glance (v5:tabs-all-primary).
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onEvent(ReportsEvent.SelectTab(tab)) },
                        text = { Text(stringResource(tabLabel(tab))) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("fl_reports_tab_${tab.name}"),
                    )
                }
            }
            if (state.loading) {
                LoadingState(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("fl_reports_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (state.tab) {
                        ReportTab.Hours -> {
                            item(key = "range") { RangePicker(state, onEvent, Modifier.padding(horizontal = 16.dp)) }
                            hours(state, onEvent)
                            delayTimeline(state, onOpenLog)
                        }
                        ReportTab.Photos -> {
                            item(key = "range") { RangePicker(state, onEvent, Modifier.padding(horizontal = 16.dp)) }
                            photoReports(state, onEvent)
                        }
                        ReportTab.Summaries -> jobSummaries(state, onEvent)
                    }
                }
            }
        }
    }
}

private fun tabLabel(tab: ReportTab) = when (tab) {
    ReportTab.Hours -> R.string.rep_tab_hours
    ReportTab.Photos -> R.string.rep_tab_photos
    ReportTab.Summaries -> R.string.rep_tab_summaries
}

/**
 * Hours for the range: the totals card, by person and by job, then the two exports. With no hours a one-line row says
 * so (the screen still has the delays below it, so it is not a full-screen empty state, rule 10) and the exports stay
 * shown but disabled.
 */
private fun LazyListScope.hours(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit) {
    if (state.isEmpty) {
        item(key = "hours-empty") {
            RecordListItem(
                headline = stringResource(R.string.rep_empty_title),
                supporting = stringResource(R.string.rep_empty),
                supportingMaxLines = 4,
                leading = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                trailing = null,
                modifier = Modifier.fillMaxWidth().testTag("fl_reports_empty"),
            )
        }
    } else {
        item(key = "totals") { TotalsCard(state, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
        item(key = "by-person-h") { SectionHeading(stringResource(R.string.rep_by_person)) }
        items(state.byPerson, key = { "p-${it.id}" }) { LineRow(it, Icons.Rounded.Person, "fl_reports_person_${it.id}") }
        item(key = "by-job-h") { SectionHeading(stringResource(R.string.rep_by_job)) }
        items(state.byJob, key = { "j-${it.id}" }) { LineRow(it, Icons.Rounded.Work, "fl_reports_job_${it.id}") }
    }
    item(key = "exports") { ExportButtons(state, onEvent, Modifier.padding(start = 16.dp, end = 16.dp, top = if (state.isEmpty) 8.dp else 16.dp)) }
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

/**
 * Job summaries (AC-F47): every job, Active first; tapping one makes its summary PDF (hours against the budget,
 * change orders, tasks, days on site, delays) and opens the share sheet.
 */
private fun LazyListScope.jobSummaries(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit) {
    item(key = "summaries-h") { SectionHeading(stringResource(R.string.rep_summaries)) }
    item(key = "summaries-hint") {
        Text(
            stringResource(if (state.summaries.isEmpty()) R.string.rep_summaries_none else R.string.rep_summaries_hint),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.summaryFailed) {
        item(key = "summaries-failed") {
            Text(
                stringResource(R.string.rep_summary_failed),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("fl_reports_summary_failed"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
        }
    }
    items(state.summaries, key = { "summary-${it.id}" }) { job ->
        val busy = state.summaryBusy == job.id
        RecordListItem(
            headline = job.name,
            supporting = if (busy) stringResource(R.string.rep_summary_making) else job.line,
            leading = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
            trailing = {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                else Icon(Icons.Rounded.Share, contentDescription = null)
            },
            onClick = { onEvent(ReportsEvent.ShareJobSummary(job.id)) },
            onClickLabel = stringResource(R.string.rep_summary_make),
            modifier = Modifier.fillMaxWidth().testTag("fl_reports_summary_${job.id}"),
        )
    }
}

/**
 * Photo reports (AC-F28): every job with how many photos it has in the range above; tapping one with photos makes a
 * PDF of them, four to a page with captions, and opens the share sheet. A job with none in the range is not tappable.
 */
private fun LazyListScope.photoReports(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit) {
    item(key = "photos-h") { SectionHeading(stringResource(R.string.rep_photos)) }
    item(key = "photos-hint") {
        Text(
            stringResource(if (state.photoJobs.isEmpty()) R.string.rep_summaries_none else R.string.rep_photos_hint),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.photoNotice?.takeIf { it != PhotoReportOutcome.Shared }?.let { notice ->
        item(key = "photos-notice") {
            Text(
                stringResource(if (notice == PhotoReportOutcome.NoneHere) R.string.rep_photo_none_here else R.string.rep_photo_failed),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("fl_reports_photo_notice"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
        }
    }
    items(state.photoJobs, key = { "photo-${it.id}" }) { job ->
        val busy = state.photoBusy == job.id
        val has = job.photos > 0
        RecordListItem(
            headline = job.name,
            supporting = when {
                busy -> stringResource(R.string.rep_summary_making)
                has -> pluralStringResource(R.plurals.rep_photos_count, job.photos, job.photos)
                else -> stringResource(R.string.rep_photos_zero)
            },
            leading = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
            trailing = when {
                busy -> { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp) } }
                has -> { { Icon(Icons.Rounded.Share, contentDescription = null) } }
                else -> null
            },
            onClick = if (has) { { onEvent(ReportsEvent.SharePhotoReport(job.id)) } } else null,
            onClickLabel = stringResource(R.string.rep_photo_make),
            modifier = Modifier.fillMaxWidth().testTag("fl_reports_photo_${job.id}"),
        )
    }
}
