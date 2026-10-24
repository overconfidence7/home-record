package com.kurtz.homerecord.housemanual.documents

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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/** Documents (AC-C37): PDFs and photos of paperwork, filed in folders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(state: DocumentsUiState, onEvent: (DocumentsEvent) -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.documents_title),
        onBack = { onEvent(DocumentsEvent.Back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(DocumentsEvent.Upload) }, modifier = Modifier.testTag("hm_documents_fab")) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.documents_add))
            }
        },
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
                    onAction = { onEvent(DocumentsEvent.Upload) },
                    actionModifier = Modifier.testTag("hm_documents_add"),
                )
                else -> LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.folders.forEach { folder ->
                        item(key = "folder:" + folder.name) {
                            Text(
                                folder.name.ifEmpty { stringResource(R.string.documents_unfiled) },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(folder.documents, key = { it.id }) { DocumentRow(it, onEvent) }
                    }
                }
            }
        }
    }
    val form = state.form
    if (form != null) {
        ModalBottomSheet(onDismissRequest = { onEvent(DocumentsEvent.CloseForm) }) {
            DocumentFormContent(form, state.showFormErrors, onEvent)
        }
    }
    state.confirmDeleteId?.let { id ->
        val name = state.folders.flatMap { it.documents }.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
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

@Composable
private fun DocumentRow(doc: DocumentEntity, onEvent: (DocumentsEvent) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (doc.mime == "application/pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Image, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall)
                Text(formatSize(doc.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onEvent(DocumentsEvent.OpenForm(doc.id)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_documents_edit")) {
                Text(stringResource(R.string.item_detail_edit))
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "${bytes / 1_024} KB"
    else -> "$bytes B"
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
