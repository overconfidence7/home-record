package com.kurtz.homerecord.housemanual.items

import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import com.kurtz.homerecord.housemanual.FormScreenDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.ServiceRecordEntity
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.coreui.components.DateField
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.upkeep.ConsumableDraft
import com.kurtz.homerecord.housemodel.upkeep.ConsumableField
import com.kurtz.homerecord.housemodel.upkeep.ServiceDraft
import com.kurtz.homerecord.housemodel.upkeep.ServiceField
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Consumables on the item page (AC-C35): what to buy, how many, and where to reorder. */
@Composable
fun ConsumablesCard(rows: List<ConsumableEntity>, onEvent: (ItemDetailEvent) -> Unit) {
    UpkeepCard(
        title = stringResource(R.string.item_detail_consumables),
        addLabel = stringResource(R.string.item_detail_consumable_add),
        addTag = "hm_item_detail_consumable_add",
        onAdd = { onEvent(ItemDetailEvent.OpenConsumable(null)) },
        emptyText = stringResource(R.string.item_detail_consumables_empty),
        isEmpty = rows.isEmpty(),
    ) {
        rows.forEach { c ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.spec, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.item_detail_consumable_line, kindLabel(c.kind), c.quantity),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                c.reorderUrl?.let { url ->
                    AssistChip(
                        onClick = { onEvent(ItemDetailEvent.OpenLink(url)) }, label = { Text(stringResource(R.string.item_detail_consumable_reorder)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_consumable_reorder"),
                    )
                }
                TextButton(onClick = { onEvent(ItemDetailEvent.OpenConsumable(c.id)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_consumable_edit")) {
                    Text(stringResource(R.string.item_detail_edit))
                }
            }
        }
    }
}

/** Service history on the item page, newest first (AC-C36). */
@Composable
fun ServiceCard(rows: List<ServiceRecordEntity>, contacts: List<ContactEntity>, onEvent: (ItemDetailEvent) -> Unit) {
    UpkeepCard(
        title = stringResource(R.string.item_detail_service),
        addLabel = stringResource(R.string.item_detail_service_add),
        addTag = "hm_item_detail_service_add",
        onAdd = { onEvent(ItemDetailEvent.OpenService(null)) },
        emptyText = stringResource(R.string.item_detail_service_empty),
        isEmpty = rows.isEmpty(),
    ) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.description, style = MaterialTheme.typography.bodyLarge)
                    val cost = r.costCents?.let { NumberFormat.getCurrencyInstance(Locale.US).format(it / 100.0) }
                    Text(
                        listOfNotNull(shortDate(r.date), cost, contacts.firstOrNull { it.id == r.contactId }?.name).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onEvent(ItemDetailEvent.OpenService(r.id)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_service_edit")) {
                    Text(stringResource(R.string.item_detail_edit))
                }
            }
        }
    }
}

