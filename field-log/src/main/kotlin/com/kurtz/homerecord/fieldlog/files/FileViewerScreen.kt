package com.kurtz.homerecord.fieldlog.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import kotlinx.coroutines.launch

private const val PAGE_WIDTH_PX = 1080
private const val THUMB_WIDTH_PX = 240
private const val MAX_ZOOM = 5f

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
                pages != null -> Pages(pages)
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
    EmptyState(
        icon = icon,
        headline = stringResource(headline),
        body = stringResource(body),
        actionLabel = stringResource(if (shareable) R.string.file_viewer_share else R.string.file_viewer_back),
        onAction = { onEvent(if (shareable) FileViewerEvent.Share else FileViewerEvent.Back) },
        actionModifier = Modifier.testTag(if (shareable) "fl_file_share_action" else "fl_file_back"),
    )
}

@Composable
private fun Pages(pages: PageSource) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("fl_file_pages")
                .transformable(transform, canPan = { scale > 1f })
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            state = list,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pages.pageCount) { index ->
                val label = stringResource(R.string.file_viewer_page, index + 1, pages.pageCount)
                val bitmap by produceState(pages.peek(index), pages, index) { value = pages.render(index, PAGE_WIDTH_PX) }
                val shown = bitmap
                if (shown == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.77f).semantics { contentDescription = label })
                } else {
                    Image(shown, contentDescription = label, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (pages.pageCount > 1) {
            LazyRow(
                Modifier.fillMaxWidth().testTag("fl_file_thumbnails").padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(pages.pageCount) { index ->
                    val label = stringResource(R.string.file_viewer_go_to_page, index + 1)
                    val thumb by produceState(pages.peek(index), pages, index) { value = pages.render(index, THUMB_WIDTH_PX) }
                    val shown = thumb
                    val tile = Modifier
                        .width(72.dp).height(96.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .clickable(onClickLabel = label) { scope.launch { list.animateScrollToItem(index) } }
                        .semantics { contentDescription = label }
                    if (shown == null) Box(tile) else Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = tile)
                }
            }
        }
    }
}
