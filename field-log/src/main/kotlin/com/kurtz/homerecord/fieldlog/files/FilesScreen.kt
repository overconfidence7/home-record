package com.kurtz.homerecord.fieldlog.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import com.kurtz.homerecord.core.photo.UploadStatus
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R

/** The Files tab of FL-Job: folders first, then files, search across all folders (AC-F19, AC-F23). */
@Composable
fun FilesScreen(state: FilesUiState, onEvent: (FilesEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.loading) { LoadingState(modifier); return }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query, onValueChange = { onEvent(FilesEvent.Query(it)) }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(stringResource(R.string.files_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("fl_files_search"),
        )
        if (state.canManage) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEvent(FilesEvent.AddFile) }, Modifier.heightIn(min = 56.dp).testTag("fl_files_add")) {
                Icon(Icons.Rounded.FileUpload, null); Text(stringResource(R.string.files_add), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { onEvent(FilesEvent.NewFolder) }, Modifier.heightIn(min = 56.dp).testTag("fl_files_new_folder")) {
                Icon(Icons.Rounded.CreateNewFolder, null); Text(stringResource(R.string.files_new_folder), Modifier.padding(start = 8.dp))
            }
        }
        if (state.query.isBlank() && state.folder.isNotEmpty()) {
            TextButton(onClick = { onEvent(FilesEvent.Up) }, Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp).testTag("fl_files_up")) {
                Text(stringResource(R.string.files_in_folder, state.folder))
            }
        }
        val searching = state.query.isNotBlank()
        val rows = if (searching) state.results else state.files
        when {
            searching && rows.isEmpty() -> Text(stringResource(R.string.files_no_match, state.query.trim()), Modifier.padding(24.dp).testTag("fl_files_no_match"))
            !searching && rows.isEmpty() && state.folders.isEmpty() -> Text(
                stringResource(R.string.files_empty), Modifier.padding(24.dp).testTag("fl_files_empty"), style = MaterialTheme.typography.bodyLarge,
            )
            else -> LazyColumn(Modifier.fillMaxSize().testTag("fl_files_list")) {
                if (!searching) items(state.folders, key = { "f-$it" }) { name ->
                    RecordListItem(
                        headline = name, leading = { Icon(Icons.Rounded.Folder, null) },
                        onClick = { onEvent(FilesEvent.OpenFolder(name)) }, modifier = Modifier.testTag("fl_files_folder_$name"),
                    )
                }
                items(rows, key = { it.id }) { f ->
                    RecordListItem(
                        headline = f.name, supporting = supporting(f, searching),
                        leading = { Icon(f.icon(), null) },
                        trailing = {
                            Row {
                                IconButton({ onEvent(FilesEvent.ToggleOffline(f.id, !f.offline)) }, Modifier.testTag("fl_files_offline_${f.id}")) {
                                    Icon(
                                        if (f.offline) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                                        stringResource(if (f.offline) R.string.files_offline_off else R.string.files_offline_on),
                                    )
                                }
                                if (state.canManage) {
                                    IconButton({ onEvent(FilesEvent.AskRename(f.id)) }, Modifier.testTag("fl_files_rename_${f.id}")) {
                                        Icon(Icons.Rounded.Edit, stringResource(R.string.files_rename))
                                    }
                                    IconButton({ onEvent(FilesEvent.AskDelete(f.id)) }, Modifier.testTag("fl_files_delete_${f.id}")) {
                                        Icon(Icons.Rounded.Delete, stringResource(R.string.files_delete))
                                    }
                                }
                            }
                        },
                        onClick = { onEvent(FilesEvent.OpenFile(f.id)) }, modifier = Modifier.testTag("fl_files_row_${f.id}"),
                    )
                }
            }
        }
    }
    when (val dlg = state.dialog) {
        FilesDialog.NewFolder -> NameDialog(R.string.files_new_folder, "", R.string.files_create, onDismiss = { onEvent(FilesEvent.DismissDialog) }) { onEvent(FilesEvent.CreateFolder(it)) }
        is FilesDialog.Rename -> NameDialog(R.string.files_rename, dlg.name, R.string.files_save, onDismiss = { onEvent(FilesEvent.DismissDialog) }) { onEvent(FilesEvent.Rename(dlg.id, it)) }
        is FilesDialog.Delete -> AlertDialog(
            onDismissRequest = { onEvent(FilesEvent.DismissDialog) },
            title = { Text(stringResource(R.string.files_delete_title, dlg.name)) },
            text = { Text(stringResource(R.string.files_delete_body)) },
            confirmButton = { TextButton({ onEvent(FilesEvent.Delete(dlg.id)) }, Modifier.heightIn(min = 48.dp).testTag("fl_files_delete_confirm")) { Text(stringResource(R.string.files_delete)) } },
            dismissButton = { TextButton({ onEvent(FilesEvent.DismissDialog) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.files_cancel)) } },
            modifier = Modifier.windowTestTags(),
        )
        null -> Unit
    }
    state.notice?.let { n ->
        AlertDialog(
            onDismissRequest = { onEvent(FilesEvent.DismissNotice) },
            text = { Text(stringResource(when (n) { FilesNotice.FolderExists -> R.string.files_folder_exists; FilesNotice.AddFailed -> R.string.files_add_failed; FilesNotice.DownloadFailed -> R.string.files_download_failed })) },
            confirmButton = { TextButton({ onEvent(FilesEvent.DismissNotice) }, Modifier.heightIn(min = 48.dp).testTag("fl_files_notice_ok")) { Text(stringResource(R.string.files_ok)) } },
            modifier = Modifier.windowTestTags(),
        )
    }
}

@Composable
private fun NameDialog(title: Int, initial: String, confirm: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("fl_files_name")) },
        confirmButton = { TextButton({ onConfirm(text) }, Modifier.heightIn(min = 48.dp).testTag("fl_files_name_ok"), enabled = text.isNotBlank()) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.files_cancel)) } },
        modifier = Modifier.windowTestTags(),
    )
}

@Composable
private fun supporting(f: FileRow, searching: Boolean): String {
    val size = sizeLabel(f.size)
    val where = if (searching) f.folder.ifEmpty { stringResource(R.string.files_top) } else null
    val offline = if (f.offline) stringResource(R.string.files_offline) else null
    val upload = when (f.upload) {
        UploadStatus.Waiting -> stringResource(R.string.files_upload_waiting)
        UploadStatus.Uploading -> stringResource(R.string.files_upload_uploading)
        UploadStatus.Failed -> stringResource(R.string.files_upload_failed)
        else -> null
    }
    return listOfNotNull(where, size, offline, upload).joinToString(" · ")
}

internal fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

private fun FileRow.icon(): ImageVector = when {
    mime == "application/pdf" -> Icons.Rounded.PictureAsPdf
    mime.startsWith("text/") -> Icons.Rounded.Description
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}
