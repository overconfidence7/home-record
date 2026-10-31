package com.kurtz.homerecord.fieldlog.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.housemodel.blueprint.BlueprintCanvas
import com.kurtz.homerecord.housemodel.blueprint.BlueprintPin

/**
 * "Pin on plan" over the task form (AC-F35): the job's floor plan, one floor at a time; a tap drops the task's pin
 * there (or moves it). The pin is part of the form, so Back and Done return to the form and Save keeps it.
 */
@Composable
fun TaskPinPicker(state: TaskFormUiState, onEvent: (TaskFormEvent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onEvent(TaskFormEvent.ClosePin) }
    val floor = state.pinFloor
    val pin = state.draft.planPin?.takeIf { it.floorId == floor?.id }
    val title = state.draft.title.ifBlank { stringResource(R.string.task_form_pin) }
    Box(modifier.testTag("fl_task_pin")) {
        ScreenScaffold(
            title = stringResource(R.string.task_form_pin),
            subtitle = state.draft.title.takeIf { it.isNotBlank() },
            onBack = { onEvent(TaskFormEvent.ClosePin) },
            actions = {
                TextButton({ onEvent(TaskFormEvent.ClosePin) }, Modifier.heightIn(min = 48.dp).testTag("fl_task_pin_done")) {
                    Text(stringResource(R.string.task_pin_done))
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.plan.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.plan.reversed(), key = { it.id }) { f ->
                            FilterChip(
                                selected = f.id == floor?.id,
                                onClick = { onEvent(TaskFormEvent.PinFloor(f.id)) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_task_pin_floor_${f.id}"),
                                label = { Text(f.name) },
                            )
                        }
                    }
                }
                Text(
                    stringResource(if (state.draft.planPin == null) R.string.task_pin_hint else R.string.task_pin_hint_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                BlueprintCanvas(
                    rooms = floor?.rooms.orEmpty(),
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("fl_task_pin_plan"),
                    pins = listOfNotNull(pin?.let { BlueprintPin("pin", title, it.xIn, it.yIn, 1) }),
                    onPlanTap = { x, y -> onEvent(TaskFormEvent.PlacePin(x, y)) },
                )
                if (state.draft.planPin != null) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton({ onEvent(TaskFormEvent.RemovePin) }, Modifier.heightIn(min = 48.dp).testTag("fl_task_pin_remove")) {
                            Icon(Icons.Rounded.Delete, null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.task_pin_remove))
                        }
                    }
                } else {
                    Box(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
