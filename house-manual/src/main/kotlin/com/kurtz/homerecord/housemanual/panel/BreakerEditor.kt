package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.coreui.components.EnumPicker
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.housemanual.FormScreenDialog
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.panel.FeedOption

/** HM-Breaker (AC-C40) as a full-screen form over the panel; nothing when no breaker is open. */
@Composable
fun BreakerEditorDialog(state: PanelUiState, onEvent: (PanelEvent) -> Unit) {
    val editor = state.editor ?: return
    FormScreenDialog(
        title = breakerEditorTitle(editor),
        saveTag = "hm_breaker_save",
        dirty = editor.dirty,
        saveEnabled = editor.canSave,
        onClose = { onEvent(PanelEvent.CloseEditor) },
        onSave = { onEvent(PanelEvent.SaveBreaker) },
    ) { BreakerEditorContent(editor, onEvent) }
    if (editor.confirmDelete) BreakerDeleteDialog(editor, onEvent)
}

@Composable
fun breakerEditorTitle(editor: BreakerEditorState): String =
    stringResource(if (editor.isNew) R.string.breaker_add_title else R.string.breaker_edit_title)

/** Where the breaker sits, its label, amps, poles and type, then the rooms and items it feeds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakerEditorContent(editor: BreakerEditorState, onEvent: (PanelEvent) -> Unit, modifier: Modifier = Modifier) {
    val d = editor.draft
    val change = { next: com.kurtz.homerecord.housemodel.panel.BreakerDraft -> onEvent(PanelEvent.ChangeBreaker(next)) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("hm_breaker_form"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(whereLine(editor), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).testTag("hm_breaker_where"))
        editor.slotProblem?.let {
            Text(slotProblemText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hm_breaker_slot_error"))
        }
        // AC-C41: the breaker says what it feeds up top, so a long list of chips below is not the only place to read it.
        Text(
            editor.feeds.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.breaker_feeds, it.joinToString(", ")) }
                ?: stringResource(R.string.breaker_feeds_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("hm_breaker_feeds"),
        )
        LabeledField(
            label = stringResource(R.string.breaker_label), value = d.label,
            onValueChange = { change(d.copy(label = it.take(60))) },
            helper = stringResource(R.string.breaker_label_helper),
            modifier = Modifier.testTag("hm_breaker_label"),
        )
        LabeledField(
            label = stringResource(R.string.breaker_amps), value = d.amps,
            onValueChange = { change(d.copy(amps = it.filter(Char::isDigit).take(3))) },
            keyboardType = KeyboardType.Number,
            error = if (editor.ampsError) stringResource(R.string.breaker_amps_error) else null,
            modifier = Modifier.testTag("hm_breaker_amps"),
        )
        if (d.tandem) {
            Text(stringResource(R.string.breaker_tandem_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.breaker_poles), style = MaterialTheme.typography.labelLarge)
            EnumPicker(
                options = listOf(1, 2), selected = d.poles, onSelect = { change(d.copy(poles = it)) },
                labelOf = { stringResource(if (it == 1) R.string.breaker_poles_one else R.string.breaker_poles_two) },
                tagOf = { "hm_breaker_poles_$it" },
            )
        }
        Text(stringResource(R.string.breaker_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BreakerType.entries.forEach { type ->
                FilterChip(
                    selected = d.type == type, onClick = { change(d.copy(type = type)) },
                    label = { Text(breakerTypeName(type)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_type_${type.name.lowercase()}"),
                )
            }
        }
        FeedPicker(
            title = stringResource(R.string.breaker_rooms), empty = stringResource(R.string.breaker_rooms_none),
            options = editor.rooms, selected = d.roomIds, tag = "room",
            onToggle = { id -> change(d.copy(roomIds = d.roomIds.toggle(id))) },
        )
        FeedPicker(
            title = stringResource(R.string.breaker_items), empty = stringResource(R.string.breaker_items_none),
            options = editor.items, selected = d.itemIds, tag = "item",
            onToggle = { id -> change(d.copy(itemIds = d.itemIds.toggle(id))) },
        )
        if (!editor.isNew) {
            TextButton(onClick = { onEvent(PanelEvent.AskDeleteBreaker) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_delete")) {
                Text(stringResource(R.string.breaker_delete))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedPicker(title: String, empty: String, options: List<FeedOption>, selected: Set<String>, tag: String, onToggle: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (options.isEmpty()) {
        Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o.id in selected, onClick = { onToggle(o.id) },
                label = { Text(o.name) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_${tag}_chip"),
            )
        }
    }
}

/** "Delete Range?" over the form (rule 16): Delete frees the slots, Cancel keeps the form. */
@Composable
fun BreakerDeleteDialog(editor: BreakerEditorState, onEvent: (PanelEvent) -> Unit) {
    AlertDialog(
        modifier = Modifier.windowTestTags(),
        onDismissRequest = { onEvent(PanelEvent.CancelDeleteBreaker) },
        title = { Text(stringResource(R.string.breaker_delete_title, editor.draft.label.trim().ifEmpty { whereLine(editor) })) },
        text = { Text(stringResource(R.string.breaker_delete_body)) },
        confirmButton = {
            TextButton(onClick = { onEvent(PanelEvent.ConfirmDeleteBreaker) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_delete_confirm")) {
                Text(stringResource(R.string.breaker_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PanelEvent.CancelDeleteBreaker) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_breaker_delete_cancel")) {
                Text(stringResource(R.string.breaker_cancel))
            }
        },
    )
}

/** "Slot 5", "Slots 1 and 3" or "Slot 7, tandem". */
@Composable
private fun whereLine(editor: BreakerEditorState): String {
    val d = editor.draft
    val slots = (editor.placement as? SlotPlacement.Ok)?.slots ?: listOf(d.startSlot)
    return when {
        d.tandem -> stringResource(R.string.breaker_where_tandem, d.startSlot)
        slots.size == 2 -> stringResource(R.string.breaker_where_two, slots.min(), slots.max())
        else -> stringResource(R.string.breaker_where_slot, d.startSlot)
    }
}

@Composable
private fun slotProblemText(p: SlotPlacement): String = when (p) {
    is SlotPlacement.Taken -> pluralStringResource(R.plurals.breaker_slot_taken, p.slots.size, p.slots.joinToString(", "))
    SlotPlacement.OffPanel -> stringResource(R.string.breaker_slot_off_panel)
    SlotPlacement.TandemNeedsOnePole -> stringResource(R.string.breaker_slot_tandem)
    is SlotPlacement.Ok -> ""
}

@Composable
private fun breakerTypeName(type: BreakerType): String = stringResource(
    when (type) {
        BreakerType.STANDARD -> R.string.breaker_type_standard
        BreakerType.GFCI -> R.string.panel_type_gfci
        BreakerType.AFCI -> R.string.panel_type_afci
        BreakerType.DUAL_FUNCTION -> R.string.panel_type_dual
    },
)

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id
