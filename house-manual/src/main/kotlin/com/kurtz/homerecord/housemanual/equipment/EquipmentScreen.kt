package com.kurtz.homerecord.housemanual.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.kurtz.homerecord.core.logic.WarrantyStatus
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.rooms.labelRes
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun EquipmentRoute(viewModel: EquipmentViewModel, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    EquipmentScreen(state) { event ->
        when (event) {
            EquipmentEvent.Add -> onAdd()
            is EquipmentEvent.Open -> onOpen(event.equipmentId)
        }
    }
}

@Composable
fun EquipmentScreen(state: EquipmentUiState, onEvent: (EquipmentEvent) -> Unit) {
    if (!state.loaded) return
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.equipment_title), style = MaterialTheme.typography.headlineMedium)
        Button(
            onClick = { onEvent(EquipmentEvent.Add) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("equipment_add"),
        ) { Text(stringResource(R.string.equipment_add)) }
        if (state.items.isEmpty()) {
            Text(
                stringResource(R.string.equipment_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("equipment_empty"),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.items, key = { it.equipment.id }) { item ->
                    EquipmentRow(item, state.roomNames) { onEvent(EquipmentEvent.Open(item.equipment.id)) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EquipmentRow(item: EquipmentItem, roomNames: Map<String, String>, onClick: () -> Unit) {
    val e = item.equipment
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp).testTag("equipment_row"),
    ) {
        Text(stringResource(e.category.labelRes()), style = MaterialTheme.typography.titleMedium)
        val name = listOfNotNull(e.make, e.model).joinToString(" ")
        if (name.isNotBlank()) Text(name)
        e.roomId?.let { roomNames[it] }?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        item.ageYears?.let { Text(pluralStringResource(R.plurals.equipment_age, it, it), modifier = Modifier.testTag("equipment_age")) }
        when (val w = item.warranty) {
            is WarrantyStatus.Until -> Text(stringResource(R.string.equipment_warranty_until, w.date), modifier = Modifier.testTag("equipment_warranty"))
            WarrantyStatus.Expired -> Text(stringResource(R.string.equipment_warranty_expired), modifier = Modifier.testTag("equipment_warranty"))
            null -> Unit
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EquipmentScreenPreview() {
    val e = Equipment("1", "h", null, EquipmentCategory.WATER_HEATER, "Rheem", "XE50", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)
    HomeRecordTheme { EquipmentScreen(EquipmentUiState(true, listOf(EquipmentItem(e, 4, WarrantyStatus.Expired)))) {} }
}
