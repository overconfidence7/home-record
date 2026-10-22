package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.kurtz.homerecord.coreui.components.LinkChip
import com.kurtz.homerecord.coreui.components.LinkChipRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kurtz.homerecord.coreui.components.DateField
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.R

/** FL-JobEdit. Client, address, dates, budget hours and notes; rooms chips; crew and phases follow (T018c2). */
@Composable
fun JobEditScreen(state: JobEditUiState, onEvent: (JobEditEvent) -> Unit, onClose: () -> Unit) {
    if (state.loading) {
        Scaffold { LoadingState(Modifier.padding(it)) }
        return
    }
    val d = state.draft
    FormScaffold(
        title = stringResource(if (state.isNew) R.string.job_edit_new else R.string.job_edit_title),
        dirty = state.dirty,
        saveEnabled = state.canSave,
        onClose = onClose,
        onSave = { onEvent(JobEditEvent.Save) },
    ) {
        LabeledField(stringResource(R.string.job_name), d.name, { onEvent(JobEditEvent.Name(it)) }, Modifier.testTag("fl_jobedit_name"), required = true)
        LabeledField(stringResource(R.string.job_client), d.clientName, { onEvent(JobEditEvent.Client(it)) }, Modifier.testTag("fl_jobedit_client"))
        LabeledField(stringResource(R.string.job_address), d.address, { onEvent(JobEditEvent.Address(it)) }, Modifier.testTag("fl_jobedit_address"), helper = stringResource(R.string.job_address_helper))
        DateField(stringResource(R.string.job_start), d.startDate, { onEvent(JobEditEvent.Start(it)) }, Modifier.testTag("fl_jobedit_start"))
        DateField(stringResource(R.string.job_end), d.endDate, { onEvent(JobEditEvent.End(it)) }, Modifier.testTag("fl_jobedit_end"))
        if (d.datesInvalid) Text(stringResource(R.string.job_dates_error), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        LabeledField(
            stringResource(R.string.job_budget), d.budgetHoursText, { onEvent(JobEditEvent.Budget(it)) }, Modifier.testTag("fl_jobedit_budget"),
            error = if (d.budgetInvalid) stringResource(R.string.job_budget_error) else null, keyboardType = KeyboardType.Number,
        )
        var roomInput by rememberSaveable { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            LabeledField(
                stringResource(R.string.job_rooms), roomInput, { roomInput = it }, Modifier.weight(1f).testTag("fl_jobedit_room_input"),
                helper = stringResource(R.string.job_rooms_helper),
            )
            OutlinedButton(
                onClick = { onEvent(JobEditEvent.AddRoom(roomInput)); roomInput = "" },
                enabled = roomInput.isNotBlank(),
                modifier = Modifier.heightIn(min = 56.dp).testTag("fl_jobedit_room_add"),
            ) { Text(stringResource(R.string.job_rooms_add)) }
        }
        if (d.rooms.isNotEmpty()) {
            LinkChipRow(
                d.rooms.map { LinkChip(it, it) }, onOpen = {}, onRemove = { onEvent(JobEditEvent.RemoveRoom(it.id)) },
                modifier = Modifier.testTag("fl_jobedit_rooms"),
            )
        }
        LabeledField(
            stringResource(R.string.job_notes), d.notes, { onEvent(JobEditEvent.Notes(it)) }, Modifier.testTag("fl_jobedit_notes"),
            imeAction = ImeAction.Default, singleLine = false,
        )
    }
}