@Composable
private fun UpkeepCard(
    title: String,
    addLabel: String,
    addTag: String,
    onAdd: () -> Unit,
    emptyText: String,
    isEmpty: Boolean,
    rows: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (isEmpty) Text(emptyText, style = MaterialTheme.typography.bodyLarge) else rows()
            OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp).testTag(addTag)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(addLabel, Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** The open upkeep form as a bottom sheet; nothing when neither form is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpkeepFormSheet(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    val c = state.consumableForm
    val s = state.serviceForm
    if (c == null && s == null) return
    val title = stringResource(
        if (c != null) (if (c.id == null) R.string.item_detail_consumable_add else R.string.item_detail_consumable_edit_title)
        else (if (s?.id == null) R.string.item_detail_service_add else R.string.item_detail_service_edit_title),
    )
    FormScreenDialog(
        title = title,
        saveTag = if (c != null) "hm_item_detail_consumable_save" else "hm_item_detail_service_save",
        dirty = state.formDirty,
        saveEnabled = state.formCanSave,
        onClose = { onEvent(ItemDetailEvent.CloseForm) },
        onSave = { onEvent(if (c != null) ItemDetailEvent.SaveConsumable else ItemDetailEvent.SaveService) },
    ) {
        if (c != null) ConsumableFormContent(c, state.showFormErrors, onEvent)
        if (s != null) ServiceFormContent(s, state.contacts, state.showFormErrors, onEvent)
    }
    state.confirmUpkeepDelete?.let { UpkeepDeleteDialog(it, onEvent) }
}

/** "Delete 16x25x1 MERV 8?" over the open form (rule 16); Delete removes the record and closes the form, Cancel keeps both. */
@Composable
fun UpkeepDeleteDialog(target: UpkeepDelete, onEvent: (ItemDetailEvent) -> Unit) {
    AlertDialog(
        modifier = Modifier.windowTestTags(),
        onDismissRequest = { onEvent(ItemDetailEvent.CancelUpkeepDelete) },
        title = { Text(stringResource(R.string.item_detail_upkeep_delete_title, target.label)) },
        text = {
            Text(
                stringResource(
                    when (target) {
                        is UpkeepDelete.Consumable -> R.string.item_detail_consumable_delete_body
                        is UpkeepDelete.Service -> R.string.item_detail_service_delete_body
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ItemDetailEvent.ConfirmUpkeepDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_upkeep_delete_confirm")) {
                Text(stringResource(R.string.item_detail_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ItemDetailEvent.CancelUpkeepDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_upkeep_delete_cancel")) {
                Text(stringResource(R.string.item_detail_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsumableFormContent(draft: ConsumableDraft, showErrors: Boolean, onEvent: (ItemDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val errors = if (showErrors) draft.errors() else emptySet()
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsumableKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == draft.kind, onClick = { onEvent(ItemDetailEvent.ChangeConsumable(draft.copy(kind = kind))) },
                    label = { Text(kindLabel(kind.name)) }, modifier = Modifier.testTag("hm_item_detail_consumable_kind_${kind.name.lowercase()}"),
                )
            }
        }
        LabeledField(
            label = stringResource(R.string.item_detail_consumable_spec), value = draft.spec,
            onValueChange = { onEvent(ItemDetailEvent.ChangeConsumable(draft.copy(spec = it))) }, required = true,
            helper = stringResource(R.string.item_detail_consumable_spec_helper),
            error = if (ConsumableField.SPEC in errors) stringResource(R.string.item_detail_consumable_spec_error) else null,
            modifier = Modifier.testTag("hm_item_detail_consumable_spec"),
        )
        LabeledField(
            label = stringResource(R.string.item_detail_consumable_quantity), value = draft.quantity,
            onValueChange = { onEvent(ItemDetailEvent.ChangeConsumable(draft.copy(quantity = it.filter(Char::isDigit).take(3)))) },
            keyboardType = KeyboardType.Number, required = true,
            error = if (ConsumableField.QUANTITY in errors) stringResource(R.string.item_detail_consumable_quantity_error) else null,
            modifier = Modifier.testTag("hm_item_detail_consumable_quantity"),
        )
        LabeledField(
            label = stringResource(R.string.item_detail_consumable_url), value = draft.reorderUrl,
            onValueChange = { onEvent(ItemDetailEvent.ChangeConsumable(draft.copy(reorderUrl = it))) }, keyboardType = KeyboardType.Uri,
            error = if (ConsumableField.URL in errors) stringResource(R.string.item_detail_consumable_url_error) else null,
            modifier = Modifier.testTag("hm_item_detail_consumable_url"),
        )
        FormButtons(draft.id, onDelete = { draft.id?.let { onEvent(ItemDetailEvent.AskDeleteConsumable(it)) } }, tag = "consumable")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceFormContent(draft: ServiceDraft, contacts: List<ContactEntity>, showErrors: Boolean, onEvent: (ItemDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val errors = if (showErrors) draft.errors() else emptySet()
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = stringResource(R.string.item_detail_service_date), isoDate = draft.date.ifEmpty { null },
            onDateChange = { onEvent(ItemDetailEvent.ChangeService(draft.copy(date = it.orEmpty()))) }, required = true,
            modifier = Modifier.testTag("hm_item_detail_service_date"),
        )
        if (ServiceField.DATE in errors) Text(stringResource(R.string.item_detail_service_date_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        LabeledField(
            label = stringResource(R.string.item_detail_service_description), value = draft.description,
            onValueChange = { onEvent(ItemDetailEvent.ChangeService(draft.copy(description = it))) }, required = true,
            error = if (ServiceField.DESCRIPTION in errors) stringResource(R.string.item_detail_service_description_error) else null,
            modifier = Modifier.testTag("hm_item_detail_service_description"),
        )
        if (contacts.isNotEmpty()) {
            Text(stringResource(R.string.item_detail_service_contact), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.contactId == null, onClick = { onEvent(ItemDetailEvent.ChangeService(draft.copy(contactId = null))) },
                    label = { Text(stringResource(R.string.item_detail_service_contact_none)) }, modifier = Modifier.testTag("hm_item_detail_service_contact_none"),
                )
                contacts.forEach { c ->
                    FilterChip(
                        selected = draft.contactId == c.id, onClick = { onEvent(ItemDetailEvent.ChangeService(draft.copy(contactId = c.id))) },
                        label = { Text(c.name) }, modifier = Modifier.testTag("hm_item_detail_service_contact_chip"),
                    )
                }
            }
        }
        LabeledField(
            label = stringResource(R.string.item_detail_service_cost), value = draft.cost,
            onValueChange = { onEvent(ItemDetailEvent.ChangeService(draft.copy(cost = it))) }, keyboardType = KeyboardType.Decimal,
            error = if (ServiceField.COST in errors) stringResource(R.string.item_detail_service_cost_error) else null,
            modifier = Modifier.testTag("hm_item_detail_service_cost"),
        )
        FormButtons(draft.id, onDelete = { draft.id?.let { onEvent(ItemDetailEvent.AskDeleteService(it)) } }, tag = "service")
    }
}

@Composable
private fun FormButtons(id: String?, onDelete: () -> Unit, tag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (id != null) {
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_${tag}_delete")) { Text(stringResource(R.string.item_detail_delete)) }
        }
    }
}

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (runCatching { ConsumableKind.valueOf(kind) }.getOrDefault(ConsumableKind.OTHER)) {
        ConsumableKind.FILTER -> R.string.consumable_kind_filter
        ConsumableKind.BULB -> R.string.consumable_kind_bulb
        ConsumableKind.BATTERY -> R.string.consumable_kind_battery
        ConsumableKind.CARTRIDGE -> R.string.consumable_kind_cartridge
        ConsumableKind.BELT -> R.string.consumable_kind_belt
        ConsumableKind.SALT -> R.string.consumable_kind_salt
        ConsumableKind.UV_LAMP -> R.string.consumable_kind_uv_lamp
        ConsumableKind.OTHER -> R.string.consumable_kind_other
    },
)

private fun shortDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)

/** Documents attached to the item (AC-C37): open in the viewer, add from Files or the scanner, or attach one already in the home. */
@Composable
fun DocumentsCard(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.item_detail_documents), style = MaterialTheme.typography.titleMedium)
            if (state.documents.isEmpty()) {
                Text(stringResource(R.string.item_detail_documents_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                state.documents.forEach { doc ->
                    TextButton(
                        onClick = { onEvent(ItemDetailEvent.OpenDocument(doc.id)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_item_detail_document_row"),
                    ) {
                        Text(doc.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (state.documentNotice) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.documents_unsupported), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onEvent(ItemDetailEvent.DismissDocumentNotice) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.documents_dismiss))
                    }
                }
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEvent(ItemDetailEvent.UploadDocument) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_document_add")) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.item_detail_document_add), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { onEvent(ItemDetailEvent.ScanDocument) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_document_scan")) {
                    Text(stringResource(R.string.item_detail_document_scan))
                }
                OutlinedButton(onClick = { onEvent(ItemDetailEvent.ShowAttach) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_document_attach")) {
                    Text(stringResource(R.string.item_detail_document_attach))
                }
            }
        }
    }
}

/** Bottom sheet listing the home's other documents; tapping one attaches it to this item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachDocumentSheet(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    if (!state.showAttach) return
    ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(ItemDetailEvent.HideAttach) }) {
        AttachDocumentContent(state, onEvent)
    }
}

/** The sheet's scrolling list, so every document in a full home stays reachable at 200% text; tests draw it. */
@Composable
fun AttachDocumentContent(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    LazyColumn(
        Modifier.testTag("hm_item_detail_attach_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") { Text(stringResource(R.string.item_detail_attach_title), style = MaterialTheme.typography.titleLarge) }
        if (state.attachable.isEmpty()) {
            item(key = "empty") { Text(stringResource(R.string.item_detail_attach_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        items(state.attachable, key = { it.id }) { doc ->
            TextButton(
                onClick = { onEvent(ItemDetailEvent.AttachDocument(doc.id)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("hm_item_detail_attach_row"),
            ) { Text(doc.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
