package com.kurtz.homerecord.housemanual.panel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.logic.PanelGridPlanner
import com.kurtz.homerecord.core.logic.PanelLayoutCalculator
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.housemanual.breaker.confidenceLabel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Items before the slot rows in the panel list: title, photo, slot-count editor, legend button. */
private const val PANEL_HEADER_ITEMS = 5
private val SLOT_ROW_HEIGHT = 64.dp
private val TANDEM_ROW_HEIGHT = 104.dp
private val GRID_GAP = 8.dp
const val HIGHLIGHT_MILLIS = 2_500L

@Composable
fun PanelScreen(
    state: PanelUiState,
    onEvent: (PanelEvent) -> Unit,
    photoFile: File? = null,
    highlightSlot: Int? = null,
) {
    if (!state.loaded) return
    val panel = state.panel
    if (panel == null) {
        PanelSetup(state, onEvent)
        return
    }
    val rows = PanelLayoutCalculator.rows(panel.slotCount, panel.layout)
    val rowHeights = rows.map { row -> if (row.any { state.tandemAt(it) != null }) TANDEM_ROW_HEIGHT else SLOT_ROW_HEIGHT }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    // AC-18: scroll to the searched slot and keep it highlighted for HIGHLIGHT_MILLIS.
    var highlighted by remember(highlightSlot) { mutableStateOf(highlightSlot) }
    LaunchedEffect(highlightSlot, rows) {
        val target = highlightSlot ?: return@LaunchedEffect
        val rowIndex = rows.indexOfFirst { target in it }
        if (rowIndex >= 0) {
            val offset = rowHeights.take(rowIndex).fold(0.dp) { acc, h -> acc + h + GRID_GAP }
            listState.scrollToItem(PANEL_HEADER_ITEMS, with(density) { offset.roundToPx() })
        }
        delay(HIGHLIGHT_MILLIS)
        highlighted = null
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("panel_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.panel_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item { PanelPhoto(photoFile, hasPhoto = panel.photoPath != null, onEvent) }
        item { SlotCountEditor(state, panel.slotCount, onEvent) }
        item {
            OutlinedButton(
                onClick = { onEvent(PanelEvent.ShareLegend) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("panel_legend_pdf"),
            ) { Text(stringResource(R.string.panel_legend_pdf)) }
        }
        item {
            OutlinedButton(
                onClick = { onEvent(PanelEvent.OpenMapping) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("panel_mapping_mode"),
            ) { Text(stringResource(R.string.panel_mapping_mode)) }
        }
        item(key = "slot_grid") {
            val columns = PanelGridPlanner.columns(panel.slotCount, panel.layout, state.breakers)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                columns.forEach { cells ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                        cells.forEach { cell ->
                            val height = (cell.row until cell.row + cell.rowSpan).fold((cell.rowSpan - 1) * GRID_GAP.value) { acc, r -> acc + rowHeights[r].value }.dp
                            val lit = highlighted != null && (highlighted == cell.slot || highlighted in (cell.breaker?.slots.orEmpty()))
                            val twoPole = cell.breaker?.takeIf { cell.rowSpan == 2 }
                            if (twoPole != null) {
                                TwoPoleCell(twoPole, state, onEvent, Modifier.height(height), highlighted = lit)
                            } else {
                                SlotCell(cell.slot, state, onEvent, Modifier.height(height), highlighted = lit)
                            }
                        }
                    }
                }
            }
        }
        item { Box(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SlotCountEditor(state: PanelUiState, current: Int, onEvent: (PanelEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.slotEditInput ?: current.toString(),
            onValueChange = { onEvent(PanelEvent.SlotEditChanged(it)) },
            label = { Text(stringResource(R.string.panel_slot_count)) },
            isError = !state.canApplySlotCount || state.shrinkBlockedAt != null,
            supportingText = state.shrinkBlockedAt?.let { { Text(stringResource(R.string.panel_slot_count_blocked, it), modifier = Modifier.testTag("panel_shrink_error")) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("panel_slot_edit"),
        )
        OutlinedButton(
            onClick = { onEvent(PanelEvent.ApplySlotCount) },
            enabled = state.slotEditInput != null && state.slotEditInput != current.toString() && state.canApplySlotCount,
            modifier = Modifier.heightIn(min = 48.dp).testTag("panel_slot_apply"),
        ) { Text(stringResource(R.string.panel_slot_count_apply)) }
        Text(stringResource(R.string.panel_make_tandem), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PanelPhoto(photoFile: File?, hasPhoto: Boolean, onEvent: (PanelEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasPhoto) {
            val bitmap by produceState<Bitmap?>(null, photoFile) {
                value = photoFile?.let { withContext(Dispatchers.IO) { decodeScaled(it, 1280) } }
            }
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = stringResource(R.string.panel_photo_description),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().testTag("panel_photo"),
                )
            }
        }
        OutlinedButton(
            onClick = { onEvent(PanelEvent.TakePhoto) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("panel_take_photo"),
        ) {
            Text(stringResource(if (hasPhoto) R.string.panel_replace_photo else R.string.panel_take_photo))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotCell(slot: Int, state: PanelUiState, onEvent: (PanelEvent) -> Unit, modifier: Modifier, highlighted: Boolean = false) {
    val tandem = state.tandemAt(slot)
    val highlightDescription = stringResource(R.string.panel_slot_highlighted)
    OutlinedCard(
        modifier = modifier.testTag("slot_$slot").then(if (highlighted) Modifier.semantics { stateDescription = highlightDescription } else Modifier),
        colors = if (highlighted) CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer) else CardDefaults.outlinedCardColors(),
        border = if (highlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        if (tandem != null) {
            tandem.forEachIndexed { index, half ->
                val suffix = if (index == 0) "a" else "b"
                SlotLine(
                    "$slot $suffix", half,
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("slot_${slot}_$suffix")
                        .clickable { onEvent(PanelEvent.OpenBreaker(slot, half?.id, index)) },
                )
            }
        } else {
            val breaker = state.breakerAt(slot)
            SlotLine(
                slot.toString(), breaker,
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .combinedClickable(
                        onClick = { onEvent(PanelEvent.OpenBreaker(slot, breaker?.id)) },
                        onLongClick = { onEvent(PanelEvent.SlotLongPressed(slot)) },
                    ),
            )
        }
    }
}

/** AC-9: one cell spanning both rows, both slot numbers, a 2-pole marker, one label and amps; tap anywhere opens the breaker. */
@Composable
private fun TwoPoleCell(breaker: Breaker, state: PanelUiState, onEvent: (PanelEvent) -> Unit, modifier: Modifier, highlighted: Boolean) {
    val highlightDescription = stringResource(R.string.panel_slot_highlighted)
    val numbers = breaker.slots.sorted().joinToString(" · ")
    val first = breaker.slots.min()
    val dotLabel = stringResource(R.string.confidence_dot_description, stringResource(confidenceLabel(breaker.confidence)))
    OutlinedCard(
        modifier = modifier.testTag("slot_$first").then(if (highlighted) Modifier.semantics { stateDescription = highlightDescription } else Modifier),
        colors = if (highlighted) CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer) else CardDefaults.outlinedCardColors(),
        border = if (highlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary) else BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            Modifier.fillMaxSize()
                .combinedClickable(onClick = { onEvent(PanelEvent.OpenBreaker(first, breaker.id)) }, onLongClick = { onEvent(PanelEvent.SlotLongPressed(first)) })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(numbers, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp).testTag("two_pole_numbers"))
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(confidenceColor(breaker.confidence))
                        .semantics { contentDescription = dotLabel }
                        .testTag("dot_${breaker.confidence.name}"),
                )
            }
            Text(stringResource(R.string.panel_two_pole_marker), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val amps = breaker.amps?.let { " · ${it}A" }.orEmpty()
            Text(
                if (breaker.label.isBlank()) stringResource(R.string.panel_slot_empty) else breaker.label + amps,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SlotLine(number: String, breaker: Breaker?, modifier: Modifier) {
    Row(modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
        if (breaker != null) {
            val dotLabel = stringResource(R.string.confidence_dot_description, stringResource(confidenceLabel(breaker.confidence)))
            Box(
                Modifier.padding(end = 8.dp).size(12.dp).clip(CircleShape)
                    .background(confidenceColor(breaker.confidence))
                    .semantics { contentDescription = dotLabel }
                    .testTag("dot_${breaker.confidence.name}"),
            )
        }
        if (breaker == null) {
            Text(stringResource(R.string.panel_slot_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (breaker.label.isBlank()) {
            Text(stringResource(R.string.panel_slot_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val amps = breaker.amps?.let { " · ${it}A" }.orEmpty()
            Text(breaker.label + amps, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PanelSetup(state: PanelUiState, onEvent: (PanelEvent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.panel_setup_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.slotCountInput,
            onValueChange = { onEvent(PanelEvent.SlotCountChanged(it)) },
            label = { Text(stringResource(R.string.panel_slot_count)) },
            isError = !state.canSave,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("panel_slot_count"),
        )
        Text(stringResource(R.string.panel_layout), style = MaterialTheme.typography.titleMedium)
        LayoutOption(PanelLayout.NA_NUMBERED, R.string.panel_layout_na, state, onEvent)
        LayoutOption(PanelLayout.DIN, R.string.panel_layout_din, state, onEvent)
        Button(
            onClick = { onEvent(PanelEvent.SavePanel) },
            enabled = state.canSave,
            modifier = Modifier.heightIn(min = 48.dp).testTag("panel_save"),
        ) { Text(stringResource(R.string.save)) }
    }
}

@Composable
private fun LayoutOption(layout: PanelLayout, label: Int, state: PanelUiState, onEvent: (PanelEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = state.layoutInput == layout, role = Role.RadioButton, onClick = { onEvent(PanelEvent.LayoutChanged(layout)) })
            .testTag("layout_${layout.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = state.layoutInput == layout, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
    }
}

/** AC-15: green Confirmed, amber Likely, grey Unknown. */
private fun confidenceColor(c: Confidence): Color = when (c) {
    Confidence.CONFIRMED -> Color(0xFF2E7D32)
    Confidence.LIKELY -> Color(0xFFF9A825)
    Confidence.UNKNOWN -> Color(0xFF9E9E9E)
}

private fun decodeScaled(file: File, maxEdge: Int): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Preview(showBackground = true)
@Composable
private fun PanelSetupPreview() {
    HomeRecordTheme { PanelScreen(PanelUiState(loaded = true), onEvent = {}) }
}
