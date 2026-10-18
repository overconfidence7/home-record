package com.kurtz.homerecord.housemanual.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurtz.homerecord.core.mapping.MappingState
import com.kurtz.homerecord.housemanual.R

@Composable
fun MappingRoute(viewModel: MappingViewModel) {
    val state by viewModel.state.collectAsState()
    // AC-36: keep the screen on while armed or alarming; cleared when leaving the screen.
    val keepOn = state.mapping == MappingState.Armed || state.mapping == MappingState.Alarm
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    MappingScreen(state, viewModel::onEvent)
}

@Composable
fun MappingScreen(state: MappingUiState, onEvent: (MappingEvent) -> Unit) {
    if (!state.loaded) return
    when (state.mapping) {
        MappingState.Alarm -> AlarmPanel(onEvent)
        MappingState.Confirm -> ConfirmPanel(state, onEvent)
        else -> StartPanel(state, onEvent)
    }
}

@Composable
private fun StartPanel(state: MappingUiState, onEvent: (MappingEvent) -> Unit) {
    val armed = state.mapping == MappingState.Armed
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.mapping_title), style = MaterialTheme.typography.headlineMedium)
        if (state.saved) {
            Text(stringResource(R.string.mapping_saved), modifier = Modifier.testTag("mapping_saved"))
        }
        val status = when (state.mapping) {
            MappingState.Armed -> R.string.mapping_armed
            MappingState.PluggedIn -> R.string.mapping_ready
            else -> R.string.mapping_plug_in
        }
        Text(
            stringResource(status),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("mapping_status"),
        )
        if (!armed) {
            Button(
                onClick = { onEvent(MappingEvent.Start) },
                enabled = state.canStart,
                modifier = Modifier.heightIn(min = 56.dp).testTag("mapping_start"),
            ) { Text(stringResource(R.string.mapping_start)) }
        }
    }
}

@Composable
private fun AlarmPanel(onEvent: (MappingEvent) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(24.dp).testTag("mapping_alarm"),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.mapping_power_lost),
            color = MaterialTheme.colorScheme.onError,
            fontSize = 44.sp,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { onEvent(MappingEvent.Stop) },
            modifier = Modifier.heightIn(min = 72.dp).fillMaxWidth().testTag("mapping_stop"),
        ) { Text(stringResource(R.string.mapping_stop), fontSize = 24.sp) }
    }
}

@Composable
private fun ConfirmPanel(state: MappingUiState, onEvent: (MappingEvent) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("mapping_confirm"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.mapping_which_breaker), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        if (state.breakers.isEmpty()) item { Text(stringResource(R.string.mapping_no_breakers)) }
        items(state.breakers, key = { it.id }) { b ->
            FilterChip(
                selected = state.selectedBreakerId == b.id,
                onClick = { onEvent(MappingEvent.BreakerPicked(b.id)) },
                label = { Text(stringResource(R.string.mapping_breaker_row, b.slots.min(), b.label)) },
                modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth().testTag("mapping_breaker_${b.id}"),
            )
        }
        item { Text(stringResource(R.string.mapping_which_room), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        items(state.rooms, key = { "room-" + it.id }) { r ->
            FilterChip(
                selected = state.selectedRoomId == r.id,
                onClick = { onEvent(MappingEvent.RoomPicked(r.id)) },
                label = { Text(r.name) },
                modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth().testTag("mapping_room_${r.id}"),
            )
        }
        if (state.selectedRoomId != null) {
            item { Text(stringResource(R.string.mapping_which_outlet), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
            items(state.endpoints, key = { "ep-" + it.id }) { e ->
                FilterChip(
                    selected = state.selectedEndpointId == e.id,
                    onClick = { onEvent(MappingEvent.EndpointPicked(e.id)) },
                    label = { Text(e.label) },
                    modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth().testTag("mapping_endpoint_${e.id}"),
                )
            }
            item {
                OutlinedTextField(
                    value = state.newOutletLabel.orEmpty(),
                    onValueChange = { onEvent(MappingEvent.NewOutletChanged(it)) },
                    label = { Text(stringResource(R.string.mapping_new_outlet)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mapping_new_outlet"),
                )
            }
        }
        item {
            Box(Modifier.padding(vertical = 16.dp)) {
                Button(
                    onClick = { onEvent(MappingEvent.Save) },
                    enabled = state.canSave,
                    modifier = Modifier.heightIn(min = 56.dp).fillMaxWidth().testTag("mapping_save"),
                ) { Text(stringResource(R.string.mapping_save)) }
            }
        }
    }
}
