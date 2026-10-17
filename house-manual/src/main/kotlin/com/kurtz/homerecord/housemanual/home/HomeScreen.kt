package com.kurtz.homerecord.housemanual.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.SearchResult
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.rooms.labelRes
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onPanel: () -> Unit,
    onEmergency: () -> Unit,
    onEquipment: () -> Unit,
    onRooms: () -> Unit,
    onImport: () -> Unit,
    onSlot: (Int) -> Unit,
    onRoom: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(state) { event ->
        when (event) {
            is HomeEvent.SearchChanged -> viewModel.onSearchChanged(event.value)
            HomeEvent.OpenPanel -> onPanel()
            HomeEvent.OpenEmergency -> onEmergency()
            HomeEvent.OpenEquipment -> onEquipment()
            HomeEvent.OpenRooms -> onRooms()
            HomeEvent.OpenImport -> onImport()
            is HomeEvent.OpenSlot -> onSlot(event.slot)
            is HomeEvent.OpenRoom -> onRoom(event.roomId)
        }
    }
}

@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.homeName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("home_screen"))
        OutlinedTextField(
            value = state.search,
            onValueChange = { onEvent(HomeEvent.SearchChanged(it)) },
            label = { Text(stringResource(R.string.home_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("home_search"),
        )
        if (state.isSearching) {
            SearchResults(state, onEvent)
            return@Column
        }
        HubCard(
            title = stringResource(R.string.home_card_emergency),
            summary = pluralStringResource(R.plurals.home_shutoff_count, state.shutoffCount, state.shutoffCount),
            tag = "card_emergency",
            emergency = true,
            onClick = { onEvent(HomeEvent.OpenEmergency) },
        )
        HubCard(
            title = stringResource(R.string.home_card_panel),
            summary = if (state.hasPanel) {
                stringResource(R.string.home_panel_summary, state.panelSlotCount, state.percentLabeled)
            } else {
                stringResource(R.string.home_panel_none)
            },
            tag = "card_panel",
            onClick = { onEvent(HomeEvent.OpenPanel) },
        )
        HubCard(
            title = stringResource(R.string.home_card_equipment),
            summary = pluralStringResource(R.plurals.home_equipment_count, state.equipmentCount, state.equipmentCount),
            tag = "card_equipment",
            onClick = { onEvent(HomeEvent.OpenEquipment) },
        )
        HubCard(
            title = stringResource(R.string.home_card_rooms),
            summary = pluralStringResource(R.plurals.home_room_count, state.roomCount, state.roomCount),
            tag = "card_rooms",
            onClick = { onEvent(HomeEvent.OpenRooms) },
        )
        HubCard(
            title = stringResource(R.string.home_card_import),
            summary = stringResource(R.string.home_import_summary),
            tag = "card_import",
            onClick = { onEvent(HomeEvent.OpenImport) },
        )
    }
}

@Composable
private fun SearchResults(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    if (state.results.isEmpty()) {
        Text(stringResource(R.string.search_nothing_found), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("search_empty"))
        return
    }
    Column(Modifier.testTag("search_results"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.results.forEachIndexed { index, result ->
            val (title, subtitle, onClick) = when (result) {
                is SearchResult.BreakerHit -> Triple(
                    result.breaker.label.ifBlank { stringResource(R.string.panel_slot_empty) },
                    stringResource(R.string.search_breaker_subtitle, result.breaker.slots.min()) +
                        result.matchedEndpoints.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · "),
                    { result.breaker.slots.minOrNull()?.let { onEvent(HomeEvent.OpenSlot(it)) } ?: Unit },
                )
                is SearchResult.EndpointHit -> Triple(
                    result.endpoint.label,
                    stringResource(R.string.search_endpoint_subtitle, state.roomNames[result.endpoint.roomId].orEmpty()),
                    { onEvent(HomeEvent.OpenRoom(result.endpoint.roomId)) },
                )
                is SearchResult.EquipmentHit -> Triple(
                    listOfNotNull(result.equipment.make, result.equipment.model).joinToString(" ").ifBlank { stringResource(result.equipment.category.labelRes()) },
                    stringResource(R.string.search_equipment_subtitle),
                    { onEvent(HomeEvent.OpenEquipment) },
                )
                is SearchResult.ShutoffHit -> Triple(
                    stringResource(result.shutoff.kind.labelRes()),
                    stringResource(R.string.search_shutoff_subtitle),
                    { onEvent(HomeEvent.OpenEmergency) },
                )
            }
            Card(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("search_result_$index").clickable(onClick = onClick)) {
                Column(Modifier.padding(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HubCard(title: String, summary: String, tag: String, onClick: () -> Unit, emergency: Boolean = false) {
    Card(
        colors = if (emergency) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag(tag).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeRecordTheme {
        HomeScreen(
            HomeUiState(homeName = "Maple St", hasPanel = true, panelSlotCount = 30, percentLabeled = 40, shutoffCount = 3, equipmentCount = 5, roomCount = 8),
            onEvent = {},
        )
    }
}
