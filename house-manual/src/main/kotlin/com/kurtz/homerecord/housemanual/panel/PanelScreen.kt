package com.kurtz.homerecord.housemanual.panel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
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
import com.kurtz.homerecord.core.logic.PanelLayoutCalculator
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PanelScreen(
    state: PanelUiState,
    onEvent: (PanelEvent) -> Unit,
    photoFile: File? = null,
) {
    if (!state.loaded) return
    val panel = state.panel
    if (panel == null) {
        PanelSetup(state, onEvent)
        return
    }
    val rows = PanelLayoutCalculator.rows(panel.slotCount, panel.layout)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("panel_grid"),
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
        items(rows, key = { it.first() }) { slots ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEach { slot -> SlotCell(slot, state, onEvent, Modifier.weight(1f)) }
                if (slots.size < PanelLayoutCalculator.columns(panel.layout)) Box(Modifier.weight(1f))
            }
        }
        item { Box(Modifier.height(16.dp)) }
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

@Composable
private fun SlotCell(slot: Int, state: PanelUiState, onEvent: (PanelEvent) -> Unit, modifier: Modifier) {
    val breaker = state.breakerAt(slot)
    OutlinedCard(
        modifier = modifier.heightIn(min = 56.dp).testTag("slot_$slot").clickable { onEvent(PanelEvent.SlotTapped(slot)) },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(slot.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
            if (breaker == null) {
                Text(stringResource(R.string.panel_slot_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val amps = breaker.amps?.let { " · ${it}A" }.orEmpty()
                Text(breaker.label + amps, style = MaterialTheme.typography.bodyMedium)
            }
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
