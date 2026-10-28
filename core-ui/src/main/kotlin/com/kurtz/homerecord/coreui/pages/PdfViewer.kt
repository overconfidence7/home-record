package com.kurtz.homerecord.coreui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.kurtz.homerecord.coreui.R
import kotlinx.coroutines.launch

private const val PAGE_WIDTH_PX = 1080
private const val THUMB_WIDTH_PX = 240
private const val MAX_ZOOM = 5f

/**
 * The one PDF and photo viewer both apps use: pages rendered on demand with pinch zoom, and a
 * thumbnail strip that jumps to a page when the document has more than one. Tags are
 * `<tagPrefix>_pages` and `<tagPrefix>_thumbnails` (`hm_document`, `fl_file`).
 */
@Composable
fun PdfViewer(pages: PageSource, tagPrefix: String, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(modifier) {
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("${tagPrefix}_pages")
                .transformable(transform, canPan = { scale > 1f })
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            state = list,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pages.pageCount) { index ->
                val label = stringResource(R.string.pdf_viewer_page, index + 1, pages.pageCount)
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
                Modifier.fillMaxWidth().testTag("${tagPrefix}_thumbnails").padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(pages.pageCount) { index ->
                    val label = stringResource(R.string.pdf_viewer_go_to_page, index + 1)
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
