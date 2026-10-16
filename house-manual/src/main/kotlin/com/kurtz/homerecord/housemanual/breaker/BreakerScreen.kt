package com.kurtz.homerecord.housemanual.breaker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakerScreen(state: BreakerUiState, onEvent: (BreakerEvent) -> Unit) {
    if (!state.loaded) return
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("breaker_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.breaker_title, state.slot), style = MaterialTheme.typography.headlineMedium)
        if (state.tandemIndex != null) {
            Text(stringResource(R.string.breaker_tandem_half, if (state.tandemIndex == 0) "a" else "b"))
        }
        OutlinedTextField(
            value = state.label,
            onValueChange = { onEvent(BreakerEvent.LabelChanged(it)) },
            label = { Text(stringResource(R.string.breaker_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("breaker_label"),
        )
        OutlinedTextField(
            value = state.amps,
            onValueChange = { onEvent(BreakerEvent.AmpsChanged(it)) },
            label = { Text(stringResource(R.string.breaker_amps)) },
            isError = !com.kurtz.homerecord.core.logic.AmpsRule.isValid(state.amps),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("breaker_amps"),
        )
        if (state.showPoles) {
            Text(stringResource(R.string.breaker_poles), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2).forEach { p ->
                    FilterChip(
                        selected = state.poles == p,
                        onClick = { onEvent(BreakerEvent.PolesChanged(p)) },
                        label = { Text(p.toString()) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("poles_$p"),
                    )
                }
            }
        }
        Text(stringResource(R.string.breaker_type), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BreakerType.entries.forEach { t ->
                FilterChip(
                    selected = state.type == t,
                    onClick = { onEvent(BreakerEvent.TypeChanged(t)) },
                    label = { Text(stringResource(typeLabel(t))) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("type_${t.name}"),
                )
            }
        }
        Text(stringResource(R.string.breaker_confidence), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Confidence.entries.forEach { c ->
                FilterChip(
                    selected = state.confidence == c,
                    onClick = { onEvent(BreakerEvent.ConfidenceChanged(c)) },
                    label = { Text(stringResource(confidenceLabel(c))) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("confidence_${c.name}"),
                )
            }
        }
        if (state.rooms.isNotEmpty()) {
            Text(stringResource(R.string.breaker_rooms), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.rooms.forEach { r ->
                    FilterChip(
                        selected = r.id in state.selectedRoomIds,
                        onClick = { onEvent(BreakerEvent.RoomToggled(r.id)) },
                        label = { Text(r.name) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("room_${r.id}"),
                    )
                }
            }
            Text(stringResource(R.string.breaker_endpoints), style = MaterialTheme.typography.titleMedium)
            state.endpoints.forEachIndexed { i, e ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    val roomName = state.rooms.firstOrNull { it.id == e.roomId }?.name.orEmpty()
                    Text("${stringResource(endpointKindLabel(e.kind))}: ${e.label} ($roomName)", Modifier.weight(1f))
                    TextButton(onClick = { onEvent(BreakerEvent.RemoveEndpoint(i)) }, modifier = Modifier.testTag("endpoint_remove_$i")) {
                        Text(stringResource(R.string.endpoint_remove))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EndpointKind.entries.forEach { k ->
                    FilterChip(
                        selected = state.newEndpointKind == k,
                        onClick = { onEvent(BreakerEvent.EndpointKindChanged(k)) },
                        label = { Text(stringResource(endpointKindLabel(k))) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("endpoint_kind_${k.name}"),
                    )
                }
            }
            OutlinedTextField(
                value = state.newEndpointLabel,
                onValueChange = { onEvent(BreakerEvent.EndpointLabelChanged(it)) },
                label = { Text(stringResource(R.string.endpoint_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("endpoint_label"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.rooms.forEach { r ->
                    FilterChip(
                        selected = state.newEndpointRoomId == r.id,
                        onClick = { onEvent(BreakerEvent.EndpointRoomChanged(r.id)) },
                        label = { Text(r.name) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("endpoint_room_${r.id}"),
                    )
                }
            }
            OutlinedButton(
                onClick = { onEvent(BreakerEvent.AddEndpoint) },
                enabled = state.canAddEndpoint,
                modifier = Modifier.heightIn(min = 48.dp).testTag("endpoint_add"),
            ) { Text(stringResource(R.string.endpoint_add)) }
        }
        state.problem?.let {
            val message = when (it) {
                is SlotProblem.Occupied -> stringResource(R.string.breaker_error_occupied, it.slot)
                SlotProblem.OutOfRange -> stringResource(R.string.breaker_error_range)
            }
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("breaker_error"))
        }
        Button(
            onClick = { onEvent(BreakerEvent.Save) },
            enabled = state.canSave,
            modifier = Modifier.heightIn(min = 48.dp).testTag("breaker_save"),
        ) { Text(stringResource(R.string.save)) }
        if (!state.isNew) {
            OutlinedButton(
                onClick = { onEvent(BreakerEvent.DeleteClicked) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("breaker_delete"),
            ) { Text(stringResource(R.string.room_delete)) }
        }
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(BreakerEvent.DeleteDismissed) },
            title = { Text(stringResource(R.string.breaker_delete_title)) },
            text = { Text(stringResource(R.string.breaker_delete_body, state.label)) },
            confirmButton = {
                TextButton(onClick = { onEvent(BreakerEvent.DeleteConfirmed) }, modifier = Modifier.testTag("breaker_delete_confirm")) {
                    Text(stringResource(R.string.room_delete))
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(BreakerEvent.DeleteDismissed) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

fun typeLabel(type: BreakerType): Int = when (type) {
    BreakerType.STANDARD -> R.string.breaker_type_standard
    BreakerType.GFCI -> R.string.breaker_type_gfci
    BreakerType.AFCI -> R.string.breaker_type_afci
    BreakerType.DUAL -> R.string.breaker_type_dual
}

fun endpointKindLabel(kind: EndpointKind): Int = when (kind) {
    EndpointKind.OUTLET -> R.string.endpoint_kind_outlet
    EndpointKind.LIGHT -> R.string.endpoint_kind_light
    EndpointKind.SWITCH -> R.string.endpoint_kind_switch
    EndpointKind.APPLIANCE -> R.string.endpoint_kind_appliance
    EndpointKind.HARDWIRED -> R.string.endpoint_kind_hardwired
}

fun confidenceLabel(c: Confidence): Int = when (c) {
    Confidence.CONFIRMED -> R.string.confidence_confirmed
    Confidence.LIKELY -> R.string.confidence_likely
    Confidence.UNKNOWN -> R.string.confidence_unknown
}

@Preview(showBackground = true)
@Composable
private fun BreakerPreview() {
    HomeRecordTheme { BreakerScreen(BreakerUiState(loaded = true, slot = 3, label = "Kitchen"), onEvent = {}) }
}
