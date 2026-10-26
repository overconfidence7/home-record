package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The Photos tab of FL-Job: days newest first, two tiles to a row, a before/after pair sharing one row (AC-F24, AC-F25). */
@Composable
fun PhotosScreen(state: PhotosUiState, onEvent: (PhotosEvent) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> LoadingState(modifier)
        state.days.isEmpty() -> Column(
            modifier.fillMaxSize().padding(24.dp).testTag("fl_photos_empty"),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.photos_empty), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { onEvent(PhotosEvent.Capture) }, Modifier.testTag("fl_photos_take")) { Text(stringResource(R.string.photos_take)) }
        }
        else -> {
            val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            LazyColumn(
                modifier.fillMaxSize().testTag("fl_photos_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.days.forEach { day ->
                    item(key = "d-${day.day}") { Text(day.day.format(fmt), style = MaterialTheme.typography.titleMedium) }
                    items(day.cells.chunkedRows(), key = { row -> row.joinToString("-") { it.key() } }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { cell ->
                                when (cell) {
                                    is PhotoCell.Single -> Tile(cell.tile, Modifier.weight(1f))
                                    is PhotoCell.Pair -> Row(Modifier.weight(2f).testTag("fl_photos_pair_${cell.after.id}"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Tile(cell.before, Modifier.weight(1f), stringResource(R.string.photos_before))
                                        Tile(cell.after, Modifier.weight(1f), stringResource(R.string.photos_after))
                                    }
                                }
                            }
                            if (row.size == 1 && row[0] is PhotoCell.Single) Box(Modifier.weight(1f))
                        }
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

/** Rows hold two singles, or one pair (a pair is two tiles wide). */
private fun List<PhotoCell>.chunkedRows(): List<List<PhotoCell>> {
    val rows = mutableListOf<MutableList<PhotoCell>>()
    for (cell in this) {
        val last = rows.lastOrNull()
        if (cell is PhotoCell.Single && last != null && last.size == 1 && last[0] is PhotoCell.Single) last.add(cell) else rows.add(mutableListOf(cell))
    }
    return rows
}

@Composable
private fun Tile(tile: PhotoTile, modifier: Modifier, badge: String? = null) {
    val facts = listOfNotNull(tile.roomName, tile.phase).joinToString(" · ")
    Column(modifier.testTag("fl_photos_tile_${tile.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            AsyncImage(
                model = File(tile.imagePath), contentDescription = tile.caption ?: facts.ifBlank { null }, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val label = badge ?: if (tile.annotated) stringResource(R.string.photos_marked) else null
            if (label != null) {
                Text(
                    label, Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (facts.isNotBlank()) Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tile.caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
