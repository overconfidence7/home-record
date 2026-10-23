package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

private fun JobTab.label(): Int = when (this) {
    JobTab.OVERVIEW -> R.string.jobtab_overview
    JobTab.PHOTOS -> R.string.jobtab_photos
    JobTab.LOG -> R.string.jobtab_log
    JobTab.FILES -> R.string.jobtab_files
    JobTab.TASKS -> R.string.jobtab_tasks
    JobTab.TIME -> R.string.jobtab_time
    JobTab.HOUSE -> R.string.jobtab_house
    JobTab.CHANGES -> R.string.jobtab_changes
}

/** FL-Job: name and client in the bar, the eight tabs below. Overview and House are built so far. */
@Composable
fun JobScreen(
    state: JobOverviewUiState,
    selected: JobTab,
    onSelect: (JobTab) -> Unit,
    onEvent: (JobOverviewEvent) -> Unit,
    onBack: () -> Unit,
    houseContent: @Composable () -> Unit = { SoonNote(JobTab.HOUSE) },
) {
    ScreenScaffold(title = state.name.ifBlank { stringResource(R.string.tab_jobs) }, subtitle = state.clientName, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 8.dp) {
                JobTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        text = { Text(stringResource(tab.label())) },
                        modifier = Modifier.testTag("fl_job_tab_${tab.name.lowercase()}"),
                    )
                }
            }
            if (selected == JobTab.OVERVIEW) {
                JobOverviewScreen(state, onEvent)
            } else if (selected == JobTab.HOUSE) {
                houseContent()
            } else {
                SoonNote(selected)
            }
        }
    }
}

@Composable
private fun SoonNote(tab: JobTab) {
    Text(
        stringResource(R.string.jobtab_soon, stringResource(tab.label())),
        Modifier.padding(24.dp).testTag("fl_job_tab_soon"),
        textAlign = TextAlign.Center,
    )
}
