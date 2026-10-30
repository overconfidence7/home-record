package com.kurtz.homerecord.housemanual.documents

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.media.ByteSize
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/** Documents (AC-C37): PDFs and photos of paperwork, filed in folders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(state: DocumentsUiState, onEvent: (DocumentsEvent) -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.documents_title),
        onBack = { onEvent(DocumentsEvent.Back) },
        floatingActionButton = { DocumentsAddMenu(state.addMenuOpen, onEvent) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.unsupportedNotice) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp).testTag("hm_documents_notice"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.documents_unsupported), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { onEvent(DocumentsEvent.DismissNotice) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.documents_dismiss))
                        }
                    }
                }
            }
            when {
                state.loading -> LoadingState()
                state.folders.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Description,
                    headline = stringResource(R.string.documents_empty_title),
                    body = stringResource(R.string.documents_empty_body),
                    actionLabel = stringResource(R.string.documents_add),
                    onAction = { onEvent(DocumentsEvent.SetAddMenu(true)) },
                    actionModifier = Modifier.testTag("hm_documents_add"),
                )
                else -> LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    state.folders.forEach { folder ->
                        item(key = "folder:" + folder.name) {
                            Text(
                                folder.name.ifEmpty { stringResource(R.string.documents_unfiled) },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.animateItem().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(folder.documents, key = { it.id }) { DocumentRow(it, onEvent, Modifier.animateItem()) }
                    }
                }
            }
        }
    }
    val form = state.form
    if (form != null) {
        ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(DocumentsEvent.CloseForm) }) {
            DocumentFormContent(form, state.showFormErrors, onEvent)
        }
    }
    state.confirmDeleteId?.let { id ->
        val name = state.folders.flatMap { it.documents }.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(DocumentsEvent.CancelDelete) },
            title = { Text(stringResource(R.string.documents_delete_title, name)) },
            text = { Text(stringResource(R.string.documents_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(DocumentsEvent.ConfirmDelete) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_documents_delete_confirm")) {
                    Text(stringResource(R.string.documents_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(DocumentsEvent.CancelDelete) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.item_detail_cancel)) }
            },
        )
    }
}

/**
 * The one create action (rule 13): a FAB menu with Add a file and Scan a document. The empty state's
 * Add document opens the same menu, so both entry points offer the same two choices (rule 10).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocumentsAddMenu(open: Boolean, onEvent: (DocumentsEvent) -> Unit) {
    BackHandler(enabled = open) { onEvent(DocumentsEvent.SetAddMenu(false)) }
    val addLabel = stringResource(R.string.documents_add)
    FloatingActionButtonMenu(
        expanded = open,
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = { onEvent(DocumentsEvent.SetAddMenu(it)) },
                modifier = Modifier.semantics { contentDescription = addLabel }.testTag("hm_documents_fab"),
            ) {
                Icon(if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add, contentDescription = null)
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onEvent(DocumentsEvent.Upload) },
            text = { Text(stringResource(R.string.documents_add_file)) },
            icon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
            modifier = Modifier.testTag("hm_documents_upload"),
        )
        FloatingActionButtonMenuItem(
            onClick = { onEvent(DocumentsEvent.Scan) },
            text = { Text(stringResource(R.string.documents_scan)) },
            icon = { Icon(Icons.Rounded.DocumentScanner, contentDescription = null) },
            modifier = Modifier.testTag("hm_documents_scan"),
        )
    }
}

/** One document as a list row (rule 7): tap opens the viewer, the trailing button renames or moves it. */
@Composable
private fun DocumentRow(doc: DocumentEntity, onEvent: (DocumentsEvent) -> Unit, modifier: Modifier = Modifier) {
    RecordListItem(
        headline = doc.name,
        supporting = ByteSize.label(doc.size),
        leading = { Icon(if (doc.mime == "application/pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Image, contentDescription = null) },
        trailing = {
            IconButton(onClick = { onEvent(DocumentsEvent.OpenForm(doc.id)) }, modifier = Modifier.testTag("hm_documents_edit")) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.documents_edit, doc.name))
            }
        },
        onClick = { onEvent(DocumentsEvent.Open(doc.id)) },
        modifier = modifier.testTag("hm_documents_row"),
    )
}

/** The rename and move form as sheet content; previewed directly because sheets don't render in screenshot tests. */
@Composable
fun DocumentFormContent(form: DocumentForm, showErrors: Boolean, onEvent: (DocumentsEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.documents_form_title), style = MaterialTheme.typography.titleLarge)
        LabeledField(
            stringResource(R.string.documents_name), form.name, { onEvent(DocumentsEvent.ChangeForm(form.copy(name = it))) },
            Modifier.fillMaxWidth().testTag("hm_documents_name"),
            required = true, error = if (showErrors && form.name.isBlank()) stringResource(R.string.field_required) else null,
        )
        LabeledField(
            stringResource(R.string.documents_folder), form.folder, { onEvent(DocumentsEvent.ChangeForm(form.copy(folder = it))) },
            Modifier.fillMaxWidth().testTag("hm_documents_folder"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onEvent(DocumentsEvent.SaveForm) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_documents_save")) { Text(stringResource(R.string.documents_save)) }
            TextButton(onClick = { onEvent(DocumentsEvent.AskDelete(form.id)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_documents_delete")) {
                Text(stringResource(R.string.documents_delete))
            }
        }
    }
}
