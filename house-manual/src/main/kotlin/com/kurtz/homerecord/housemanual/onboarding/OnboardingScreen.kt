package com.kurtz.homerecord.housemanual.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel, onDone: () -> Unit, onRestore: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    OnboardingScreen(state = state, onRestore = onRestore, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(state: OnboardingUiState, onRestore: () -> Unit = {}, onEvent: (OnboardingEvent) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.homeName,
            onValueChange = { onEvent(OnboardingEvent.HomeNameChanged(it)) },
            label = { Text(stringResource(R.string.onboarding_home_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.onboarding_rooms_heading), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickRoom.entries.forEach { room ->
                FilterChip(
                    selected = room in state.selected,
                    onClick = { onEvent(OnboardingEvent.ToggleRoom(room)) },
                    label = { Text(stringResource(room.label)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            state.customRooms.forEach { name ->
                InputChip(
                    selected = true,
                    onClick = { onEvent(OnboardingEvent.RemoveCustomRoom(name)) },
                    label = { Text(name) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.customInput,
                onValueChange = { onEvent(OnboardingEvent.CustomInputChanged(it)) },
                label = { Text(stringResource(R.string.onboarding_custom_room)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { onEvent(OnboardingEvent.AddCustomRoom) },
                enabled = state.canAddCustom,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.onboarding_add)) }
        }
        Button(
            onClick = { onEvent(OnboardingEvent.Continue) },
            enabled = state.canContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.onboarding_continue)) }
        androidx.compose.material3.TextButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("onboarding_restore"),
        ) { Text(stringResource(R.string.onboarding_restore)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    HomeRecordTheme {
        OnboardingScreen(
            state = OnboardingUiState(homeName = "Maple St", selected = setOf(QuickRoom.KITCHEN), customRooms = listOf("Sunroom")),
            onEvent = {},
        )
    }
}
