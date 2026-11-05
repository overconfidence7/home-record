package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.EnumPicker
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.ui.itemIcon
import com.kurtz.homerecord.core.model.ItemCategory

/** HM-Breaker (AC-C40): label, amps, poles, type, the rooms and items it feeds; Delete on a saved breaker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakerScreen(state: BreakerUiState, onEvent: (BreakerEvent) -> Unit, onClose: () -> Unit) {
    val placeholder: (@Composable (Modifier) -> Unit)? = when {
        state.missing -> { m ->
            EmptyState(
                icon = Icons.Rounded.ElectricalServices,
                headline = stringResource(R.string.breaker_missing_title),
                body = stringResource(R.string.breaker_missing_body),
                actionLabel = null, onAction = {},
                modifier = m.testTag("hm_breaker_missing"),
            )
        }
        state.loading -> { m -> LoadingState(m.testTag("hm_breaker_loading")) }
        else -> null
    }
    FormScaffold(
        title = stringResource(if (state.isNew) R.string.breaker_new else R.string.breaker_edit),
        dirty = state.dirty,
        saveEnabled = true,
        onClose = onClose,
        onSave = { onEvent(BreakerEvent.Save) },
        placeholder = placeholder,
    ) {
        val d = state.draft
        val slots = state.slots
        Text(
            when {
                d.tandem -> stringResource(R.string.breaker_slot_tandem, d.startSlot)
                slots != null && slots.size == 2 -> stringResource(R.string.breaker_slots_two, slots[0], slots[1])
                else -> stringResource(R.string.breaker_slot_one, d.startSlot)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("hm_breaker_slots"),
        )
        LabeledField(
            label = stringResource(R.string.breaker_label),
            value = d.label,
            onValueChange = { onEvent(BreakerEvent.Label(it)) },
            helper = stringResource(R.string.breaker_label_helper),
            modifier = Modifier.testTag("hm_breaker_label"),
        )
        LabeledField(
            label = stringResource(R.string.breaker_amps),
            value = d.amps,
            onValueChange = { onEvent(BreakerEvent.Amps(it)) },
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            error = if (state.ampsError) stringResource(R.string.breaker_amps_error) else null,
            helper = stringResource(R.string.breaker_amps_helper),
            modifier = Modifier.testTag("hm_breaker_amps"),
        )
        if (!d.tandem) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(R.string.breaker_poles))
                EnumPicker(
                    options = listOf(1, 2),
                    selected = d.poles,
                    onSelect = { onEvent(BreakerEvent.Poles(it)) },
                    labelOf = { stringResource(if (it == 1) R.string.breaker_poles_one else R.string.breaker_poles_two) },
                    tagOf = { "hm_breaker_poles_$it" },
                )
                placementError(state.placement, state.slotCount)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp).testTag("hm_breaker_slot_error"),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.breaker_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BreakerType.entries.forEach { t ->
                    val on = t == d.type
                    FilterChip(
                        selected = on,
                        onClick = { onEvent(BreakerEvent.Type(t)) },
                        label = { Text(typeName(t)) },
                        leadingIcon = if (on) { { Icon(Icons.Rounded.Check, contentDescription = null) } } else null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_type_${t.name.lowercase()}"),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.breaker_rooms))
            if (state.rooms.isEmpty()) {
                Text(stringResource(R.string.breaker_rooms_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.rooms.forEach { r ->
                    val on = r.id in d.roomIds
                    FilterChip(
                        selected = on,
                        onClick = { onEvent(BreakerEvent.ToggleRoom(r.id)) },
                        label = { Text(r.name) },
                        leadingIcon = if (on) { { Icon(Icons.Rounded.Check, contentDescription = null) } } else null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_room_${r.id}"),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.breaker_items))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.selectedItems.forEach { item ->
                    InputChip(
                        selected = false,
                        onClick = { onEvent(BreakerEvent.ToggleItem(item.id)) },
                        label = { Text(item.name) },
                        leadingIcon = { Icon(itemIcon(ItemCategory.subtype(item.subtypeId)?.iconName ?: "Category"), contentDescription = null) },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.breaker_item_remove, item.name)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_item_${item.id}"),
                    )
                }
                AssistChip(
                    onClick = { onEvent(BreakerEvent.PickItems) },
                    label = { Text(stringResource(R.string.breaker_items_add)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_items_add"),
                )
            }
        }
        if (!state.isNew) {
            OutlinedButton(
                onClick = { onEvent(BreakerEvent.Delete) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_breaker_delete"),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.breaker_delete))
            }
        }
    }
    if (state.pickingItems) ItemPickerSheet(state, onEvent)
    if (state.confirmDelete) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(BreakerEvent.CancelDelete) },
            title = { Text(stringResource(R.string.breaker_delete_title)) },
            text = { Text(stringResource(R.string.breaker_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(BreakerEvent.ConfirmDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_delete_confirm")) {
                    Text(stringResource(R.string.breaker_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(BreakerEvent.CancelDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_delete_cancel")) {
                    Text(stringResource(com.kurtz.homerecord.coreui.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun typeName(t: BreakerType): String = stringResource(
    when (t) {
        BreakerType.STANDARD -> R.string.breaker_type_standard
        BreakerType.GFCI -> R.string.panel_type_gfci
        BreakerType.AFCI -> R.string.panel_type_afci
        BreakerType.DUAL_FUNCTION -> R.string.panel_type_dual
    },
)

@Composable
private fun placementError(p: SlotPlacement?, slotCount: Int): String? = when (p) {
    null, is SlotPlacement.Ok -> null
    SlotPlacement.OffPanel -> stringResource(R.string.breaker_error_off_panel, slotCount)
    is SlotPlacement.Taken -> stringResource(R.string.breaker_error_taken, p.slots.joinToString(", "))
    SlotPlacement.TandemNeedsOnePole -> stringResource(R.string.breaker_error_tandem)
}

/** "Items it feeds" picker (rule 20): a searchable list of the home's items, ticked ones already linked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemPickerSheet(state: BreakerUiState, onEvent: (BreakerEvent) -> Unit) {
    ModalBottomSheet(
        modifier = Modifier.windowTestTags(),
        onDismissRequest = { onEvent(BreakerEvent.ClosePicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ItemPickerContent(state.items, state.draft.itemIds, onEvent)
    }
}

/** The picker's body; previews and tests draw it without the sheet. */
@Composable
fun ItemPickerContent(items: List<ItemRow>, picked: Set<String>, onEvent: (BreakerEvent) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = items.filter { query.isBlank() || it.name.contains(query.trim(), true) || it.roomName.contains(query.trim(), true) }
    Column(Modifier.widthIn(max = 640.dp).navigationBarsPadding()) {
        Text(
            stringResource(R.string.breaker_items),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.breaker_items_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("hm_breaker_items_search"),
        )
        if (shown.isEmpty()) {
            Text(
                stringResource(if (items.isEmpty()) R.string.breaker_items_none else R.string.breaker_items_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.testTag("hm_breaker_items_list")) {
            items(shown, key = { it.id }) { item ->
                val on = item.id in picked
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = item.roomName.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    leadingContent = { Icon(itemIcon(ItemCategory.subtype(item.subtypeId)?.iconName ?: "Category"), contentDescription = null) },
                    trailingContent = { Checkbox(checked = on, onCheckedChange = null) },
                    modifier = Modifier.clickable { onEvent(BreakerEvent.ToggleItem(item.id)) }.testTag("hm_breaker_pick_${item.id}"),
                )
            }
        }
        TextButton(
            onClick = { onEvent(BreakerEvent.ClosePicker) },
            modifier = Modifier.align(androidx.compose.ui.Alignment.End).padding(8.dp).heightIn(min = 48.dp).testTag("hm_breaker_items_done"),
        ) { Text(stringResource(R.string.breaker_items_done)) }
    }
}
