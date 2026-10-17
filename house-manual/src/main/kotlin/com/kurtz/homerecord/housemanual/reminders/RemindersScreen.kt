package com.kurtz.homerecord.housemanual.reminders

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.ClimateZone
import com.kurtz.homerecord.core.model.MaintenanceRule
import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun RemindersRoute(viewModel: RemindersViewModel, onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val askNotifications = rememberNotificationPermissionRequester {}
    LaunchedEffect(viewModel) { viewModel.tasksCreated.collect { askNotifications() } }
    RemindersScreen(state, viewModel::onEvent)
}

@Composable
fun RemindersScreen(state: RemindersUiState, onEvent: (RemindersEvent) -> Unit) {
    if (!state.loaded) return
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.reminders_climate_label), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClimateZone.entries.forEach { zone ->
                FilterChip(
                    selected = state.climateZone == zone,
                    onClick = { onEvent(RemindersEvent.PickZone(zone)) },
                    label = { Text(stringResource(zone.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("zone_${zone.name}"),
                )
            }
        }
        if (state.items.isEmpty()) {
            Text(stringResource(R.string.reminders_empty), modifier = Modifier.testTag("reminders_empty"))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.task.id }) { item -> ReminderRow(item, item.task.id == state.highlightId, onEvent) }
            }
        }
    }
}

@Composable
private fun ReminderRow(item: ReminderItem, highlighted: Boolean, onEvent: (RemindersEvent) -> Unit) {
    val colors = if (highlighted) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth().testTag("reminder_row"), colors = colors) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.task.title, style = MaterialTheme.typography.titleMedium)
            val status = when {
                item.overdue -> stringResource(R.string.reminders_overdue, item.task.nextDue)
                item.dueToday -> stringResource(R.string.reminders_due_today)
                else -> stringResource(R.string.reminders_due, item.task.nextDue)
            }
            Text(
                status,
                color = if (item.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("reminder_status"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(RemindersEvent.Done(item.task.id)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_done"),
                ) { Text(stringResource(R.string.reminders_done)) }
                OutlinedButton(
                    onClick = { onEvent(RemindersEvent.Snooze(item.task.id)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_snooze"),
                ) { Text(stringResource(R.string.reminders_snooze)) }
            }
        }
    }
}

private fun ClimateZone.labelRes(): Int = when (this) {
    ClimateZone.COLD -> R.string.climate_cold
    ClimateZone.MIXED -> R.string.climate_mixed
    ClimateZone.HOT_HUMID -> R.string.climate_hot_humid
    ClimateZone.HOT_DRY -> R.string.climate_hot_dry
}

@Preview(showBackground = true)
@Composable
private fun RemindersScreenPreview() {
    val task = MaintenanceTask("1", "h", null, "Clean gutters", MaintenanceRule.CALENDAR, 6, null, null, "2026-10-01", 0, 0, Source.HOUSE_MANUAL)
    HomeRecordTheme { RemindersScreen(RemindersUiState(true, listOf(ReminderItem(task, overdue = true, dueToday = false)), ClimateZone.COLD)) {} }
}
