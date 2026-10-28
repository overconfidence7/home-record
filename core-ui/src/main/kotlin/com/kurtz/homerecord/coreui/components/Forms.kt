package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Full-screen editor with Close and Save (AC-U6). When [dirty], Close and back ask before discarding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScaffold(
    title: String,
    dirty: Boolean,
    saveEnabled: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = dirty) { confirmDiscard = true }
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = requestClose, modifier = Modifier.testTag("${LocalTagPrefix.current}_close")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = saveEnabled, modifier = Modifier.testTag("${LocalTagPrefix.current}_save")) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) { content() }
    }
    if (confirmDiscard) DiscardChangesDialog(onDiscard = { confirmDiscard = false; onClose() }, onKeepEditing = { confirmDiscard = false })
}

/** "Discard changes?" asked when a dirty form is closed (ui-standards rule 3); every form uses this one. */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        modifier = Modifier.windowTestTags(),
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_discard")) {
                Text(stringResource(R.string.discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing, modifier = Modifier.heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_keep_editing")) {
                Text(stringResource(R.string.keep_editing))
            }
        },
    )
}

/** Text field with a real label, required marker and inline error (ui-standards 21). */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    helper: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (required) stringResource(R.string.required_label, label) else label) },
        supportingText = (error ?: helper)?.let { { Text(it) } },
        isError = error != null,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
    )
}

/** Length input in inches: accepts `12'6"`, `12.5`, `12 6` and metres in metric mode (AC-C4). */
@Composable
fun LengthField(
    label: String,
    inches: Int?,
    onInchesChange: (Int?) -> Unit,
    system: UnitSystem,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var text by rememberSaveable { mutableStateOf(inches?.let { LengthParser.format(it, system) }.orEmpty()) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val parsed = LengthParser.parse(text, system)
    val invalid = touched && text.isNotBlank() && parsed == null
    LabeledField(
        label = label,
        value = text,
        onValueChange = {
            text = it
            touched = true
            onInchesChange(LengthParser.parse(it, system))
        },
        modifier = modifier,
        required = required,
        helper = stringResource(if (system == UnitSystem.IMPERIAL) R.string.length_helper_imperial else R.string.length_helper_metric),
        error = if (invalid) stringResource(R.string.length_error) else null,
        keyboardType = KeyboardType.Text,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
    )
}

/** Money input in cents with a numeric keypad. */
@Composable
fun MoneyField(
    label: String,
    cents: Long?,
    onCentsChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf(cents?.let { formatCents(it) }.orEmpty()) }
    val invalid = text.isNotBlank() && parseCents(text) == null
    LabeledField(
        label = label,
        value = text,
        onValueChange = { text = it; onCentsChange(parseCents(it)) },
        modifier = modifier,
        required = required,
        error = if (invalid) stringResource(R.string.money_error) else null,
        keyboardType = KeyboardType.Decimal,
    )
}

internal fun parseCents(input: String): Long? {
    val cleaned = input.trim().removePrefix("$").replace(",", "")
    if (!Regex("""\d+(\.\d{1,2})?""").matches(cleaned)) return null
    val (whole, frac) = cleaned.split('.').let { it[0] to (it.getOrNull(1) ?: "") }
    return whole.toLong() * 100 + frac.padEnd(2, '0').toLong()
}

internal fun formatCents(cents: Long): String = "%d.%02d".format(cents / 100, cents % 100)

/** ISO `yyyy-MM-dd` date field backed by the Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    isoDate: String?,
    onDateChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = isoDate.orEmpty(),
        onValueChange = {},
        readOnly = true,
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (required) stringResource(R.string.required_label, label) else label) },
        trailingIcon = {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.pick_date, label))
            }
        },
    )
    if (open) {
        val initial = isoDate?.let { runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onDateChange(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() })
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

/** Enum chooser as a segmented row (up to 4 options). Longer lists belong in an [OptionsSheet]. */
@Composable
fun <T> EnumPicker(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    labelOf: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    require(options.size <= 4) { "EnumPicker holds at most 4 options; use OptionsSheet" }
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(labelOf(option)) }
        }
    }
}
