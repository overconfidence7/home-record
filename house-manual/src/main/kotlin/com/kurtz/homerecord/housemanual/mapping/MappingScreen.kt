package com.kurtz.homerecord.housemanual.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.mapping.MappingPhase
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.panel.FeedOption

/**
 * HM-Mapping (AC-C42): plug the phone into an outlet, start, then turn breakers off one at a time at the panel. When the
 * phone loses power it sounds an alarm; silence it, then pick the breaker and what the outlet feeds to confirm the link.
 */
@Composable
fun MappingScreen(state: MappingUiState, onEvent: (MappingEvent) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.mapping_title), onBack = { onEvent(MappingEvent.Back) }) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding).testTag("hm_mapping_loading"))
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("hm_mapping_scroll"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.panelName.isNotBlank()) {
                Text(state.panelName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state.phase) {
                MappingPhase.IDLE -> Idle(state, onEvent)
                MappingPhase.WAITING_FOR_POWER -> Status(Icons.Rounded.PowerOff, stringResource(R.string.mapping_waiting_title), stringResource(R.string.mapping_waiting_body), "hm_mapping_waiting") { StopButton(onEvent) }
                MappingPhase.ARMED -> Status(Icons.Rounded.BatteryChargingFull, stringResource(R.string.mapping_armed_title), stringResource(R.string.mapping_armed_body), "hm_mapping_armed") { StopButton(onEvent) }
                MappingPhase.ALARM -> Status(Icons.Rounded.NotificationsActive, stringResource(R.string.mapping_alarm_title), stringResource(R.string.mapping_alarm_body), "hm_mapping_alarm", error = true) {
                    Button(
                        onClick = { onEvent(MappingEvent.Silence) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("hm_mapping_silence"),
                    ) { Text(stringResource(R.string.mapping_silence), style = MaterialTheme.typography.titleLarge) }
                }
                MappingPhase.PICKING -> Picking(state, onEvent)
                MappingPhase.STOPPED -> Status(Icons.Rounded.PowerOff, stringResource(R.string.mapping_stopped_title), stringResource(R.string.mapping_stopped_body), "hm_mapping_stopped") {
                    Button(onClick = { onEvent(MappingEvent.Resume) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_mapping_resume")) {
                        Text(stringResource(R.string.mapping_resume))
                    }
                }
            }
            if (state.confirmed > 0) {
                Text(
                    pluralStringResource(R.plurals.mapping_confirmed_count, state.confirmed, state.confirmed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("hm_mapping_confirmed"),
                )
            }
        }
    }
}

@Composable
private fun Idle(state: MappingUiState, onEvent: (MappingEvent) -> Unit) {
    Text(stringResource(R.string.mapping_how_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    listOf(R.string.mapping_step_1, R.string.mapping_step_2, R.string.mapping_step_3).forEachIndexed { i, res ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
        }
    }
    Text(
        stringResource(if (state.plugged) R.string.mapping_plugged else R.string.mapping_unplugged),
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.plugged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("hm_mapping_power"),
    )
    if (state.breakers.isEmpty()) {
        Text(stringResource(R.string.mapping_no_breakers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Button(
        onClick = { onEvent(MappingEvent.Start) },
        enabled = state.breakers.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_mapping_start"),
    ) { Text(stringResource(R.string.mapping_start)) }
}

@Composable
private fun Status(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, tag: String, error: Boolean = false, action: @Composable () -> Unit) {
    val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().testTag(tag).semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(8.dp))
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = if (error) tint else MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    action()
}

@Composable
private fun StopButton(onEvent: (MappingEvent) -> Unit) {
    OutlinedButton(onClick = { onEvent(MappingEvent.Stop) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_mapping_stop")) {
        Text(stringResource(R.string.mapping_stop))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Picking(state: MappingUiState, onEvent: (MappingEvent) -> Unit) {
    Text(stringResource(R.string.mapping_pick_breaker), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("hm_mapping_breakers")) {
        state.breakers.forEach { b ->
            FilterChip(
                selected = b.id == state.breakerId,
                onClick = { onEvent(MappingEvent.PickBreaker(b.id)) },
                label = { Text(breakerName(b)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("hm_mapping_breaker_${b.number}"),
            )
        }
    }
    Text(stringResource(R.string.mapping_pick_feeds), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Options(stringResource(R.string.mapping_rooms), state.rooms, state.roomId, "room") { onEvent(MappingEvent.PickRoom(it)) }
    Options(stringResource(R.string.mapping_items), state.items, state.itemId, "item") { onEvent(MappingEvent.PickItem(it)) }
    Button(
        onClick = { onEvent(MappingEvent.Save) },
        enabled = state.canSave,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_mapping_save"),
        contentPadding = ButtonDefaults.ContentPadding,
    ) { Text(stringResource(R.string.mapping_save)) }
    TextButton(onClick = { onEvent(MappingEvent.Skip) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_mapping_skip")) {
        Text(stringResource(R.string.mapping_skip))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Options(title: String, options: List<FeedOption>, selected: String?, tag: String, onPick: (String) -> Unit) {
    if (options.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o.id == selected, onClick = { onPick(o.id) },
                label = { Text(o.name) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("hm_mapping_${tag}_chip"),
            )
        }
    }
}

@Composable
private fun breakerName(b: MappingBreaker): String {
    val number = stringResource(R.string.mapping_breaker_number, b.number)
    return if (b.label.isBlank()) number else stringResource(R.string.mapping_breaker_named, number, b.label)
}
