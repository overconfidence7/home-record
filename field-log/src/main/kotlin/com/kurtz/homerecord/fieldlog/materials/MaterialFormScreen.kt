package com.kurtz.homerecord.fieldlog.materials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.material.MaterialCategory
import com.kurtz.homerecord.coreui.components.DateField
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.MoneyField
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R

/** Add or edit a receipt (AC-F44): job, vendor, amount, category, date, note; Delete on a saved one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialFormScreen(state: MaterialFormUiState, onEvent: (MaterialFormEvent) -> Unit, onClose: () -> Unit) {
    val placeholder: (@Composable (Modifier) -> Unit)? = when {
        state.loading -> { m -> LoadingState(m) }
        state.failed -> { m ->
            ErrorState(
                message = stringResource(R.string.material_form_missing),
                onRetry = { onEvent(MaterialFormEvent.Retry) },
                modifier = m.testTag("fl_material_form_failed"),
            )
        }
        else -> null
    }
    FormScaffold(
        title = stringResource(if (state.isNew) R.string.material_form_new else R.string.material_form_edit),
        dirty = state.dirty,
        saveEnabled = true,
        onClose = onClose,
        onSave = { onEvent(MaterialFormEvent.Save) },
        placeholder = placeholder,
    ) {
        val d = state.draft
        val jobError = state.showErrors && state.jobMissing
        Column {
            Text(
                stringResource(R.string.material_form_job),
                style = MaterialTheme.typography.labelLarge,
                color = if (jobError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedCard {
                RecordListItem(
                    headline = state.jobName ?: stringResource(R.string.material_form_pick_job),
                    leading = { Icon(Icons.Rounded.Work, contentDescription = null) },
                    onClick = { onEvent(MaterialFormEvent.PickJob) },
                    modifier = Modifier.testTag("fl_material_form_job"),
                )
            }
            if (jobError) {
                Text(
                    stringResource(if (state.jobs.isEmpty()) R.string.material_form_no_jobs else R.string.material_form_job_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp).testTag("fl_material_form_job_error"),
                )
            }
        }
        LabeledField(
            label = stringResource(R.string.material_form_vendor),
            value = d.vendor,
            onValueChange = { onEvent(MaterialFormEvent.Vendor(it)) },
            helper = stringResource(R.string.material_form_vendor_helper),
            modifier = Modifier.testTag("fl_material_form_vendor"),
        )
        MoneyField(
            label = stringResource(R.string.material_form_amount),
            text = d.amount,
            onTextChange = { onEvent(MaterialFormEvent.Amount(it)) },
            required = true,
            error = if (state.showErrors && state.amountMissing && d.amount.isBlank()) stringResource(R.string.material_form_amount_error) else null,
            modifier = Modifier.testTag("fl_material_form_amount"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.material_form_category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MaterialCategory.entries.forEach { c ->
                    val on = c == d.category
                    FilterChip(
                        selected = on,
                        onClick = { onEvent(MaterialFormEvent.Category(c)) },
                        label = { Text(categoryLabel(c)) },
                        leadingIcon = { Icon(if (on) Icons.Rounded.Check else categoryIcon(c), contentDescription = null) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("fl_material_form_cat_${c.name.lowercase()}"),
                    )
                }
            }
        }
        DateField(
            label = stringResource(R.string.material_form_date),
            isoDate = d.date,
            onDateChange = { iso -> iso?.let { onEvent(MaterialFormEvent.Date(it)) } },
            modifier = Modifier.testTag("fl_material_form_date"),
        )
        LabeledField(
            label = stringResource(R.string.material_form_note),
            value = d.note,
            onValueChange = { onEvent(MaterialFormEvent.Note(it)) },
            singleLine = false,
            imeAction = ImeAction.Default,
            modifier = Modifier.testTag("fl_material_form_note"),
        )
        if (state.saveFailed) {
            Text(
                stringResource(R.string.material_form_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("fl_material_form_save_failed"),
            )
        }
        if (!state.isNew) {
            OutlinedButton(
                onClick = { onEvent(MaterialFormEvent.AskDelete) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_material_form_delete"),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.material_form_delete))
            }
        }
    }
    if (state.pickingJob) {
        OptionsSheet(
            title = stringResource(R.string.material_form_pick_job),
            options = state.jobs.map { SheetOption(it.id, it.name, Icons.Rounded.Work) },
            onDismiss = { onEvent(MaterialFormEvent.DismissJob) },
            onPick = { onEvent(MaterialFormEvent.Job(it.id)) },
        )
    }
    if (state.confirmDelete) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(MaterialFormEvent.DismissDelete) },
            title = { Text(stringResource(R.string.material_form_delete_title)) },
            text = { Text(stringResource(R.string.material_form_delete_body)) },
            confirmButton = {
                TextButton({ onEvent(MaterialFormEvent.Delete) }, Modifier.testTag("fl_material_form_delete_confirm")) { Text(stringResource(R.string.co_form_delete_confirm)) }
            },
            dismissButton = {
                TextButton({ onEvent(MaterialFormEvent.DismissDelete) }) { Text(stringResource(com.kurtz.homerecord.coreui.R.string.cancel)) }
            },
        )
    }
}
