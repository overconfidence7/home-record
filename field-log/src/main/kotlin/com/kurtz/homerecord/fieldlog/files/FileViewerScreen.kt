package com.kurtz.homerecord.fieldlog.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.pages.PdfViewer
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

/** FL-File: PDF pages with a thumbnail strip and pinch zoom, or a photo, plus Share (AC-F19). */
@Composable
fun FileViewerScreen(state: FileViewerUiState, onEvent: (FileViewerEvent) -> Unit) {
    ScreenScaffold(
        title = state.name.ifEmpty { stringResource(R.string.file_viewer_title) },
        onBack = { onEvent(FileViewerEvent.Back) },
        actions = {
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
