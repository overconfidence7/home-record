package com.kurtz.homerecord.housemanual.documents

import com.kurtz.homerecord.coreui.pages.PdfViewer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/** In-app viewer for a PDF or photo with pinch zoom, plus Share (AC-C37). */
@Composable
fun DocumentViewerScreen(state: DocumentViewerUiState, onEvent: (DocumentViewerEvent) -> Unit) {
    ScreenScaffold(
        title = state.name,
        onBack = { onEvent(DocumentViewerEvent.Back) },
        actions = {
            if (state.pages != null) {
                IconButton(onClick = { onEvent(DocumentViewerEvent.Share) }, modifier = Modifier.testTag("hm_document_share")) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.document_viewer_share))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val pages = state.pages
            when {
                state.loading -> LoadingState()
                pages == null -> EmptyState(
                    icon = Icons.Rounded.ErrorOutline,
                    headline = stringResource(R.string.document_viewer_missing_title),
                    body = stringResource(R.string.document_viewer_missing_body),
                    actionLabel = stringResource(R.string.document_viewer_back),
                    onAction = { onEvent(DocumentViewerEvent.Back) },
                    actionModifier = Modifier.testTag("hm_document_back"),
                )
                else -> PdfViewer(pages, tagPrefix = "hm_document", modifier = Modifier.fillMaxSize())
            }
        }
    }
}
