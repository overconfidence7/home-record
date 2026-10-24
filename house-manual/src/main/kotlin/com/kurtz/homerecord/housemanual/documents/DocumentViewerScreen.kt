package com.kurtz.homerecord.housemanual.documents

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

private const val PAGE_WIDTH_PX = 1080
private const val MAX_ZOOM = 5f

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
                else -> ZoomablePages(pages)
            }
        }
    }
}

@Composable
private fun ZoomablePages(pages: PageSource) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("hm_document_pages")
            .transformable(transform, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(List(pages.pageCount) { it }) { index, _ ->
            val label = stringResource(R.string.document_viewer_page, index + 1, pages.pageCount)
            val bitmap by produceState(pages.peek(index), pages, index) { value = pages.render(index, PAGE_WIDTH_PX) }
            val shown = bitmap
            if (shown == null) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.77f).semantics { contentDescription = label })
            } else {
                Image(
                    shown, contentDescription = label, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
