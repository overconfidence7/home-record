package com.kurtz.homerecord.housemanual.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.LinkChip
import com.kurtz.homerecord.coreui.components.LinkChipRow
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.housemanual.R

/**
 * Shutoff (AC-C45, AC-H7): a "Shutoff: Kitchen sink cold valve" chip that opens the shutoff's page, with Change beside it;
 * with none linked, a row that opens the picker. Hidden while the home has no other shutoff to pick.
 */
@Composable
internal fun ShutoffSection(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    val linked = state.shutoff
    if (linked == null && state.shutoffChoices.isEmpty() && !state.shutoffRefused) return
    // A viewer sees a linked shutoff but cannot pick or change it.
    if (linked == null && state.viewOnly) return
    Section(stringResource(R.string.item_detail_shutoff), Modifier.testTag("hm_item_detail_shutoff")) {
        if (state.shutoffRefused) {
            Row(Modifier.padding(horizontal = 16.dp).testTag("hm_item_detail_shutoff_refused"), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.item_detail_shutoff_refused), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onEvent(ItemDetailEvent.DismissShutoffNotice) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.documents_dismiss))
                }
            }
        }
        if (linked != null) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkChipRow(
                    listOf(LinkChip(linked.id, stringResource(R.string.item_detail_shutoff_chip, linked.name), Icons.Rounded.PowerOff)),
                    onOpen = { onEvent(ItemDetailEvent.OpenShutoff(linked.id)) },
                    modifier = Modifier.weight(1f).testTag("hm_item_detail_shutoff_chip"),
                )
                if (!state.viewOnly) TextButton(
                    onClick = { onEvent(ItemDetailEvent.ShowShutoffPicker) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_shutoff_change"),
                ) { Text(stringResource(R.string.item_detail_shutoff_change)) }
            }
        } else {
            RecordListItem(
                headline = stringResource(R.string.item_detail_shutoff_pick),
                supporting = pluralStringResource(R.plurals.item_detail_shutoff_pick_supporting, state.shutoffChoices.size, state.shutoffChoices.size),
                leading = { Icon(Icons.Rounded.PowerOff, contentDescription = null) },
                onClick = { onEvent(ItemDetailEvent.ShowShutoffPicker) },
                modifier = Modifier.testTag("hm_item_detail_shutoff_pick"),
            )
        }
    }
}

/** "Which shutoff turns this off?": the home's shutoffs, mains first; the linked one is checked, "No shutoff" clears it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShutoffPickerSheet(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    if (!state.showShutoffPicker) return
    ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(ItemDetailEvent.HideShutoffPicker) }) {
        ShutoffPickerContent(state, onEvent)
    }
}

/** The picker's scrolling list, so every shutoff stays reachable at 200% text; tests and previews draw it. */
@Composable
fun ShutoffPickerContent(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    LazyColumn(Modifier.testTag("hm_item_detail_shutoff_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "title") {
            Text(
                stringResource(R.string.item_detail_shutoff_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
        }
        itemsIndexed(state.shutoffChoices, key = { _, c -> c.id }) { i, c ->
            val picked = c.id == state.shutoff?.id
            RecordListItem(
                headline = c.name,
                supporting = c.roomName.ifBlank { null },
                leading = { Icon(Icons.Rounded.PowerOff, contentDescription = null) },
                trailing = if (picked) ({ Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.item_detail_shutoff_picked)) }) else null,
                onClick = { onEvent(ItemDetailEvent.PickShutoff(c.id)) },
                modifier = Modifier.testTag("hm_item_detail_shutoff_option_$i"),
            )
        }
        if (state.shutoff != null) {
            item(key = "none") {
                RecordListItem(
                    headline = stringResource(R.string.item_detail_shutoff_none),
                    trailing = null,
                    onClick = { onEvent(ItemDetailEvent.PickShutoff(null)) },
                    modifier = Modifier.testTag("hm_item_detail_shutoff_none"),
                )
            }
        }
    }
}
