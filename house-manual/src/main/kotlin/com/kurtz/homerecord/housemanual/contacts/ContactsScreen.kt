package com.kurtz.homerecord.housemanual.contacts

import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kurtz.homerecord.housemanual.FormScreenDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.upkeep.ContactDraft
import com.kurtz.homerecord.housemodel.upkeep.ContactField
import com.kurtz.homerecord.housemodel.upkeep.ContactRow

/** Contacts (AC-C39): who fixed what, with tap to call or email. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(state: ContactsUiState, onEvent: (ContactsEvent) -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.contacts_title),
        onBack = { onEvent(ContactsEvent.Back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(ContactsEvent.OpenForm(null)) }, modifier = Modifier.testTag("hm_contacts_fab")) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.contacts_add))
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.rows.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Contacts,
                headline = stringResource(R.string.contacts_empty_title),
                body = stringResource(R.string.contacts_empty_body),
                actionLabel = stringResource(R.string.contacts_add),
                onAction = { onEvent(ContactsEvent.OpenForm(null)) },
                modifier = Modifier.padding(padding),
                actionModifier = Modifier.testTag("hm_contacts_add"),
            )
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.contact.id }) { ContactCard(it, onEvent) }
            }
        }
    }
    val form = state.form
    if (form != null) {
        FormScreenDialog(
            title = stringResource(if (form.id == null) R.string.contacts_form_new else R.string.contacts_form_edit),
            saveTag = "hm_contacts_save",
            onClose = { onEvent(ContactsEvent.CloseForm) },
            onSave = { onEvent(ContactsEvent.SaveForm) },
        ) {
            ContactFormContent(form, state.showFormErrors, onEvent)
        }
    }
    state.confirmDeleteId?.let { id ->
        val name = state.rows.firstOrNull { it.contact.id == id }?.contact?.name.orEmpty()
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ContactsEvent.CancelDelete) },
            title = { Text(stringResource(R.string.contacts_delete_title, name)) },
            text = { Text(stringResource(R.string.contacts_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(ContactsEvent.ConfirmDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_contacts_delete_confirm")) {
                    Text(stringResource(R.string.contacts_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(ContactsEvent.CancelDelete) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.item_detail_cancel)) }
            },
        )
    }
}

@Composable
private fun ContactCard(row: ContactRow, onEvent: (ContactsEvent) -> Unit) {
    val c = row.contact
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    val sub = listOfNotNull(c.trade, c.company).joinToString(", ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onEvent(ContactsEvent.OpenForm(c.id)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_contacts_edit")) {
                    Text(stringResource(R.string.item_detail_edit))
                }
            }
            if (row.itemNames.isNotEmpty()) {
                Text(
                    stringResource(R.string.contacts_serviced, row.itemNames.joinToString(", "), pluralStringResource(R.plurals.contacts_visits, row.serviceCount, row.serviceCount)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.phone?.let { phone ->
                    OutlinedButton(onClick = { onEvent(ContactsEvent.Call(phone)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_contacts_call")) {
                        Icon(Icons.Rounded.Phone, contentDescription = null)
                        Text(phone, Modifier.padding(start = 8.dp))
                    }
                }
                c.email?.let { address ->
                    OutlinedButton(onClick = { onEvent(ContactsEvent.Email(address)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_contacts_email")) {
                        Icon(Icons.Rounded.Email, contentDescription = stringResource(R.string.contacts_email))
                    }
                }
            }
            if (!c.notes.isNullOrBlank()) Text(c.notes.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The contact form as sheet content; previewed directly because sheets don't render in screenshot tests. */
@Composable
fun ContactFormContent(draft: ContactDraft, showErrors: Boolean, onEvent: (ContactsEvent) -> Unit, modifier: Modifier = Modifier) {
    val errors = if (showErrors) draft.errors() else emptySet()
    fun change(d: ContactDraft) = onEvent(ContactsEvent.ChangeForm(d))
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabeledField(
            stringResource(R.string.contacts_name), draft.name, { change(draft.copy(name = it)) }, Modifier.fillMaxWidth().testTag("hm_contacts_name"),
            required = true, error = if (ContactField.NAME in errors) stringResource(R.string.field_required) else null,
        )
        LabeledField(stringResource(R.string.contacts_company), draft.company, { change(draft.copy(company = it)) }, Modifier.fillMaxWidth().testTag("hm_contacts_company"))
        LabeledField(stringResource(R.string.contacts_trade), draft.trade, { change(draft.copy(trade = it)) }, Modifier.fillMaxWidth().testTag("hm_contacts_trade"))
        LabeledField(stringResource(R.string.contacts_phone), draft.phone, { change(draft.copy(phone = it)) }, Modifier.fillMaxWidth().testTag("hm_contacts_phone"), keyboardType = KeyboardType.Phone)
        LabeledField(
            stringResource(R.string.contacts_email_field), draft.email, { change(draft.copy(email = it)) }, Modifier.fillMaxWidth().testTag("hm_contacts_email_field"),
            keyboardType = KeyboardType.Email, error = if (ContactField.EMAIL in errors) stringResource(R.string.contacts_email_error) else null,
        )
        LabeledField(stringResource(R.string.contacts_notes), draft.notes, { change(draft.copy(notes = it)) }, Modifier.fillMaxWidth().testTag("hm_contacts_notes"), singleLine = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (draft.id != null) {
                TextButton(onClick = { onEvent(ContactsEvent.AskDelete(draft.id.orEmpty())) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_contacts_delete")) {
                    Text(stringResource(R.string.contacts_delete))
                }
            }
        }
    }
}
