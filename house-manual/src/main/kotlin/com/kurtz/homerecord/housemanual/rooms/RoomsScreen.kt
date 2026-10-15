package com.kurtz.homerecord.housemanual.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun RoomsRoute(viewModel: RoomsViewModel, onOpenRoom: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    RoomsScreen(state) { event ->
        if (event is RoomsEvent.Open) onOpenRoom(event.roomId) else viewModel.onEvent(event)
    }
}

@Composable
fun RoomsScreen(state: RoomsUiState, onEvent: (RoomsEvent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.rooms_title), style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.newName,
                onValueChange = { onEvent(RoomsEvent.NewNameChanged(it)) },
                label = { Text(stringResource(R.string.rooms_add_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("rooms_new_name"),
            )
            Button(
                onClick = { onEvent(RoomsEvent.Add) },
                enabled = state.canAdd,
                modifier = Modifier.heightIn(min = 48.dp).testTag("rooms_add"),
            ) { Text(stringResource(R.string.onboarding_add)) }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.rooms, key = { it.id }) { room ->
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable { onEvent(RoomsEvent.Open(room.id)) }
                        .padding(vertical = 16.dp).testTag("room_row"),
                )
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoomsScreenPreview() {
    val r = { n: String -> Room(n, "h", n, null, 0, 0, Source.HOUSE_MANUAL) }
    HomeRecordTheme { RoomsScreen(RoomsUiState(listOf(r("Kitchen"), r("Garage"))), onEvent = {}) }
}
