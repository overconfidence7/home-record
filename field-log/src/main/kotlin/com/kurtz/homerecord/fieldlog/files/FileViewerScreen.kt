package com.kurtz.homerecord.fieldlog.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.pages.PdfViewer
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * FL-File: PDF pages with a thumbnail strip and pinch zoom, or a photo, plus Share (AC-F19); Mark up for those who
 * manage files and Version history once a file has been marked up (AC-F21).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(state: FileViewerUiState, onEvent: (FileViewerEvent) -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    ScreenScaffold(
        title = state.name.ifEmpty { stringResource(R.string.file_viewer_title) },
        onBack = { onEvent(FileViewerEvent.Back) },
        actions = {
            if (state.canMarkUp) {
                IconButton(onClick = { onEvent(FileViewerEvent.MarkUp) }, modifier = Modifier.testTag("fl_file_markup")) {
                    Icon(Icons.Rounded.Draw, contentDescription = stringResource(R.string.file_viewer_markup))
                }
            }
            if (state.versions.size > 1) {
                IconButton(onClick = { onEvent(FileViewerEvent.ShowHistory) }, modifier = Modifier.testTag("fl_file_history")) {
                    Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.file_viewer_history))
                }
            }
            if (state.path.isNotEmpty() && state.problem != FileViewerProblem.NotOnPhone) {
                IconButton(onClick = { onEvent(FileViewerEvent.Share) }, modifier = Modifier.testTag("fl_file_share")) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.file_viewer_share))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val pages = state.pages
            when {
                state.loading -> LoadingState()
                pages != null -> PdfViewer(pages, tagPrefix = "fl_file", modifier = Modifier.fillMaxSize())
                else -> Problem(state.problem ?: FileViewerProblem.Missing, onEvent)
            }
        }
    }
    if (state.showHistory) {
        ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(FileViewerEvent.HideHistory) }) {
            VersionHistory(state.versions, onEvent, zone)
        }
    }
}

/** The history sheet's list, newest first; previews and tests draw it without the sheet. */
@Composable
fun VersionHistory(versions: List<FileVersion>, onEvent: (FileViewerEvent) -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    LazyColumn(Modifier.widthIn(max = 640.dp).navigationBarsPadding().testTag("fl_file_history_list")) {
        item(key = "title") {
            Text(
                stringResource(R.string.file_viewer_history), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(versions, key = { it.id }) { v ->
            val time = Instant.ofEpochMilli(v.createdAt).atZone(zone).format(fmt)
            ListItem(
                headlineContent = { Text(stringResource(R.string.file_viewer_version, v.version)) },
                supportingContent = { Text(if (v.author != null) stringResource(R.string.file_viewer_version_by, v.author, time) else time) },
                trailingContent = if (v.shown) {
                    { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.file_viewer_version_open)) }
                } else null,
                modifier = Modifier.clickable { onEvent(FileViewerEvent.OpenVersion(v.id)) }.testTag("fl_file_version_${v.version}"),
            )
        }
    }
}

@Composable
private fun Problem(problem: FileViewerProblem, onEvent: (FileViewerEvent) -> Unit) {
    val (icon, headline, body) = when (problem) {
        FileViewerProblem.NotOnPhone -> Triple(Icons.Rounded.CloudOff, R.string.file_viewer_not_on_phone_title, R.string.file_viewer_not_on_phone_body)
        FileViewerProblem.NoPreview -> Triple(Icons.Rounded.ErrorOutline, R.string.file_viewer_no_preview_title, R.string.file_viewer_no_preview_body)
        FileViewerProblem.Unreadable -> Triple(Icons.Rounded.ErrorOutline, R.string.file_viewer_unreadable_title, R.string.file_viewer_unreadable_body)
        FileViewerProblem.Missing -> Triple(Icons.Rounded.ErrorOutline, R.string.file_viewer_missing_title, R.string.file_viewer_missing_body)
    }
    val shareable = problem == FileViewerProblem.NoPreview
    val retry = problem == FileViewerProblem.NotOnPhone
    EmptyState(
        icon = icon,
        headline = stringResource(headline),
        body = stringResource(body),
        actionLabel = stringResource(if (shareable) R.string.file_viewer_share else if (retry) R.string.file_viewer_try_again else R.string.file_viewer_back),
        onAction = { onEvent(if (shareable) FileViewerEvent.Share else if (retry) FileViewerEvent.Retry else FileViewerEvent.Back) },
        actionModifier = Modifier.testTag(if (shareable) "fl_file_share_action" else if (retry) "fl_file_retry" else "fl_file_back"),
    )
}
