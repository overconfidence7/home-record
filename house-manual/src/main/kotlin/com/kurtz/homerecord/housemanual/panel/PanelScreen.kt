package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelCell
import com.kurtz.homerecord.core.model.PanelGrid
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import java.io.File

/** HM-Panel (AC-C40): panel facts and photo, then the slots as a grid the way they sit in the panel. */
@Composable
fun PanelScreen(state: PanelUiState, onEvent: (PanelEvent) -> Unit) {
    val panel = state.panel
    val grid = state.grid
    ScreenScaffold(
        title = state.name.ifBlank { stringResource(R.string.panel_title) },
        onBack = { onEvent(PanelEvent.Back) },
    ) { padding ->
        when {
            state.missing -> EmptyState(
                icon = Icons.Rounded.ElectricalServices,
                headline = stringResource(R.string.panel_missing_title),
                body = stringResource(R.string.panel_missing_body),
                actionLabel = null, onAction = {},
                modifier = Modifier.padding(padding).testTag("hm_panel_missing"),
            )
            state.loading || panel == null || grid == null -> LoadingState(Modifier.padding(padding).testTag("hm_panel_loading"))
            else -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).testTag("hm_panel_scroll")) {
                PanelHeader(panel, state.breakers.size)
                SectionHeader(stringResource(R.string.panel_slots))
                if (state.breakers.isEmpty()) {
                    Text(
                        stringResource(R.string.panel_no_breakers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("hm_panel_empty"),
                    )
                }
                SlotGrid(grid, state, onEvent, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                val unplaced = state.unplaced
                if (unplaced.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.panel_unplaced))
                    unplaced.forEach { b ->
                        RecordListItem(
                            headline = b.label.ifBlank { stringResource(R.string.panel_breaker_unlabeled) },
                            supporting = stringResource(R.string.panel_unplaced_row, b.slots.joinToString(", ")),
                            leading = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            trailing = null,
                            modifier = Modifier.testTag("hm_panel_unplaced"),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Photo (or the panel icon) beside the facts: main amps, brand, slot count and layout. */
@Composable
private fun PanelHeader(panel: Panel, breakerCount: Int) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).testTag("hm_panel_header"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier.size(88.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("hm_panel_photo"),
            contentAlignment = Alignment.Center,
        ) {
            val photo = panel.photoPath
            if (photo != null) {
                AsyncImage(File(photo), contentDescription = stringResource(R.string.panel_photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.ElectricalServices, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                panel.mainAmps?.let { stringResource(R.string.panel_main_amps, it) } ?: stringResource(R.string.panel_main_unknown),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("hm_panel_main"),
            )
            panel.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("hm_panel_brand")) }
            Text(
                stringResource(
                    R.string.panel_slots_line,
                    pluralStringResource(R.plurals.panel_slot_count, panel.slotCount, panel.slotCount),
                    stringResource(if (panel.layout == PanelLayout.DIN) R.string.panel_layout_din else R.string.panel_layout_two_column),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("hm_panel_slots_line"),
            )
            Text(
                pluralStringResource(R.plurals.panel_breaker_count, breakerCount, breakerCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The slots column by column, each cell as tall as the rows it spans, so a two-pole breaker draws as one tall cell and the
 * left and right columns stay level. A tandem splits its cell into a top and a bottom half.
 */
@Composable
private fun SlotGrid(grid: PanelGrid, state: PanelUiState, onEvent: (PanelEvent) -> Unit, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    // Tall enough for a two-line label and the amps line at any text size (rule 32: the label wraps, it is not cut).
    val rowHeight = (64f * maxOf(1f, fontScale)).dp
    val gap = 4.dp
    Row(modifier.fillMaxWidth().testTag("hm_panel_grid"), horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (column in 0 until grid.columns) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                grid.cells.filter { it.column == column }.sortedBy { it.row }.forEach { cell ->
                    SlotCell(cell, state, rowHeight * cell.rowSpan + gap * (cell.rowSpan - 1), onEvent)
                }
            }
        }
    }
}

@Composable
private fun SlotCell(cell: PanelCell, state: PanelUiState, height: Dp, onEvent: (PanelEvent) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val breakers = cell.breakerIds.mapNotNull(state::breaker)
    val large = LocalDensity.current.fontScale >= 1.5f
    val description = slotDescription(cell, breakers)
    // Tap a slot to edit its breaker or add one there; a tandem's halves open their own breaker.
    val openLabel = if (breakers.isEmpty()) stringResource(R.string.panel_slot_open_action, cell.slot) else stringResource(R.string.panel_slot_edit_action)
    val tap = if (cell.isTandem) Modifier else Modifier.clickable(onClickLabel = openLabel) { onEvent(PanelEvent.OpenSlot(cell.slot, breakers.firstOrNull()?.id)) }
    val base = Modifier.fillMaxWidth().height(height).clip(shape).then(tap)
        .semantics(mergeDescendants = !cell.isTandem) { contentDescription = description }
        .testTag("hm_panel_slot_${cell.slot}")
    if (breakers.isEmpty()) {
        Row(
            base.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlotNumber(cell.slot.toString())
            Text(stringResource(R.string.panel_slot_open), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(base.background(MaterialTheme.colorScheme.secondaryContainer)) {
        breakers.forEachIndexed { i, b ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
            val number = when {
                cell.isTandem -> stringResource(if (i == 0) R.string.panel_tandem_a else R.string.panel_tandem_b, cell.slot)
                b.poles == 2 -> stringResource(R.string.panel_two_pole_number, b.slots.min(), b.slots.max())
                else -> cell.slot.toString()
            }
            val label = b.label.ifBlank { stringResource(R.string.panel_breaker_unlabeled) }
            val facts = breakerFacts(b)
            val textColor = MaterialTheme.colorScheme.onSecondaryContainer
            if (!cell.isTandem && large) {
                // From 150% text the label takes the whole cell width so words are not split; the number leads the amps line.
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SlotNumber(number)
                        facts?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                return@forEachIndexed
            }
            val half = if (cell.isTandem) {
                Modifier.clickable(onClickLabel = stringResource(R.string.panel_slot_edit_action)) { onEvent(PanelEvent.OpenSlot(cell.slot, b.id)) }
                    .testTag("hm_panel_slot_${cell.slot}${if (i == 0) "a" else "b"}")
            } else Modifier
            Row(Modifier.weight(1f).fillMaxWidth().then(half).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SlotNumber(number)
                if (cell.isTandem) {
                    // Half a cell holds one line: the label, then the amps.
                    Text(
                        listOfNotNull(label, facts).joinToString("  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        facts?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotNumber(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp),
        maxLines = 1,
    )
}

@Composable
private fun breakerFacts(b: Breaker): String? = listOfNotNull(
    b.amps?.let { stringResource(R.string.panel_amps, it) },
    typeLabel(b.type),
).joinToString(" · ").ifEmpty { null }

@Composable
private fun typeLabel(type: BreakerType): String? = when (type) {
    BreakerType.STANDARD -> null
    BreakerType.GFCI -> stringResource(R.string.panel_type_gfci)
    BreakerType.AFCI -> stringResource(R.string.panel_type_afci)
    BreakerType.DUAL_FUNCTION -> stringResource(R.string.panel_type_dual)
}

/** What TalkBack reads for a cell: "Slot 5, open" or "Slot 5, Kitchen counter, 20 A, GFCI". */
@Composable
private fun slotDescription(cell: PanelCell, breakers: List<Breaker>): String {
    if (breakers.isEmpty()) return stringResource(R.string.panel_slot_open_description, cell.slot)
    val parts = breakers.map { b ->
        listOfNotNull(b.label.ifBlank { stringResource(R.string.panel_breaker_unlabeled) }, breakerFacts(b)).joinToString(", ")
    }
    val two = breakers.singleOrNull()?.takeIf { it.poles == 2 }
    return when {
        two != null -> stringResource(R.string.panel_two_pole_description, two.slots.min(), two.slots.max(), parts.single())
        cell.isTandem -> stringResource(R.string.panel_tandem_description, cell.slot, parts[0], parts[1])
        else -> stringResource(R.string.panel_slot_description, cell.slot, parts.single())
    }
}
