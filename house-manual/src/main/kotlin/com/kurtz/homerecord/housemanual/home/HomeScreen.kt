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
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onPanel: () -> Unit,
    onEmergency: () -> Unit,
    onEquipment: () -> Unit,
    onRooms: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(state) { event ->
        when (event) {
            is HomeEvent.SearchChanged -> viewModel.onSearchChanged(event.value)
            HomeEvent.OpenPanel -> onPanel()
            HomeEvent.OpenEmergency -> onEmergency()
            HomeEvent.OpenEquipment -> onEquipment()
            HomeEvent.OpenRooms -> onRooms()
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
