package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.MoneyField
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R

/**
 * Full-screen change order form (FL-ChangeOrder, AC-F37, rule 15): title, client, description typed or spoken, line
 * items with quantity and price, and the total MoneyMath works out. Close asks before dropping typed changes (rule 3);
 * a Draft can be deleted after a confirm (rule 16); a Signed order opens locked (AC-F38).
 */
@Composable
fun ChangeOrderFormScreen(state: ChangeOrderFormUiState, onEvent: (ChangeOrderFormEvent) -> Unit, modifier: Modifier = Modifier) {
    val editable = !state.locked && !state.working
    FormScaffold(
        title = stringResource(if (state.isNew) R.string.co_form_new else R.string.co_form_edit),
        dirty = state.dirty && !state.done && !state.locked,
        saveEnabled = state.canSave,
        onClose = { onEvent(ChangeOrderFormEvent.Close) },
        onSave = { onEvent(ChangeOrderFormEvent.Save) },
        modifier = modifier.testTag("fl_co_form"),
        placeholder = when {
            state.loading -> { m -> LoadingState(m.fillMaxSize()) }
            state.failed -> { m -> ErrorState(stringResource(R.string.co_form_failed), { onEvent(ChangeOrderFormEvent.Retry) }, m.fillMaxSize()) }
            else -> null
        },
    ) {
        if (state.locked) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fl_co_form_locked"), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.co_form_signed), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Field(
            label = stringResource(R.string.co_form_title), value = state.draft.title, editable = editable,
            onChange = { onEvent(ChangeOrderFormEvent.Title(it)) }, tag = "fl_co_form_title", required = true,
            helper = stringResource(R.string.co_form_title_help),
            error = if (state.titleError) stringResource(R.string.co_form_title_error) else null,
        )
        Field(
            label = stringResource(R.string.co_form_client), value = state.draft.clientName, editable = editable,
            onChange = { onEvent(ChangeOrderFormEvent.Client(it)) }, tag = "fl_co_form_client",
        )
        Field(
            label = stringResource(R.string.co_form_description), value = state.draft.description, editable = editable,
            onChange = { onEvent(ChangeOrderFormEvent.Description(it)) }, tag = "fl_co_form_description", singleLine = false,
        )
        if (state.canDictate && editable) {
            val speak = stringResource(R.string.co_form_speak_description)
            OutlinedButton(
                onClick = { onEvent(ChangeOrderFormEvent.Speak) },
                modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = speak }.testTag("fl_co_form_speak"),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.co_form_speak))
            }
        }
        Text(
            stringResource(R.string.co_form_lines),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        if (state.draft.lines.isEmpty()) {
            Text(stringResource(R.string.co_form_no_lines), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.draft.lines.forEachIndexed { index, line -> LineCard(index, line, editable, onEvent) }
        if (editable) {
            OutlinedButton(
                onClick = { onEvent(ChangeOrderFormEvent.AddLine) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("fl_co_form_add_line"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.co_form_line_add))
            }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) {}.testTag("fl_co_form_total"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.co_form_total), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(dollars(state.draft.totalCents), style = MaterialTheme.typography.titleLarge)
        }
        if (!state.isNew && !state.locked) {
            OutlinedButton(
                onClick = { onEvent(ChangeOrderFormEvent.AskDelete) },
                enabled = !state.working,
                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_co_form_delete"),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.co_form_delete))
            }
        }
    }
    if (state.confirmDelete) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ChangeOrderFormEvent.DismissDelete) },
            title = { Text(stringResource(R.string.co_form_delete_title)) },
            text = { Text(stringResource(R.string.co_form_delete_body, state.initial.title)) },
            confirmButton = {
                TextButton({ onEvent(ChangeOrderFormEvent.Delete) }, Modifier.heightIn(min = 48.dp).testTag("fl_co_form_delete_confirm")) {
                    Text(stringResource(R.string.co_form_delete_confirm))
                }
            },
            dismissButton = {
                TextButton({ onEvent(ChangeOrderFormEvent.DismissDelete) }, Modifier.heightIn(min = 48.dp).testTag("fl_co_form_delete_cancel")) {
                    Text(stringResource(R.string.co_form_cancel))
                }
            },
        )
    }
}

@Composable
private fun Field(
    label: String, value: String, editable: Boolean, onChange: (String) -> Unit, tag: String,
    required: Boolean = false, helper: String? = null, error: String? = null, singleLine: Boolean = true,
) {
    if (editable) {
        LabeledField(
            label = label, value = value, onValueChange = onChange, modifier = Modifier.testTag(tag), required = required,
            helper = helper, error = error, singleLine = singleLine, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        )
    } else if (value.isNotBlank()) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** One line item: what, quantity, price each, its amount and Remove. Quantity and price wrap under the item at 200% text. */
@Composable
private fun LineCard(index: Int, line: FormLine, editable: Boolean, onEvent: (ChangeOrderFormEvent) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("fl_co_form_line_$index")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editable) {
                LabeledField(
                    label = stringResource(R.string.co_form_line_description), value = line.description,
                    onValueChange = { onEvent(ChangeOrderFormEvent.LineDescription(line.id, it)) },
                    modifier = Modifier.testTag("fl_co_form_line_${index}_description"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = line.qty,
                        onValueChange = { onEvent(ChangeOrderFormEvent.LineQty(line.id, it)) },
                        label = { Text(stringResource(R.string.co_form_line_qty)) },
                        isError = line.qtyError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.widthIn(min = 96.dp, max = 120.dp).testTag("fl_co_form_line_${index}_qty"),
                    )
                    MoneyField(
                        label = stringResource(R.string.co_form_line_price), text = line.price,
                        onTextChange = { onEvent(ChangeOrderFormEvent.LinePrice(line.id, it)) },
                        modifier = Modifier.widthIn(min = 160.dp, max = 220.dp).testTag("fl_co_form_line_${index}_price"),
                    )
                }
                // Under the row at full width, so the message reads whole at 200% text (rule 21, rule 32).
                if (line.qtyError) {
                    Text(
                        stringResource(R.string.co_form_line_qty_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("fl_co_form_line_${index}_qty_error"),
                    )
                }
            } else {
                Text(line.description, style = MaterialTheme.typography.bodyLarge)
                Text("${line.qty} × ${line.priceCents?.let(::dollars).orEmpty()}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.co_form_line_amount, line.amountCents?.let(::dollars) ?: "–"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).testTag("fl_co_form_line_${index}_amount"),
                )
                if (editable) {
                    val remove = stringResource(R.string.co_form_line_remove_description, index + 1)
                    IconButton(
                        onClick = { onEvent(ChangeOrderFormEvent.RemoveLine(line.id)) },
                        modifier = Modifier.semantics { contentDescription = remove }.testTag("fl_co_form_line_${index}_remove"),
                    ) { Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null) }
                }
            }
        }
    }
}
