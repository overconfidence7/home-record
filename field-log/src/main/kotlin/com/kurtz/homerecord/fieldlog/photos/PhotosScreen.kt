package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Photos tab of FL-Job: days newest first in a three-column grid with 2 dp gaps (ui-standards rule 27), a
 * before/after pair two cells wide, and the capture FAB (SPEC FL-Job; AC-F24, AC-F25). Room, phase and caption are on
 * the Photo page; the tile reads them out as its description.
 */
@Composable
fun PhotosScreen(state: PhotosUiState, onEvent: (PhotosEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingState(Modifier.fillMaxSize())
            state.days.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp).testTag("fl_photos_empty"),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.photos_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            else -> PhotoGrid(state, onEvent)
        }
        if (!state.loading) {
            ExtendedFloatingActionButton(
                onClick = { onEvent(PhotosEvent.Capture) },
                icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.photos_take)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 56.dp).testTag("fl_photos_fab"),
            )
        }
    }
}

@Composable
private fun PhotoGrid(state: PhotosUiState, onEvent: (PhotosEvent) -> Unit) {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val gap = 2.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().testTag("fl_photos_list"),
        // Room at the bottom so the last row clears the FAB.
        contentPadding = PaddingValues(bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        state.days.forEach { day ->
            item(key = "d-${day.day}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    day.day.format(fmt),
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(day.cells, key = { it.key() }, span = { GridItemSpan(if (it is PhotoCell.Pair) 2 else 1) }) { cell ->
                when (cell) {
                    is PhotoCell.Single -> Tile(cell.tile, Modifier.fillMaxWidth(), onOpen = { onEvent(PhotosEvent.Open(it)) })
                    is PhotoCell.Pair -> Row(Modifier.fillMaxWidth().testTag("fl_photos_pair_${cell.after.id}"), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Tile(cell.before, Modifier.weight(1f), stringResource(R.string.photos_before)) { onEvent(PhotosEvent.Open(it)) }
                        Tile(cell.after, Modifier.weight(1f), stringResource(R.string.photos_after)) { onEvent(PhotosEvent.Open(it)) }
                    }
                }
            }
        }
    }
}

private fun PhotoCell.key(): String = when (this) {
    is PhotoCell.Single -> tile.id
    is PhotoCell.Pair -> after.id
}

@Composable
private fun Tile(tile: PhotoTile, modifier: Modifier, badge: String? = null, onOpen: (String) -> Unit) {
    val description = listOfNotNull(badge, tile.caption, tile.roomName, tile.phase).joinToString(", ").ifBlank { null }
    Box(
        modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onOpen(tile.id) }.testTag("fl_photos_tile_${tile.id}"),
    ) {
        AsyncImage(
            model = File(tile.imagePath), contentDescription = description, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val label = badge ?: if (tile.annotated) stringResource(R.string.photos_marked) else null
        if (label != null) {
            Text(
                label, Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}
