package com.kurtz.homerecord.housemanual.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/**
 * HM-Report (AC-H13): what the home report holds (cover, a floor plan per floor, every room's items with warranty
 * status, shutoffs, panel legends) and one button that makes the PDF and opens the share sheet.
 */
@Composable
fun ReportScreen(state: ReportUiState, onEvent: (ReportEvent) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.report_screen_title), subtitle = state.homeName.ifBlank { null }, onBack = { onEvent(ReportEvent.Back) }) { padding ->
        val contents = state.contents
        if (state.loading || contents == null) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).testTag("hm_report_contents")) {
            Text(
                stringResource(R.string.report_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                stringResource(R.string.report_whats_in),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            Part(Icons.Outlined.Home, stringResource(R.string.report_part_cover), stringResource(R.string.report_part_cover_body), "cover")
            Part(Icons.Outlined.Map, stringResource(R.string.report_part_plans), pluralStringResource(R.plurals.report_part_plans_body, contents.floors, contents.floors), "plans")
            Part(
                Icons.Outlined.Chair, stringResource(R.string.report_part_rooms),
                pluralStringResource(R.plurals.report_part_rooms_body, contents.rooms, contents.rooms) + stringResource(R.string.list_separator) +
                    pluralStringResource(R.plurals.report_part_items_body, contents.items, contents.items),
                "rooms",
            )
            Part(
                Icons.Outlined.PowerOff, stringResource(R.string.report_part_shutoffs),
                if (contents.shutoffs == 0) stringResource(R.string.report_part_shutoffs_none) else pluralStringResource(R.plurals.report_part_shutoffs_body, contents.shutoffs, contents.shutoffs),
                "shutoffs",
            )
            Part(
                Icons.Outlined.ElectricalServices, stringResource(R.string.report_part_panels),
                if (contents.panels == 0) stringResource(R.string.report_part_panels_none) else pluralStringResource(R.plurals.report_part_panels_body, contents.panels, contents.panels),
                "panels",
            )
            Button(
                onClick = { onEvent(ReportEvent.Make) },
                enabled = !state.busy,
                modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 56.dp).testTag("hm_report_make"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Share, contentDescription = null)
                    Text(stringResource(if (state.busy) R.string.report_making else R.string.report_make))
                }
            }
            if (state.failed) {
                Text(
                    stringResource(R.string.report_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("hm_report_failed"),
                )
            }
        }
    }
}

@Composable
private fun Part(icon: ImageVector, title: String, body: String, tag: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.testTag("hm_report_part_$tag"),
    )
}
