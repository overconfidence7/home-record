package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.coreui.components.EnumPicker
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.housemanual.FormScreenDialog
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.panel.PanelField

/** Panel details (AC-C40): slot count, layout, main breaker amps and brand, as a full-screen form over the panel. */
@Composable
fun PanelFactsDialog(state: PanelUiState, onEvent: (PanelEvent) -> Unit) {
    val facts = state.facts ?: return
    FormScreenDialog(
        title = stringResource(R.string.panel_facts_title),
        saveTag = "hm_panel_facts_save",
        dirty = facts.dirty,
        saveEnabled = facts.dirty,
        onClose = { onEvent(PanelEvent.CloseFacts) },
        onSave = { onEvent(PanelEvent.SaveFacts) },
    ) { PanelFactsContent(facts, onEvent) }
}

@Composable
fun PanelFactsContent(facts: PanelFactsState, onEvent: (PanelEvent) -> Unit, modifier: Modifier = Modifier) {
    val d = facts.draft
    val errors = facts.errors
    val change = { next: com.kurtz.homerecord.housemodel.panel.PanelDraft -> onEvent(PanelEvent.ChangeFacts(next)) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).testTag("hm_panel_facts_form"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val slotsError = when {
            PanelField.SLOT_COUNT !in errors -> null
            facts.highestUsed > 0 && (d.slotCount.trim().toIntOrNull() ?: 0) in 1 until facts.highestUsed ->
                stringResource(R.string.panel_facts_slots_error_used, facts.highestUsed)
            else -> stringResource(R.string.panel_facts_slots_error)
        }
        LabeledField(
            label = stringResource(R.string.panel_facts_slots), value = d.slotCount,
            onValueChange = { change(d.copy(slotCount = it.filter(Char::isDigit).take(2))) },
            keyboardType = KeyboardType.Number, required = true,
            helper = stringResource(R.string.panel_facts_slots_helper), error = slotsError,
            modifier = Modifier.testTag("hm_panel_facts_slots"),
        )
        Text(stringResource(R.string.panel_facts_layout), style = MaterialTheme.typography.labelLarge)
        if (facts.layoutLocked) {
            Text(
                stringResource(if (d.layout == PanelLayout.DIN) R.string.panel_facts_layout_din else R.string.panel_facts_layout_two_column),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.panel_facts_layout_locked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            EnumPicker(
                options = PanelLayout.entries, selected = d.layout, onSelect = { change(d.copy(layout = it)) },
                labelOf = { stringResource(if (it == PanelLayout.DIN) R.string.panel_facts_layout_din else R.string.panel_facts_layout_two_column) },
                tagOf = { "hm_panel_facts_layout_${it.name.lowercase()}" },
            )
        }
        LabeledField(
            label = stringResource(R.string.panel_facts_main), value = d.mainAmps,
            onValueChange = { change(d.copy(mainAmps = it.filter(Char::isDigit).take(4))) },
            keyboardType = KeyboardType.Number,
            error = if (PanelField.MAIN_AMPS in errors) stringResource(R.string.panel_facts_main_error) else null,
            modifier = Modifier.testTag("hm_panel_facts_main"),
        )
        LabeledField(
            label = stringResource(R.string.panel_facts_brand), value = d.brand,
            onValueChange = { change(d.copy(brand = it.take(60))) },
            modifier = Modifier.testTag("hm_panel_facts_brand"),
        )
    }
}
